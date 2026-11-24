---
name: meander-correct
description: >-
  Encode review findings, recurring bugs, or broken assumptions into permanent deterministic checks (ArchUnit, guardrail tests, AST checks, Playwright assertions, Docker smoke tests) and concise AGENTS.md updates.
  Use whenever a PR review or bug report uncovers a mistake that existing checks failed to catch.
---

# MeanderAtlas Correct Loop

Never leave a review finding as a one-off code edit. Convert every failure mode into a deterministic 0-token guard so it cannot recur:

## 1. Classify Where the Guard Belongs

| Failure Class | Permanent Deterministic Guard |
| --- | --- |
| Forbidden dependency, wrong Jackson/nullability API, Mockito, leaky Facade generic type | Add/extend an ArchUnit rule + negative synthetic test in `backend/src/test/java/com/meanderatlas/ArchitectureTest.java` |
| Shell/privacy hook edge case (macOS Bash 3.2 `set -u`, staged blob size, blocked filenames) | Add a failing test case to `scripts/test-guardrails.sh` (executed under `/bin/bash`) |
| Comment-checker false positive or missed comment (JSX, templates, regex) | Add a failing test case to `frontend/scripts/check-no-comments.test.mjs` |
| Doc bloat (`AGENTS.md > 120` lines, `docs/adr/*.md > 15` lines) | Enforced by `scripts/check-docs-budget.sh` |
| Container build, missing healthcheck tool, reverse-proxy/compose failure | Add assertion to `scripts/verify-docker.sh` |
| Runtime browser/WebGL/worker/map-state bug | Add state assertion (not URL-only) to `frontend/e2e/` Playwright spec |

## 2. Red → Green → Encode

1. **Red:** Add the regression test or guard check first and confirm it fails against the buggy behavior.
2. **Green:** Fix the root cause cleanly without adding unnecessary abstraction.
3. **Update docs sparingly:** Only add a rule to `AGENTS.md` if it cannot be expressed purely in code/lints, keeping `AGENTS.md <= 120` lines and any ADR `<= 15` lines.
4. **Verify:** Run `.agents/skills/meander-pr-preflight/scripts/preflight.sh` and confirm all gates pass cleanly.
