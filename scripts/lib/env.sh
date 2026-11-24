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
