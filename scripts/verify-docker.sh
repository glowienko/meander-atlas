#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

source "$REPO_ROOT/scripts/lib/env.sh"
resolve_docker_host
resolve_node26

allocate_three_ports() {
  node -e '
    const net = require("node:net");
    const servers = [net.createServer(), net.createServer(), net.createServer()];
    const ports = [];
    const bindNext = (index) => {
      if (index === servers.length) {
        let remaining = servers.length;
        for (const s of servers) {
          s.close(() => {
            remaining -= 1;
            if (remaining === 0) {
              console.log(ports.join(" "));
            }
          });
        }
        return;
      }
      servers[index].listen(0, "127.0.0.1", () => {
        ports.push(servers[index].address().port);
        bindNext(index + 1);
      });
    };
    bindNext(0);
  '
}

PROJECT="meander-atlas-smoke-$$"
TMP_IMPORTS="$(mktemp -d)"
TMP_BOUNDARIES="$(mktemp -d)"

cleanup() {
  docker compose -p "$PROJECT" --profile app down -v --remove-orphans >/dev/null 2>&1 || true
  rm -rf "$TMP_IMPORTS" "$TMP_BOUNDARIES"
}
trap cleanup EXIT INT TERM

read -r PG_PORT BE_PORT FE_PORT <<<"$(allocate_three_ports)"

export POSTGRES_PORT="$PG_PORT"
export APP_PORT="$BE_PORT"
export FRONTEND_PORT="$FE_PORT"
export APP_IMPORTS_DIR="$TMP_IMPORTS"
export APP_BOUNDARIES_DIR="$TMP_BOUNDARIES"

docker compose -p "$PROJECT" --profile app config -q

if ! docker compose -p "$PROJECT" --profile app up --build -d --wait --wait-timeout 180; then
  printf 'FAIL: docker compose --profile app did not become healthy\n' >&2
  docker compose -p "$PROJECT" --profile app ps >&2 || true
  docker compose -p "$PROJECT" --profile app logs --tail=80 >&2 || true
  exit 1
fi

HTML="$(curl -fsS "http://127.0.0.1:${FE_PORT}/")"
if ! printf '%s' "$HTML" | grep -q '<title>MeanderAtlas</title>'; then
  printf 'FAIL: frontend root did not serve MeanderAtlas HTML\n' >&2
  exit 1
fi

HEALTH="$(curl -fsS "http://127.0.0.1:${FE_PORT}/api/v1/atlas/health")"
if ! printf '%s' "$HEALTH" | grep -q '"status":"UP"' || ! printf '%s' "$HEALTH" | grep -q '"postgisVersion":"3\.'; then
  printf 'FAIL: proxied /api/v1/atlas/health returned unexpected payload: %s\n' "$HEALTH" >&2
  exit 1
fi

LIVENESS="$(curl -fsS "http://127.0.0.1:${BE_PORT}/actuator/health/liveness")"
READINESS="$(curl -fsS "http://127.0.0.1:${BE_PORT}/actuator/health/readiness")"
if ! printf '%s' "$LIVENESS" | grep -q '"status":"UP"' || ! printf '%s' "$READINESS" | grep -q '"status":"UP"'; then
  printf 'FAIL: backend actuator probes failed (liveness=%s, readiness=%s)\n' "$LIVENESS" "$READINESS" >&2
  exit 1
fi

PUBLIC_ACTUATOR="$(curl -fsS "http://127.0.0.1:${FE_PORT}/actuator/health")"
if printf '%s' "$PUBLIC_ACTUATOR" | grep -q '"status":"UP"'; then
  printf 'FAIL: /actuator/health leaked through Caddy proxy\n' >&2
  exit 1
fi

printf 'PASS: verify-docker (postgres + backend actuator probes + frontend Caddy proxy healthy)\n'
