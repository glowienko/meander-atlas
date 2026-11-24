#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PRIVACY_SCRIPT="$REPO_ROOT/scripts/check-privacy.sh"
BASH_BIN="/bin/bash"
if [[ ! -x "$BASH_BIN" ]]; then
  BASH_BIN="$(command -v bash)"
fi

TMP_REPO="$(mktemp -d)"
trap 'rm -rf "$TMP_REPO"' EXIT

git -C "$TMP_REPO" init -q
git -C "$TMP_REPO" config user.name "test"
git -C "$TMP_REPO" config user.email "test@example.com"

run_privacy_staged() {
  (cd "$TMP_REPO" && "$BASH_BIN" "$PRIVACY_SCRIPT" --staged) >/dev/null 2>&1
}

assert_pass() {
  local label="$1"
  if ! run_privacy_staged; then
    printf 'FAIL: expected pass for %s\n' "$label" >&2
    exit 1
  fi
}

assert_fail() {
  local label="$1"
  if run_privacy_staged; then
    printf 'FAIL: expected failure for %s\n' "$label" >&2
    exit 1
  fi
}

assert_pass "empty staged set under system bash (Bash 3.2 set -u safety)"

mkdir -p "$TMP_REPO/fixtures" "$TMP_REPO/data"
printf 'APP_PORT=8080\n' > "$TMP_REPO/.env.example"
printf '{"ok":true}\n' > "$TMP_REPO/fixtures/synthetic-sample.json"
git -C "$TMP_REPO" add .env.example fixtures/synthetic-sample.json
assert_pass "allowed .env.example and small synthetic fixture"
git -C "$TMP_REPO" commit -q -m "base"

git -C "$TMP_REPO" rm -q .env.example
assert_pass "deletion-only staged commit"
git -C "$TMP_REPO" reset -q --hard HEAD

for blocked in ".env" ".env.local" "secret.pem" "Takeout.zip" "Timeline.json" "Zapisane miejsca.csv" "Opinie.json" "data/export.csv" "backend/src/test/resources/Timeline.json" "backend/src/main/resources/Takeout.csv"; do
  mkdir -p "$(dirname "$TMP_REPO/$blocked")"
  printf 'blocked\n' > "$TMP_REPO/$blocked"
  git -C "$TMP_REPO" add -f "$blocked"
  assert_fail "blocked path $blocked"
  git -C "$TMP_REPO" reset -q -- "$blocked"
  rm -f "$TMP_REPO/$blocked"
done

for allowed_src in "backend/src/main/java/com/meanderatlas/ingestion/domain/TakeoutCsvParser.java" "backend/src/main/java/com/meanderatlas/ingestion/domain/TimelineParser.java" "frontend/src/features/ingestion/TimelineView.tsx"; do
  mkdir -p "$(dirname "$TMP_REPO/$allowed_src")"
  printf 'package test;\n' > "$TMP_REPO/$allowed_src"
  git -C "$TMP_REPO" add -f "$allowed_src"
  assert_pass "allowed source path $allowed_src"
  git -C "$TMP_REPO" reset -q -- "$allowed_src"
  rm -f "$TMP_REPO/$allowed_src"
done

dd if=/dev/zero of="$TMP_REPO/fixtures/synthetic-too-big.json" bs=1024 count=60 status=none
git -C "$TMP_REPO" add -f fixtures/synthetic-too-big.json
assert_fail "synthetic fixture over 50 KB"
git -C "$TMP_REPO" reset -q -- fixtures/synthetic-too-big.json
rm -f "$TMP_REPO/fixtures/synthetic-too-big.json"

dd if=/dev/zero of="$TMP_REPO/blob.bin" bs=1024 count=520 status=none
git -C "$TMP_REPO" add -f blob.bin
printf 'tiny\n' > "$TMP_REPO/blob.bin"
assert_fail "staged blob over 500 KB even if working tree was shrunk"
git -C "$TMP_REPO" reset -q -- blob.bin
rm -f "$TMP_REPO/blob.bin"
