# ADR-0003: Playwright Live Visual Proof Gate and Full-Stack Docker Compose

## Context
Static builds and unit tests do not catch WebGL worker bundling, tile-loading failures, or reverse-proxy wiring errors at runtime.

## Decision
Require `./scripts/verify.sh` (and GitHub Actions `ci`) to boot an ephemeral PostGIS `17-3.5` container, the Spring Boot `bootJar`, and Vite preview, then run Playwright headless Chromium to assert live PostGIS health, 0 console/page/network errors, and capture `artifacts/proof/phase0-globe.png`. Provide multi-stage `backend/` and `frontend/` Dockerfiles behind `compose.yml`'s `app` profile.

## Consequences
- Every UI change produces an inspectable viewport proof screenshot locally and as a CI artifact while remaining gitignored.
- Local development stays fast (`docker compose up -d` for PostGIS only; `docker compose --profile app up --build` for full containers).
