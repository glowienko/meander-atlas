#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

chmod +x .githooks/pre-commit scripts/check-privacy.sh scripts/test-guardrails.sh scripts/verify.sh scripts/install-hooks.sh scripts/run-e2e-proof.sh
git config --local core.hooksPath .githooks
printf 'Configured local git core.hooksPath=.githooks\n'
