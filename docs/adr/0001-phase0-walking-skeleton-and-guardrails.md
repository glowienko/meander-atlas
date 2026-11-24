# ADR-0001: Phase 0 Walking Skeleton, Modular Monolith, and Guardrails

## Context
MeanderAtlas starts as a public personal-first portfolio repo that must prevent accidental PII/secret leaks while keeping agent-driven development fast and deterministic.

## Decision
Use a single Gradle 9 / Spring Boot 4.1 / Java 25 Spring Modulith backend (`com.meanderatlas`) with PostgreSQL 17 + PostGIS, `NamedParameterJdbcTemplate`, Flyway, JSpecify + Error Prone/NullAway, Spotless, Checkstyle AST no-comments, ArchUnit module rules, deterministic `contracts/openapi.json` export, `gitleaks`, and a quiet `./scripts/verify.sh` gate wired to GitHub Actions job `ci`.

## Consequences
- Every architectural invariant, privacy rule, and OpenAPI/module-doc contract is verified automatically in seconds with zero manual checklists.
- Code comments in `src/` are banned in favor of self-documenting types, tests, and thin ADRs.
- External data exports remain strictly outside the repo via `APP_IMPORTS_DIR`.
