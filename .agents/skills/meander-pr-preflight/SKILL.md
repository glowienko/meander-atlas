---
name: meander-pr-preflight
description: >-
  Run deterministic pre-PR verification for MeanderAtlas before committing, pushing, or updating any pull request.
  Use whenever preparing a PR, updating an existing PR branch, or changing Dockerfiles, compose.yml, scripts, CI, or UI components.
---

# MeanderAtlas PR Preflight

Run the deterministic preflight script before pushing or opening/updating any PR:

```bash
.agents/skills/meander-pr-preflight/scripts/preflight.sh [base-ref]
```

## What `preflight.sh` Enforces Deterministically

1. **Git hygiene & identity:** Verifies current branch is not `master`/`main`, local `user.name` is `glovi-ai-bot` (when running as bot), and working tree privacy/doc budgets pass.
2. **Bash 3.2 portability check:** Syntax-checks all repo shell scripts under `/bin/bash -n` and runs `scripts/test-guardrails.sh`.
3. **Full verification gate:** Runs `./scripts/verify.sh` (privacy, doc budgets, guardrail regression tests, `gitleaks` git + working tree, Spotless, Checkstyle (size/params/complexity/catch/inline-JSON limits), Error Prone + NullAway, Spring Modulith + ArchUnit, PostGIS Testcontainers, OpenAPI & module-doc drift, Biome (incl. complexity limits), ESLint React Compiler rules, knip dead-code, jscpd duplication, TS AST comment checker + tests, `tsc -b`, Vitest, Vite build, and Playwright visual proof).
4. **Automatic Docker full-stack smoke test:** Whenever any `Dockerfile`, `.dockerignore`, `Caddyfile`, `compose.yml`, `build.gradle.kts`, `libs.versions.toml`, `package.json`, or `pnpm-lock.yaml` changed relative to `[base-ref]` (default `origin/master`), automatically runs `./scripts/verify-docker.sh` (builds both images, waits for container `HEALTHCHECK`s, and asserts `/` and proxied `/api/v1/atlas/health`).
5. **Visual proof check:** Whenever `frontend/` files changed, confirms `artifacts/proof/phase0-globe.png` (or the slice's proof PNG) is non-empty and reminds the agent to visually inspect it before pushing.

## Stacked PR & Push Rules

- Never force-push an open PR branch after review begins; add incremental fix commits and merge updated parent branches down the stack cleanly.
- Never self-merge PRs or commit directly to `master`.
- Never ship unverified container configs: `docker compose config` alone is insufficient—always run `./scripts/verify-docker.sh`.
