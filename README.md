# MeanderAtlas

Personal-first, SaaS-ready spatial travel atlas and trip-planning platform built on **Java 25 LTS**, **Spring Boot 4.1**, **Spring Modulith**, **PostgreSQL 17 + PostGIS**, and **React + MapLibre GL JS v5**.

MeanderAtlas transforms exported Google Maps saved lists and Timeline history into a minimalist 3D globe retrospective and a PostGIS-powered trip discovery and routing engine.

## Stack

- **Backend:** Pure Java 25 LTS (no Lombok), Spring Boot 4.1.3 (Spring MVC + Virtual Threads), Spring Modulith 2.1.1, `NamedParameterJdbcTemplate`, Flyway, PostgreSQL 17 + PostGIS 3.5, `springdoc-openapi` 3.x, Gradle 9.9.2 Kotlin DSL.
- **Quality & Contracts:** Spotless (`google-java-format`), Checkstyle (AST no-comment rule), Error Prone + NullAway (JSpecify `@NullMarked`), ArchUnit + Spring Modulith verification, Testcontainers PostGIS, `gitleaks`, deterministic OpenAPI export (`contracts/openapi.json`).
- **Frontend:** Node 26 + `pnpm 12`, React 19, Vite 8, strict TypeScript (`noUncheckedIndexedAccess`), Tailwind CSS v4, Biome, TanStack Query + `openapi-fetch` + `openapi-typescript`, MapLibre GL JS, Vitest.

## Repository Layout

```text
backend/        Spring Boot 4.1 modular monolith (com.meanderatlas)
contracts/      Generated OpenAPI contract (contracts/openapi.json)
docs/adr/       Concise Architecture Decision Records
docs/modules/   Generated Spring Modulith module canvases
fixtures/       Tiny synthetic public-landmark PL+EN fixtures (zero PII)
scripts/        Verification, privacy guardrail, and git hook scripts
compose.yml     Local PostgreSQL 17 + PostGIS 3.5 service
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
3. Start PostGIS locally (optional for tests, which use Testcontainers):
   ```bash
   docker compose up -d
   ```
4. Run the full verification gate:
   ```bash
   ./scripts/verify.sh
   ```

## Documentation

- Contributor & agent rules: [`AGENTS.md`](AGENTS.md)
- Architecture decisions: [`docs/adr/`](docs/adr/)
- Generated module boundaries: [`docs/modules/`](docs/modules/)
