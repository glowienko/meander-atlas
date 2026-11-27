# ADR-0005: v0.1 Slice 7 Offline S2 Materialization, Open Enrichment, and Map UX

## Context
Real Takeout CSV imports queue 6,247 `COORDINATE_LOOKUP` jobs (99.4% carrying 64-bit Google S2 Cell IDs in `0x<s2>:0x<cid>` URLs with 128 m median accuracy, plus 27 DMS/decimal titles), while 68% of mapped places remain `UNCATEGORIZED` with zero photos or cities, and partial-region visit marking plus zoomed-in map ergonomics need polish before v0.2.

## Decision
Add `V4__add_takeout_s2_approx_coord_source.sql` extending `places.coord_source` with `TAKEOUT_S2_APPROX` and adding a queue index on `resolution_jobs`. Stage A materializes pending CSV rows offline (DMS/decimal title parser + S2 cell decoder + `google_cid` dedup) onto the map immediately, enqueuing `OPEN_CATALOG_ENRICH` (and `COORDINATE_LOOKUP` only for rows lacking S2/DMS hints). Stage B supersedes ADR-0004's `150/day` cap with a resumable, politely rate-limited, batch Overpass-first + Photon-fallback `OpenPoiCatalogPort` and Wikimedia Commons/Wikipedia thumbnail resolver (disabled in CI/tests, dry-run and on-demand trigger supported), keeping Slice 7 enrichment read-only (`MANUAL` category UI, hidden lists, and zero-pin visited regions deferred to v0.2). On the frontend, add click-to-fly centering with drawer padding, an Atlas (`positron`) / Street (`liberty`) basemap toggle, street-zoom region fill fade (`z 9–12`), category pin icons, `z >= 12` place labels, a collapsible map legend, and a `Select area` (`Shift+drag`) rectangle flow feeding a reusable `PlaceSelectionDialog` (`Mark visited` / `Mark unvisited` + Undo, structured for v0.3 Trip Plans).

## Consequences
- All ~6.1k saved bucket-list places appear on the map offline in seconds with zero Google APIs or 30-day TTL, then upgrade progressively to exact `OPEN_CATALOG` coordinates, categories, cities, and open photos.
- Track 1 (FE-only map UX & drag-box selection) and Track 2 (BE S2 materialization + Overpass/Photon/Wikimedia Slice 7) can execute in parallel before a minimal OpenAPI/UI wire-up.
