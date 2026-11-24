#!/usr/bin/env bash

resolve_docker_host() {
  if [[ -n "${DOCKER_HOST:-}" && "${DOCKER_HOST}" == unix://* ]]; then
    local sock_path="${DOCKER_HOST#unix://}"
    if [[ ! -S "$sock_path" && -S "$HOME/.docker/run/docker.sock" ]]; then
      export DOCKER_HOST="unix://$HOME/.docker/run/docker.sock"
    fi
  elif [[ -z "${DOCKER_HOST:-}" && ! -S "/var/run/docker.sock" && -S "$HOME/.docker/run/docker.sock" ]]; then
    export DOCKER_HOST="unix://$HOME/.docker/run/docker.sock"
  fi
}

resolve_node26() {
  local current_node_major
  current_node_major="$(node -p 'process.versions.node.split(".")[0]' 2>/dev/null || echo 0)"
  if [[ "$current_node_major" -lt 26 && -x "/opt/homebrew/bin/node" ]]; then
    export PATH="/opt/homebrew/bin:$PATH"
    current_node_major="$(node -p 'process.versions.node.split(".")[0]' 2>/dev/null || echo 0)"
  fi
  if [[ "$current_node_major" -lt 26 ]]; then
    printf 'FAIL: Node 26+ is required (found major version %s)\n' "$current_node_major" >&2
    exit 1
  fi
}
