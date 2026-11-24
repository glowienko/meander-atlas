#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

VIOLATIONS=()

if [[ -f AGENTS.md ]]; then
  agents_lines="$(wc -l < AGENTS.md | tr -d '[:space:]')"
  if [[ "$agents_lines" -gt 120 ]]; then
    VIOLATIONS+=("AGENTS.md exceeds 120 lines ($agents_lines lines)")
  fi
fi

if [[ -d docs/adr ]]; then
  while IFS= read -r adr; do
    [[ -z "$adr" ]] && continue
    adr_lines="$(wc -l < "$adr" | tr -d '[:space:]')"
    if [[ "$adr_lines" -gt 15 ]]; then
      VIOLATIONS+=("$adr exceeds 15 lines ($adr_lines lines)")
    fi
  done < <(find docs/adr -maxdepth 1 -type f -name '*.md' | LC_ALL=C sort)
fi

if [[ "${#VIOLATIONS[@]}" -gt 0 ]]; then
  printf 'Doc budget violations detected:\n' >&2
  for v in "${VIOLATIONS[@]}"; do
    printf '  - %s\n' "$v" >&2
  done
  exit 1
fi
