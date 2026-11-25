# MeanderAtlas — Agent & Engineering Guide

> **Status:** Phase 0 (PRs #1–#4) and v0.1 Slice 1 (PR #7) merged on `master`. MVP v0.1 `/grill-me` decisions locked (see `docs/adr/0004-v01-living-atlas-schema-and-resolution.md` and Google Drive Architecture Plan Section 8); Slice 2 in progress.

## Pinned Toolchain & Stack
- **Java:** 25 LTS (pure Java, no Lombok; records + JSpecify `@NullMarked` / `@Nullable`; extract `static final` constants for reused values, thresholds, and pinned versions in `libs.versions.toml` → `TestEnvironment`, but inline single-use error/validation messages and field names)
- **Backend:** Spring Boot `4.1.3` (Spring MVC + Virtual Threads + Actuator `/actuator/health/{liveness,readiness}` + RFC 9457 `problemdetails` + `forward-headers-strategy: framework` + graceful shutdown, no WebFlux), Spring Modulith `2.1.1`, Gradle `9.9.2` Kotlin DSL (`backend/gradle/libs.versions.toml`)
- **Database:** PostgreSQL `17` + PostGIS `3.5` (`postgis/postgis:17-3.5`), `NamedParameterJdbcTemplate` only (no JPA, no Spring Data, no `JdbcClient`, no JTS — geometry stays in SQL), Flyway migrations
- **Frontend:** Node `26`, `pnpm 12`, React 19 + Vite 8 + strict TypeScript (`noUncheckedIndexedAccess`), Tailwind CSS v4 (Warm-Paper `#FAF9F6`), Biome 2, TanStack Query + `openapi-fetch`, MapLibre GL JS (raw, `?url` worker, `styleLoadedRef` — no `setStyle` dark-theme complexity)
- **Containers & Security:** Temurin 25 (`jdk-noble` → `jre-alpine`), Node 26 (`bookworm-slim` + `npm install -g pnpm`, no Corepack) → `caddy:2-alpine`, `gitleaks`, `scripts/check-privacy.sh`

## Commands
- Fast loop (privacy + doc budget + guardrails + unit + compile + NullAway + Checkstyle + FE lint/AST/typecheck/unit): `./scripts/verify.sh --fast`
- Full gate (+ gitleaks + Spotless + PostGIS Testcontainers + OpenAPI/doc drift + FE build + Playwright visual proof): `./scripts/verify.sh`
- Full + containerized stack smoke (`compose --profile app` build, healthchecks, Caddy proxy): `./scripts/verify.sh --docker` (or `./scripts/verify-docker.sh`)
- Format backend / frontend: `./backend/gradlew -p backend spotlessApply` / `pnpm --dir frontend run format`
- Local PostGIS / hooks: `docker compose up -d` / `./scripts/install-hooks.sh`

## Modular Monolith Map (`com.meanderatlas`)

| Module | Owns (tables) | Public API (root package only) | Phase |
| --- | --- | --- | --- |
| `shared` | none | `UserId`, `PlaceId`, `GeoPoint`, `VisitSource`, `PlaceCategory`, `CategorySource`, `ListKind`, `CurrentUserProvider` (interface) | Phase 0 / v0.1 |
| `atlas` | `places`, `place_visit_state`, `place_memberships`, `region_boundaries` | `AtlasFacade`, `AtlasHealthView`, event `PlacesChanged` | Phase 0 / v0.1 |
| `ingestion` | `import_batches`, `import_records`, `resolution_jobs` | `ImportFacade` | v0.1 |
| `memories` | `location_visits`, `trips`, `home_bases`, `reviews` | `MemoriesFacade` | v0.2 |
| `planning` | `trip_plans`, `trip_chat_sessions`, chat memory | `PlanningFacade` | v0.3–v0.4 |

### Layout & Dependency Rules
- Add modules only in their scheduled phase (no premature empty placeholder facades); declare `@ApplicationModule(allowedDependencies = ...)` when created.
- Backend package shape: `com.meanderatlas.<module>/{<Module>Facade, *View, domain/, application/, adapter/{web, persistence, http}}`; split growing `..domain..` packages into cohesive subpackages (each with `@NullMarked` `package-info.java`) and place cross-module domain value primitives/enums in `com.meanderatlas.shared`.
- Frontend mirrors modules under `frontend/src/features/<module>/`.
- One use case per class named verb + noun (e.g., `GetAtlasHealth`, `MarkPlacesVisited`).
- Module direction: `ingestion -> atlas`, `memories -> atlas`, `planning -> atlas`; `atlas` depends only on `shared`.
- Facades only accept/return `shared` types, JDK types, and root-package DTO records — never `..domain..`, `..application..`, or `..adapter..` types (including inside generics like `List<T>`).
- `..domain..` must not import `org.springframework..` or `java.sql..`.
- Enforced deterministically by `ArchitectureTest` (`ApplicationModules.verify()`, ArchUnit forbidden-stack & no-Mockito rules, and `Documenter -> docs/modules/`).

## Spring Boot 4.1 Gotchas
- **Jackson 3:** Use `tools.jackson.*` packages (never legacy `com.fasterxml.jackson.*`).
- **Modular starters:** Explicitly use `spring-boot-starter-webmvc` (not `web`) and `spring-boot-starter-flyway` + `flyway-database-postgresql`.
- **Nullability:** Use JSpecify `org.jspecify.annotations.NullMarked` per package and `@Nullable` (never `org.springframework.lang.*`); enforced as compile errors via Error Prone + NullAway.
- **Paths & Resilience:** Never use unexpanded `~/` in Java/Compose paths (use `${user.home}` / `${HOME}`); use Spring Framework 7 core `@Retryable` / `@ConcurrencyLimit` (no Resilience4j).
- **Testcontainers 2.x:** `org.testcontainers.postgresql.PostgreSQLContainer` (non-generic) with `@ServiceConnection`.

## Code, Testing & Agent Skills
- **Immutability & Functional Java 25:** Embrace immutable collections (`List.of`, `Set.of`, `Map.of`, `Stream.toList`, `Collectors.toUnmodifiable*`, `Stream.builder`) and concise `Stream`/`Optional`/`switch` expressions over temporary mutable `ArrayList`/`HashSet`/`HashMap` builders or `for`/`continue` loops (enforced by `ArchitectureTest` + Checkstyle); keep a small private scanner only for genuinely stateful lexers rather than convoluted functional chains.
- **No code comments in `src/` or `frontend/e2e/`:** Names, types, and tests carry intent; put design rationale in `docs/adr/NNNN-title.md` (`<=15` lines: Context, Decision, Consequences; `AGENTS.md <=120` lines). Enforced via AST (Checkstyle + `ts.createSourceFile` parser with unit tests).
- **Testing Trophy:** Pure JUnit for domain/parsers; in-memory Fakes for external ports (Mockito banned by `ArchitectureTest`); real HTTP + PostGIS Testcontainers `@SpringBootTest(RANDOM_PORT)` E2E tests.
- **Contract sync:** `OpenApiExportTest` generates `contracts/openapi.json`; `pnpm --dir frontend run gen:api` updates `frontend/src/api/schema.d.ts`. `verify.sh` fails on drift.
- **Repo Skills (`.agents/skills/`):** `meander-vertical-slice` (TDD slice workflow & pitfalls), `meander-pr-preflight` (deterministic pre-PR gate + Docker & proof checks), `meander-correct` (turn every review finding into a permanent automated guard).

## Public-Repo Privacy & Never-Commit List
Real Takeout/Timeline exports live outside the repo via `APP_IMPORTS_DIR` (see `.env.example`). **Never commit:**
- `.env` or `.env.*` (except `.env.example`)
- `*.pem`, `*.key`, `token.json`, `credentials*.json`
- `data/`, `pgdata/`, `artifacts/`
- `*.sql.gz`, `*.dump`, `*.kml`, `*.kmz`, `*.gpx`
- `*.zip`, `*Takeout*`, `*Timeline*`, `*Zapisane*`, `*Opinie*` (except small synthetic public-landmark files in `fixtures/synthetic-*`)
- Any file `> 500 KB` outside `fixtures/` and `frontend/public/`
