# ADR-0004: MVP v0.1 Living Atlas Schema, Precedence, and Resolution Guards

## Context
MVP v0.1 introduces PostGIS spatial persistence, multi-account Takeout/Timeline reconciliation, Natural Earth 1:10m boundaries, and resumable leftover resolution while honoring Google Terms (30-day coordinate TTL) and zero-placeholder Modulith boundaries.

## Decision
Add `V2__living_atlas_schema.sql` (`pg_trgm` + 7 tables: `region_boundaries` with `COUNTRY | ADMIN1 | CITY`, `places` with 11 coarse categories + freeform `subcategory` + `city_name`, `place_visit_state`, `place_memberships`, `import_batches`, `import_records`, `resolution_jobs`) while deferring the `ingestion` Java module until its first use case. Track `places.category_source` (`HEURISTIC | OPEN_CATALOG | MANUAL`) so deterministic PL/EN import heuristics light up Slices 1–6 and Slice 7's `OpenPoiCatalogPort` upgrades `HEURISTIC` rows without overwriting `MANUAL` edits. Enforce `GOOGLE_TEMP` 30-day `coord_expires_at` via `CHECK`, stateless per-place `PlaceVisitState` snapshot restoration for bulk Undo, and user-wins precedence (`MANUAL > REVIEW > TIMELINE`) where unmatched Timeline visits stay in `import_records`.

## Consequences
- Every downstream v0.1 slice builds on one lean PostGIS schema without migration churn or empty placeholder facades.
- Slice 7 agents MUST query `category_source = 'HEURISTIC'` (plus `UNCATEGORIZED`) to verify and upgrade provisional categories and subcategories via OSM/Overture, running opt-in (`enabled=false`, dry-run, 150/day cap).
