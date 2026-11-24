#!/usr/bin/env bash
set -euo pipefail

MODE="all"
if [[ "${1:-}" == "--staged" ]]; then
  MODE="staged"
fi

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

FILES=()
if [[ "$MODE" == "staged" ]]; then
  while IFS= read -r -d '' f; do
    FILES+=("$f")
  done < <(git diff --cached --name-only -z --diff-filter=ACMR)
else
  while IFS= read -r -d '' f; do
    FILES+=("$f")
  done < <(git ls-files -z --cached --others --exclude-standard)
fi

VIOLATIONS=()
MAX_DEFAULT_BYTES=512000
MAX_FIXTURE_SYNTHETIC_BYTES=51200

file_size_bytes() {
  local target="$1"
  if [[ "$MODE" == "staged" ]]; then
    git cat-file -s ":$target" 2>/dev/null || wc -c < "$target" | tr -d '[:space:]'
  elif [[ -f "$target" ]]; then
    wc -c < "$target" | tr -d '[:space:]'
  else
    printf '0'
  fi
}

if [[ "${#FILES[@]}" -gt 0 ]]; then
  for f in "${FILES[@]}"; do
    [[ -z "$f" ]] && continue
    base="$(basename "$f")"
    lower="$(printf '%s' "$f" | tr '[:upper:]' '[:lower:]')"
    lower_base="$(printf '%s' "$base" | tr '[:upper:]' '[:lower:]')"

    if [[ "$base" == ".env" || ( "$base" == .env.* && "$base" != ".env.example" ) ]]; then
      VIOLATIONS+=("blocked env file: $f")
      continue
    fi

    case "$lower_base" in
      *.pem|*.key|token.json|credentials*.json|*.sql.gz|*.dump|*.kml|*.kmz|*.gpx)
        VIOLATIONS+=("blocked sensitive/export file: $f")
        continue
        ;;
    esac

    case "$f" in
      data/*|pgdata/*|artifacts/*)
        VIOLATIONS+=("blocked runtime directory file: $f")
        continue
        ;;
    esac

    is_synthetic_fixture=false
    if [[ "$f" == fixtures/synthetic-* ]]; then
      is_synthetic_fixture=true
    fi

    is_source_code=false
    case "$f" in
      backend/src/*.java|frontend/src/*.ts|frontend/src/*.tsx)
        is_source_code=true
        ;;
    esac

    if [[ "$is_synthetic_fixture" == false && "$is_source_code" == false ]]; then
      if [[ "$lower_base" == *.zip || "$lower" == *takeout* || "$lower" == *timeline* || "$lower" == *zapisane* || "$lower" == *opinie* ]]; then
        VIOLATIONS+=("blocked archive or personal export path: $f")
        continue
      fi
    fi

    if [[ "$MODE" == "staged" || -f "$f" ]]; then
      size_bytes="$(file_size_bytes "$f")"
      if [[ "$is_synthetic_fixture" == true && "$size_bytes" -gt "$MAX_FIXTURE_SYNTHETIC_BYTES" ]]; then
        VIOLATIONS+=("synthetic fixture exceeds 50 KB ($size_bytes bytes): $f")
      elif [[ "$f" != fixtures/* && "$f" != frontend/public/* && "$size_bytes" -gt "$MAX_DEFAULT_BYTES" ]]; then
        VIOLATIONS+=("file exceeds 500 KB outside fixtures/ and frontend/public/ ($size_bytes bytes): $f")
      fi
    fi
  done
fi

if [[ "${#VIOLATIONS[@]}" -gt 0 ]]; then
  printf 'Privacy guardrail violations detected:\n' >&2
  for v in "${VIOLATIONS[@]}"; do
    printf '  - %s\n' "$v" >&2
  done
  exit 1
fi
