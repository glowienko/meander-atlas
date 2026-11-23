#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

if [[ -n "${DOCKER_HOST:-}" && "${DOCKER_HOST}" == unix://* ]]; then
  sock_path="${DOCKER_HOST#unix://}"
  if [[ ! -S "$sock_path" && -S "$HOME/.docker/run/docker.sock" ]]; then
    export DOCKER_HOST="unix://$HOME/.docker/run/docker.sock"
  fi
elif [[ -z "${DOCKER_HOST:-}" && ! -S "/var/run/docker.sock" && -S "$HOME/.docker/run/docker.sock" ]]; then
  export DOCKER_HOST="unix://$HOME/.docker/run/docker.sock"
fi

if [[ -x "/opt/homebrew/bin/node" ]]; then
  current_node_major="$(node -p 'process.versions.node.split(".")[0]' 2>/dev/null || echo 0)"
  if [[ "$current_node_major" -lt 26 ]]; then
    export PATH="/opt/homebrew/bin:$PATH"
  fi
fi

free_port() {
  node -e '
    const net = require("node:net");
    const s = net.createServer();
    s.listen(0, "127.0.0.1", () => {
      const { port } = s.address();
      s.close(() => console.log(port));
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
trap cleanup EXIT

PG_CONTAINER="$(docker run --rm -d \
  -e POSTGRES_DB=meander_atlas \
  -e POSTGRES_USER=meander \
  -e POSTGRES_PASSWORD=meander_e2e \
  -p 127.0.0.1::5432 \
  postgis/postgis:17-3.5)"

PG_PORT="$(docker port "$PG_CONTAINER" 5432/tcp | head -n 1 | awk -F: '{print $NF}')"

for _ in $(seq 1 30); do
  if docker exec "$PG_CONTAINER" pg_isready -U meander -d meander_atlas >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

./backend/gradlew -p backend --console=plain bootJar >/dev/null
JAR_PATH="$(find backend/build/libs -maxdepth 1 -name 'meander-atlas-backend-*.jar' ! -name '*-plain.jar' | head -n 1)"
if [[ -z "$JAR_PATH" ]]; then
  printf 'FAIL: bootJar artifact not found under backend/build/libs\n' >&2
  exit 1
fi

BACKEND_PORT="$(free_port)"
PREVIEW_PORT="$(free_port)"

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
