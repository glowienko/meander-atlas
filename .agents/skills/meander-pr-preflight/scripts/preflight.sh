#!/usr/bin/env bash
set -euo pipefail

BASE_REF="${1:-origin/master}"
REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

BRANCH="$(git rev-parse --abbrev-ref HEAD)"
if [[ "$BRANCH" == "master" || "$BRANCH" == "main" ]]; then
  printf 'FAIL: refusing to run PR preflight on protected branch %s\n' "$BRANCH" >&2
  exit 1
fi

BASH_BIN="/bin/bash"
if [[ ! -x "$BASH_BIN" ]]; then
  BASH_BIN="$(command -v bash)"
fi

while IFS= read -r script_file; do
  [[ -z "$script_file" ]] && continue
  "$BASH_BIN" -n "$script_file"
done < <(find scripts .githooks .agents/skills -type f \( -name '*.sh' -o -name 'pre-commit' \) 2>/dev/null | LC_ALL=C sort)

CHANGED_FILES=""
if git rev-parse --verify "$BASE_REF" >/dev/null 2>&1; then
  CHANGED_FILES="$(git diff --name-only "$BASE_REF"...HEAD; git diff --name-only; git diff --cached --name-only)"
fi

NEEDS_DOCKER=false
if printf '%s\n' "$CHANGED_FILES" | grep -Eq '(Dockerfile|\.dockerignore|Caddyfile|compose\.yml|backend/build\.gradle\.kts|backend/gradle/libs\.versions\.toml|frontend/package\.json|frontend/pnpm-lock\.yaml|scripts/verify-docker\.sh)'; then
  NEEDS_DOCKER=true
fi

if [[ "$NEEDS_DOCKER" == true ]]; then
  ./scripts/verify.sh --docker
else
  ./scripts/verify.sh
fi

if printf '%s\n' "$CHANGED_FILES" | grep -q '^frontend/'; then
  if [[ ! -s artifacts/proof/phase0-globe.png ]]; then
    printf 'FAIL: frontend changed but artifacts/proof/phase0-globe.png is missing or empty\n' >&2
    exit 1
  fi
fi

printf 'PASS: meander-pr-preflight (branch=%s, docker=%s)\n' "$BRANCH" "$NEEDS_DOCKER"
