#!/usr/bin/env bash
set -euo pipefail

FAST=false
for arg in "$@"; do
  case "$arg" in
    --fast) FAST=true ;;
    *)
      printf 'Usage: %s [--fast]\n' "$0" >&2
      exit 2
      ;;
  esac
done

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

source "$REPO_ROOT/scripts/lib/env.sh"
resolve_docker_host
if [[ -f frontend/package.json ]]; then
  resolve_node26
fi

START_TS="$(date +%s)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

CHECKS=()

run_step() {
  local name="$1"
  shift
  local log_file="$TMP_DIR/${name}.log"
  if "$@" >"$log_file" 2>&1; then
    CHECKS+=("$name")
  else
    local code=$?
    printf 'FAIL: %s (exit %d)\n' "$name" "$code" >&2
    tail -n 120 "$log_file" >&2
    exit "$code"
  fi
}

sha256_cmd() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$@"
  else
    shasum -a 256 "$@"
  fi
}

hash_paths() {
  local out=""
  for target in "$@"; do
    if [[ -d "$target" ]]; then
      while IFS= read -r file; do
        out+="$(sha256_cmd "$file")"$'\n'
      done < <(find "$target" -type f | LC_ALL=C sort)
    elif [[ -f "$target" ]]; then
      out+="$(sha256_cmd "$target")"$'\n'
    fi
  done
  printf '%s' "$out" | sha256_cmd | awk '{print $1}'
}

format_checks() {
  if [[ "${#CHECKS[@]}" -eq 0 ]]; then
    printf 'none'
    return
  fi
  local IFS=', '
  printf '%s' "${CHECKS[*]}"
}

run_step "privacy" ./scripts/check-privacy.sh
run_step "guardrail-tests" ./scripts/test-guardrails.sh

if [[ "$FAST" == true ]]; then
  run_step "backend-fast" ./backend/gradlew -p backend --console=plain checkstyleMain checkstyleTest unitTest
  if [[ -f frontend/package.json ]]; then
    run_step "frontend-fast" pnpm --dir frontend run check:fast
  fi
  ELAPSED="$(( $(date +%s) - START_TS ))"
  printf 'PASS: verify --fast (%s) [%ss]\n' "$(format_checks)" "$ELAPSED"
  exit 0
fi

if ! command -v gitleaks >/dev/null 2>&1; then
  printf 'FAIL: gitleaks is required in PATH\n' >&2
  exit 1
fi

scan_working_tree_secrets() {
  local stage_dir="$TMP_DIR/gitleaks-tree"
  mkdir -p "$stage_dir"
  while IFS= read -r -d '' f; do
    if [[ -f "$f" && ! -L "$f" ]]; then
      mkdir -p "$stage_dir/$(dirname "$f")"
      cp "$f" "$stage_dir/$f"
    fi
  done < <(git ls-files -z --cached --others --exclude-standard)
  (
    cd "$stage_dir"
    gitleaks detect --source . --no-git --config "$REPO_ROOT/.gitleaks.toml" --redact --no-banner
  )
}

run_step "gitleaks-git" gitleaks detect --source . --config .gitleaks.toml --redact --no-banner
run_step "gitleaks-tree" scan_working_tree_secrets

BEFORE_HASH="$(hash_paths contracts/openapi.json docs/modules)"
if [[ "${CI:-}" == "true" ]]; then
  run_step "backend-full" ./backend/gradlew -p backend --console=plain spotlessCheck checkstyleMain checkstyleTest test --rerun
else
  run_step "backend-full" ./backend/gradlew -p backend --console=plain spotlessCheck checkstyleMain checkstyleTest test
fi
AFTER_HASH="$(hash_paths contracts/openapi.json docs/modules)"

if [[ ! -f contracts/openapi.json ]]; then
  printf 'FAIL: contracts/openapi.json was not generated\n' >&2
  exit 1
fi

if [[ "$BEFORE_HASH" != "$AFTER_HASH" ]]; then
  printf 'FAIL: contracts/openapi.json or docs/modules/ drifted during build; review and re-run ./scripts/verify.sh\n' >&2
  git status --short -- contracts/openapi.json docs/modules >&2
  exit 1
fi

if [[ "${CI:-}" == "true" ]]; then
  DRIFT="$(git status --porcelain -- contracts/openapi.json docs/modules)"
  if [[ -n "$DRIFT" ]]; then
    printf 'FAIL: generated contracts/openapi.json or docs/modules/ have uncommitted changes:\n%s\n' "$DRIFT" >&2
    exit 1
  fi
fi
CHECKS+=("contracts-and-docs-sync")

if [[ -f frontend/package.json ]]; then
  FE_BEFORE_HASH="$(hash_paths frontend/src/api/schema.d.ts)"
  run_step "frontend-check" pnpm --dir frontend run check:all
  FE_AFTER_HASH="$(hash_paths frontend/src/api/schema.d.ts)"
  if [[ "$FE_BEFORE_HASH" != "$FE_AFTER_HASH" ]]; then
    printf 'FAIL: frontend/src/api/schema.d.ts drifted after gen:api; review and re-run ./scripts/verify.sh\n' >&2
    exit 1
  fi
  if [[ "${CI:-}" == "true" ]]; then
    FE_DRIFT="$(git status --porcelain -- frontend/src/api/schema.d.ts)"
    if [[ -n "$FE_DRIFT" ]]; then
      printf 'FAIL: frontend/src/api/schema.d.ts has uncommitted changes:\n%s\n' "$FE_DRIFT" >&2
      exit 1
    fi
  fi
  CHECKS+=("frontend-api-sync")
  if [[ -f scripts/run-e2e-proof.sh ]]; then
    run_step "playwright-proof" ./scripts/run-e2e-proof.sh
  fi
fi

ELAPSED="$(( $(date +%s) - START_TS ))"
printf 'PASS: verify (%s) [%ss]\n' "$(format_checks)" "$ELAPSED"
