#!/usr/bin/env bash
set -euo pipefail

FORCE=false
for arg in "$@"; do
  case "$arg" in
    --force) FORCE=true ;;
    *)
      printf 'Usage: %s [--force]\n' "$0" >&2
      exit 2
      ;;
  esac
done

BOUNDARIES_DIR="${APP_BOUNDARIES_DIR:-${HOME}/meander-atlas-data/boundaries}"
mkdir -p "$BOUNDARIES_DIR"

BASE_URL="https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson"
COUNTRIES_FILE="ne_10m_admin_0_countries.geojson"
ADMIN1_FILE="ne_10m_admin_1_states_provinces.geojson"

CURRENT_TMP_FILE=""
cleanup() {
  if [[ -n "${CURRENT_TMP_FILE:-}" ]]; then
    rm -f "$CURRENT_TMP_FILE"
  fi
}
trap cleanup EXIT INT TERM

download_if_needed() {
  local filename="$1"
  local target="${BOUNDARIES_DIR}/${filename}"
  local url="${BASE_URL}/${filename}"

  if [[ "$FORCE" != true && -s "$target" ]]; then
    local existing_bytes
    existing_bytes="$(wc -c < "$target" | tr -d '[:space:]')"
    printf 'Exists (%s bytes): %s\n' "$existing_bytes" "$target"
    return 0
  fi

  CURRENT_TMP_FILE="$(mktemp "${BOUNDARIES_DIR}/.${filename}.XXXXXX")"
  printf 'Downloading %s -> %s\n' "$url" "$target"
  curl -fsSL --retry 3 --retry-delay 2 "$url" -o "$CURRENT_TMP_FILE"
  mv -f "$CURRENT_TMP_FILE" "$target"
  CURRENT_TMP_FILE=""

  local downloaded_bytes
  downloaded_bytes="$(wc -c < "$target" | tr -d '[:space:]')"
  printf 'Saved (%s bytes): %s\n' "$downloaded_bytes" "$target"
}

download_if_needed "$COUNTRIES_FILE"
download_if_needed "$ADMIN1_FILE"
