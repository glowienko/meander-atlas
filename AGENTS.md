# MeanderAtlas — Agent & Engineering Guide

## Pinned Toolchain & Stack
- **Java:** 25 LTS (pure Java, no Lombok; records + JSpecify `@NullMarked` / `@Nullable`)
- **Backend:** Spring Boot `4.1.3` (Spring MVC on Tomcat + Virtual Threads, no WebFlux), Spring Modulith `2.1.1`, Gradle `9.9.2` Kotlin DSL (`backend/gradle/libs.versions.toml`)
- **Database:** PostgreSQL `17` + PostGIS `3.5` (`postgis/postgis:17-3.5`), `NamedParameterJdbcTemplate` only (no JPA, no Spring Data, no `JdbcClient`, no JTS — geometry stays in SQL), Flyway migrations
- **Frontend:** Node `26`, `pnpm 12`, React 19 + Vite 8 + strict TypeScript (`noUncheckedIndexedAccess`), Tailwind CSS v4, Biome 2, TanStack Query + `openapi-fetch`, MapLibre GL JS
- **Security:** `gitleaks` + `scripts/check-privacy.sh`

## Commands
- Fast loop (unit + compile + NullAway + Checkstyle + FE lint/typecheck): `./scripts/verify.sh --fast`
- Full gate (privacy + gitleaks + Spotless + Checkstyle + Modulith/ArchUnit + Testcontainers PostGIS + OpenAPI/doc drift + FE + Playwright): `./scripts/verify.sh`
- Format backend: `./backend/gradlew -p backend spotlessApply`
- Local PostGIS: `docker compose up -d`
- Install local hooks: `./scripts/install-hooks.sh`

## Modular Monolith Map (`com.meanderatlas`)

| Module | Owns (tables) | Public API (root package only) | Phase |
| --- | --- | --- | --- |
| `shared` | none | `UserId`, `PlaceId`, `GeoPoint`, `VisitSource`, `CurrentUserProvider` | Phase 0 / v0.1 |
| `atlas` | `places`, `place_visit_state`, `place_memberships`, `region_boundaries` | `AtlasFacade`, `AtlasHealthView`, event `PlacesChanged` | Phase 0 / v0.1 |
| `ingestion` | `import_batches`, `imported_signals`, `resolution_jobs` | `ImportFacade` | v0.1 |
| `memories` | `location_visits`, `trips`, `home_bases`, `reviews` | `MemoriesFacade` | v0.2 |
| `planning` | `trip_plans`, `trip_chat_sessions`, chat memory | `PlanningFacade` | v0.3–v0.4 |

### Layout & Dependency Rules
- Backend package shape: `com.meanderatlas.<module>/{<Module>Facade, *View, domain/, application/, adapter/{web, persistence, http}}`.
- Frontend mirrors modules under `frontend/src/features/<module>/`.
- One use case per class named verb + noun (e.g., `GetAtlasHealth`, `MarkPlacesVisited`).
- Module direction: `ingestion -> atlas`, `memories -> atlas`, `planning -> atlas`; `atlas` depends on no feature module.
- Facades only accept/return `shared` types, JDK types, and DTO records — never `..domain..` aggregates.
- `..domain..` must not import `org.springframework..` or `java.sql..`.
- Enforced by `ArchitectureTest` (`ApplicationModules.verify()` + ArchUnit + `Documenter -> docs/modules/`).

## Spring Boot 4.1 Gotchas
- **Jackson 3:** Use `tools.jackson.*` packages (never legacy `com.fasterxml.jackson.*`).
- **Modular starters:** Explicitly use `spring-boot-starter-webmvc` (not `web`) and `spring-boot-starter-flyway` + `flyway-database-postgresql`.
- **Nullability:** Use JSpecify `org.jspecify.annotations.NullMarked` per package and `@Nullable` (never `org.springframework.lang.*`); enforced as compile errors via Error Prone + NullAway.
- **Resilience:** Use Spring Framework 7 core `@Retryable` / `@ConcurrencyLimit` (no Resilience4j).
- **Testcontainers 2.x:** `org.testcontainers.postgresql.PostgreSQLContainer` (non-generic) with `@ServiceConnection`.

## Code & Testing Rules
- **No code comments in `src/`:** Names, types, and tests carry intent; put design rationale in `docs/adr/NNNN-title.md` (<=15 lines: Context, Decision, Consequences). Enforced via AST (Checkstyle for Java, TS scanner for TS/TSX allowing only `biome-ignore` / `@ts-expect-error` / `@vite-ignore`).
- **Testing Trophy:** Pure JUnit for domain/parsers; in-memory Fakes for external ports (never `Mockito.verify(...)`); real HTTP + PostGIS Testcontainers `@SpringBootTest(RANDOM_PORT)` E2E tests.
- **Contract sync:** `OpenApiExportTest` generates `contracts/openapi.json`; `pnpm --dir frontend run gen:api` updates `frontend/src/api/schema.d.ts`. `verify.sh` fails on drift.

## Public-Repo Privacy & Never-Commit List
Real Takeout/Timeline exports live outside the repo via `APP_IMPORTS_DIR` (see `.env.example`). **Never commit:**
- `.env` or `.env.*` (except `.env.example`)
- `*.pem`, `*.key`, `token.json`, `credentials*.json`
- `data/`, `pgdata/`, `artifacts/`
- `*.sql.gz`, `*.dump`, `*.kml`, `*.kmz`, `*.gpx`
- `*.zip`, `*Takeout*`, `*Timeline*`, `*Zapisane*`, `*Opinie*` (except small synthetic public-landmark files in `fixtures/synthetic-*`)
- Any file `> 500 KB` outside `fixtures/` and `frontend/public/`
