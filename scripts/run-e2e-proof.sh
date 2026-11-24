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

cleanup() {
  if [[ -n "$BACKEND_PID" ]] && kill -0 "$BACKEND_PID" 2>/dev/null; then
    kill "$BACKEND_PID" 2>/dev/null || true
    wait "$BACKEND_PID" 2>/dev/null || true
  fi
  if [[ -n "$PG_CONTAINER" ]]; then
    docker rm -f "$PG_CONTAINER" >/dev/null 2>&1 || true
  fi
  rm -f "$BACKEND_LOG"
}
trap cleanup EXIT INT TERM

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

if [[ ! -f frontend/dist/index.html ]]; then
  pnpm --dir frontend run build >/dev/null
fi

read -r BACKEND_PORT PREVIEW_PORT <<<"$(allocate_two_ports)"

java -jar "$JAR_PATH" \
  --server.port="$BACKEND_PORT" \
  --spring.datasource.url="jdbc:postgresql://127.0.0.1:${PG_PORT}/meander_atlas" \
  --spring.datasource.username=meander \
  --spring.datasource.password=meander_e2e \
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
