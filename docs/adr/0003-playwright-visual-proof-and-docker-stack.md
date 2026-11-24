# ADR-0003: Playwright Live Visual Proof Gate and Full-Stack Docker Compose

## Context
Static builds, unit tests, and `docker compose config` alone do not catch WebGL worker bundling, missing container healthcheck binaries, Node 26 Corepack removal, or reverse-proxy wiring errors.

## Decision
Require `./scripts/verify.sh` to boot ephemeral PostGIS `17-3.5`, the Spring Boot `bootJar`, and Vite preview, asserting live PostGIS health, projection toggles, 0 console/network errors, and `artifacts/proof/phase0-globe.png`. Build containers with multi-stage `eclipse-temurin:25-jdk-noble` → `25-jre-alpine` and `node:26-bookworm-slim` (`npm install -g pnpm@12.15.0`) → `caddy:2-alpine`, verified live by `./scripts/verify-docker.sh` (`verify.sh --docker` in CI).

## Consequences
- Every UI change produces an inspectable viewport screenshot locally and as a CI artifact while remaining gitignored.
- Every Dockerfile, healthcheck, and Caddy proxy change is proven end-to-end before merge while keeping local PostGIS dev fast (`docker compose up -d`).
