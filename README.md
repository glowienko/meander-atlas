# MeanderAtlas

Personal-first, SaaS-ready spatial travel atlas and trip-planning platform built on **Java 25 LTS**, **Spring Boot 4.1**, **Spring Modulith**, **PostgreSQL 17 + PostGIS**, and **React + MapLibre GL JS**.

MeanderAtlas transforms exported Google Maps saved lists and Timeline history into a minimalist 3D globe retrospective and a PostGIS-powered trip discovery and routing engine.

## Stack

- **Backend:** Pure Java 25 LTS (no Lombok), Spring Boot 4.1.3 (Spring MVC + Virtual Threads), Spring Modulith 2.1.1 (`shared`, `atlas`, `ingestion`, `memories`, `planning`), `NamedParameterJdbcTemplate`, Flyway, PostgreSQL 17 + PostGIS 3.5, `springdoc-openapi` 3.x, Gradle 9.9.2 Kotlin DSL.
- **Frontend:** Node 26 + `pnpm 12`, React 19, Vite 8, strict TypeScript (`noUncheckedIndexedAccess`), Tailwind CSS v4, Biome 2, TanStack Query + `openapi-fetch` + `openapi-typescript`, MapLibre GL JS + OpenFreeMap, Vitest, Playwright.
- **Quality & Guardrails:** Spotless (`google-java-format`), Checkstyle & TS AST no-comment rules, Error Prone + NullAway (JSpecify `@NullMarked`), ArchUnit + Spring Modulith verification, PostGIS Testcontainers, deterministic OpenAPI & module-doc drift gates, Playwright visual proof (`artifacts/proof/`), `gitleaks`, and privacy guardrails.

## Repository Layout

```text
backend/        Spring Boot 4.1 modular monolith (com.meanderatlas) + Dockerfile
frontend/       React 19 + Vite 8 + MapLibre GL JS app + Caddy Dockerfile
contracts/      Generated OpenAPI contract (contracts/openapi.json)
docs/adr/       Concise Architecture Decision Records
docs/modules/   Generated Spring Modulith module canvases
fixtures/       Tiny synthetic public-landmark PL+EN fixtures (zero PII)
scripts/        Verification, E2E visual proof, privacy, and git hook scripts
compose.yml     Local PostGIS service + optional full-stack `app` profile
```

## Quickstart

1. Copy the environment template (real exports live outside the repo at `APP_IMPORTS_DIR`):
   ```bash
   cp .env.example .env
   ```
2. Install local git pre-commit privacy and secret-scanning hooks:
   ```bash
   ./scripts/install-hooks.sh
   ```
3. Run fast loop or full end-to-end verification (includes PostGIS Testcontainers + Playwright proof):
   ```bash
   ./scripts/verify.sh --fast
   ./scripts/verify.sh
   ```
4. Start PostGIS only for local dev, or the full containerized stack (`http://localhost:8088`):
   ```bash
   docker compose up -d
   docker compose --profile app up --build
   ```

## Documentation

- Contributor & agent rules: [`AGENTS.md`](AGENTS.md)
- Architecture decisions: [`docs/adr/`](docs/adr/)
- Generated module boundaries: [`docs/modules/`](docs/modules/)
