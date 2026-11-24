---
name: meander-vertical-slice
description: >-
  Implement a MeanderAtlas vertical slice using TDD, Spring Boot 4.1 Modulith boundaries, PostGIS SQL, OpenAPI-to-TypeScript sync, and raw MapLibre.
  Use when building any new feature, endpoint, migration, parser, or UI slice in MeanderAtlas.
---

# MeanderAtlas Vertical Slice Procedure

Follow this sequence for every slice (target 1 focused vertical slice, <400 lines changed per PR):

## 1. Scope & Module Placement

- Check the module table in `AGENTS.md` before creating packages.
- Add a module (`ingestion`, `memories`, `planning`) only when implementing its first real use case—never commit empty placeholder facades.
- Package shape: `com.meanderatlas.<module>/{<Module>Facade, *View, domain/, application/, adapter/{web, persistence, http}}`.
- Annotate every new Java package with `@NullMarked` (JSpecify). Annotate new module roots with `@ApplicationModule(allowedDependencies = {"shared", "atlas"})`.

## 2. Backend TDD Loop

1. Write pure JUnit tests first for domain records, parsers, and use cases using in-memory Fakes (never Mockito—enforced by `ArchitectureTest`).
2. Keep `..domain..` free of `org.springframework..` and `java.sql..`.
3. Keep all SQL in `NamedParameterJdbcTemplate` text blocks (never JPA, Spring Data, `JdbcClient`, or JTS—geometry stays in PostGIS SQL).
4. Expose only `shared` types, JDK types, and root-package DTO records on `*Facade` public methods (including inside `List<T>` / `Optional<T>`).
5. Use `${user.home}` (Java) / `${HOME}` (shell/Compose), never unexpanded `~/` paths.
6. Write a real HTTP + PostGIS Testcontainers `@SpringBootTest(RANDOM_PORT)` E2E test for every endpoint/repository query.
7. Run `./scripts/verify.sh --fast` during iteration; run `./backend/gradlew -p backend spotlessApply test` to regenerate `contracts/openapi.json` and `docs/modules/`.

## 3. Frontend Contract & MapLibre Loop

1. Run `pnpm --dir frontend run gen:api` to sync `frontend/src/api/schema.d.ts` from `contracts/openapi.json`.
2. Place module UI under `frontend/src/features/<module>/` using Tailwind v4 Warm-Paper (`#FAF9F6`) styling.
3. MapLibre rules:
   - Use raw `maplibre-gl` with `setWorkerUrl` via Vite `?url` import.
   - Track style readiness via a `styleLoadedRef` set in `map.on("style.load", ...)`—never gate runtime actions on `map.isStyleLoaded()`, which returns `false` while tiles stream.
   - Avoid `map.setStyle()` runtime style switches that tear down custom GeoJSON/MVT layers and `feature-state`.
   - Reset `dataset.*` attributes and refs in `useEffect` cleanup for React `StrictMode`.
4. Preserve `window.location.hash` and unknown query parameters when updating URL state.
5. Add Vitest unit tests and extend `frontend/e2e/` Playwright assertions to verify real rendered state (not just URL changes), with 0 console/page/network errors.

## 4. Final Verification

- Visually inspect the updated screenshot in `artifacts/proof/`.
- Run `.agents/skills/meander-pr-preflight/scripts/preflight.sh` before committing/pushing.
