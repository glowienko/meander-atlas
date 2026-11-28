#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

source "$REPO_ROOT/scripts/lib/env.sh"
resolve_docker_host
resolve_node26

allocate_two_ports() {
  node -e '
    const net = require("node:net");
    const s1 = net.createServer();
    const s2 = net.createServer();
    s1.listen(0, "127.0.0.1", () => {
      s2.listen(0, "127.0.0.1", () => {
        const p1 = s1.address().port;
        const p2 = s2.address().port;
        s1.close(() => s2.close(() => console.log(`${p1} ${p2}`)));
      });
    });
  '
}

PG_CONTAINER=""
BACKEND_PID=""
BACKEND_LOG="$(mktemp)"
E2E_BOUNDARIES_DIR="$(mktemp -d)"
E2E_IMPORTS_DIR="$(mktemp -d)"

cleanup() {
  if [[ -n "$BACKEND_PID" ]] && kill -0 "$BACKEND_PID" 2>/dev/null; then
    kill "$BACKEND_PID" 2>/dev/null || true
    wait "$BACKEND_PID" 2>/dev/null || true
  fi
  if [[ -n "$PG_CONTAINER" ]]; then
    docker rm -f "$PG_CONTAINER" >/dev/null 2>&1 || true
  fi
  rm -f "$BACKEND_LOG"
  rm -rf "$E2E_BOUNDARIES_DIR" "$E2E_IMPORTS_DIR"
}
trap cleanup EXIT INT TERM

cat > "$E2E_BOUNDARIES_DIR/ne_10m_admin_0_countries.geojson" <<'EOF'
{
  "type": "FeatureCollection",
  "features": [
    {
      "type": "Feature",
      "properties": {"ISO_A2_EH": "PL", "ADMIN": "Poland"},
      "geometry": {
        "type": "Polygon",
        "coordinates": [[[14.0, 49.0], [24.2, 49.0], [24.2, 55.0], [14.0, 55.0], [14.0, 49.0]]]
      }
    },
    {
      "type": "Feature",
      "properties": {"ISO_A2_EH": "FR", "ADMIN": "France"},
      "geometry": {
        "type": "MultiPolygon",
        "coordinates": [
          [[[-5.0, 42.0], [8.5, 42.0], [8.5, 51.2], [-5.0, 51.2], [-5.0, 42.0]]],
          [[[-54.6, 2.1], [-51.6, 2.1], [-51.6, 5.7], [-54.6, 5.7], [-54.6, 2.1]]]
        ]
      }
    },
    {
      "type": "Feature",
      "properties": {"ISO_A2_EH": "IT", "ADMIN": "Italy"},
      "geometry": {
        "type": "Polygon",
        "coordinates": [[[6.6, 36.6], [18.5, 36.6], [18.5, 47.1], [6.6, 47.1], [6.6, 36.6]]]
      }
    },
    {
      "type": "Feature",
      "properties": {"ISO_A2_EH": "ES", "ADMIN": "Spain"},
      "geometry": {
        "type": "Polygon",
        "coordinates": [[[-9.5, 36.0], [3.5, 36.0], [3.5, 41.9], [-9.5, 41.9], [-9.5, 36.0]]]
      }
    },
    {
      "type": "Feature",
      "properties": {"ISO_A2_EH": "AU", "ADMIN": "Australia"},
      "geometry": {
        "type": "Polygon",
        "coordinates": [[[114.0, -39.0], [154.0, -39.0], [154.0, -11.0], [114.0, -11.0], [114.0, -39.0]]]
      }
    },
    {
      "type": "Feature",
      "properties": {"ISO_A2_EH": "AQ", "ADMIN": "Antarctica"},
      "geometry": {
        "type": "Polygon",
        "coordinates": [[[-180.0, -90.0], [180.0, -90.0], [180.0, -68.0], [-180.0, -68.0], [-180.0, -90.0]]]
      }
    }
  ]
}
EOF

cat > "$E2E_BOUNDARIES_DIR/ne_10m_admin_1_states_provinces.geojson" <<'EOF'
{
  "type": "FeatureCollection",
  "features": [
    {
      "type": "Feature",
      "properties": {"iso_3166_2": "PL-12", "iso_a2": "PL", "name": "Małopolskie"},
      "geometry": {
        "type": "Polygon",
        "coordinates": [[[19.0, 49.4], [21.2, 49.4], [21.2, 50.6], [19.0, 50.6], [19.0, 49.4]]]
      }
    }
  ]
}
EOF

mkdir -p "$E2E_IMPORTS_DIR/personal"
unzip -q "$REPO_ROOT/fixtures/synthetic-takeout-pl-en.zip" -d "$E2E_IMPORTS_DIR/personal"
cp "$REPO_ROOT/fixtures/synthetic-timeline.json" "$E2E_IMPORTS_DIR/personal/Timeline.json"

PG_CONTAINER="$(docker run --rm -d \
  -e POSTGRES_DB=meander_atlas \
  -e POSTGRES_USER=meander \
  -e POSTGRES_PASSWORD=meander_e2e \
  -p 127.0.0.1::5432 \
  postgis/postgis:17-3.5)"

PG_PORT="$(docker port "$PG_CONTAINER" 5432/tcp | head -n 1 | awk -F: '{print $NF}')"

PG_READY=false
for _ in $(seq 1 30); do
  if docker exec "$PG_CONTAINER" pg_isready -h 127.0.0.1 -U meander -d meander_atlas >/dev/null 2>&1; then
    PG_READY=true
    break
  fi
  sleep 1
done

if [[ "$PG_READY" != true ]]; then
  printf 'FAIL: ephemeral PostGIS container did not become ready in time:\n' >&2
  docker logs "$PG_CONTAINER" 2>&1 | tail -n 60 >&2 || true
  exit 1
fi

./backend/gradlew -p backend --console=plain bootJar >/dev/null
JAR_PATH="backend/build/libs/meander-atlas-backend.jar"
if [[ ! -f "$JAR_PATH" ]]; then
  printf 'FAIL: bootJar artifact not found at %s\n' "$JAR_PATH" >&2
  exit 1
fi

pnpm --dir frontend run build >/dev/null

read -r BACKEND_PORT PREVIEW_PORT <<<"$(allocate_two_ports)"

java -jar "$JAR_PATH" \
  --server.port="$BACKEND_PORT" \
  --spring.datasource.url="jdbc:postgresql://127.0.0.1:${PG_PORT}/meander_atlas" \
  --spring.datasource.username=meander \
  --spring.datasource.password=meander_e2e \
  --app.boundaries-dir="$E2E_BOUNDARIES_DIR" \
  --app.boundaries-auto-seed=true \
  --app.imports-dir="$E2E_IMPORTS_DIR" \
  >"$BACKEND_LOG" 2>&1 &
BACKEND_PID=$!

READY=false
for _ in $(seq 1 40); do
  if curl -fsS "http://127.0.0.1:${BACKEND_PORT}/api/v1/atlas/health" 2>/dev/null | grep -q '"status":"UP"'; then
    READY=true
    break
  fi
  if ! kill -0 "$BACKEND_PID" 2>/dev/null; then
    printf 'FAIL: backend exited early during E2E startup:\n' >&2
    tail -n 80 "$BACKEND_LOG" >&2
    exit 1
  fi
  sleep 0.5
done

if [[ "$READY" != true ]]; then
  printf 'FAIL: backend did not become healthy in time:\n' >&2
  tail -n 80 "$BACKEND_LOG" >&2
  exit 1
fi

rm -f artifacts/proof/phase0-globe.png
VITE_API_PROXY_TARGET="http://127.0.0.1:${BACKEND_PORT}" \
E2E_PREVIEW_PORT="$PREVIEW_PORT" \
  pnpm --dir frontend run test:e2e

if [[ ! -s artifacts/proof/phase0-globe.png ]]; then
  printf 'FAIL: visual proof screenshot artifacts/proof/phase0-globe.png missing\n' >&2
  exit 1
fi
