# Plan: v0.1 Slice 7 (S2 Materialization + Open Catalog Enrichment) & Map UX Polish

> Ground-truth spec for `/teamwork` workers. Locked in `docs/adr/0005-v01-slice7-s2-enrichment-and-map-ux.md`.
> Follow all rules in `AGENTS.md` (Java 25 pure/immutable, JSpecify `@NullMarked`, zero comments in `src/` & `frontend/e2e/`, no Mockito, >= 90% JaCoCo coverage, OpenAPI-to-TS sync, `./scripts/verify.sh`).

## Locked Scope & Non-Goals
- **In Scope:**
  1. **Track 1 (FE-only quick wins):** Click-to-fly & center with drawer padding; Atlas (`positron`) / Street (`liberty`) basemap toggle; street-zoom region fill fade (`z 9 -> 12`); 10 category pin icons + dot for `UNCATEGORIZED`; collision-avoided place labels at `z >= 12`; collapsible Map Legend chip; `Select area` mode + `Shift+drag` rectangle selection feeding a reusable `PlaceSelectionDialog` (`Mark visited` & `Mark unvisited` with existing Undo toast); displaying `firstVisitedAt` / `lastVisitedAt` in `PlaceDetailCard` (already present on `AtlasPlaceView`).
  2. **Track 2 (BE Slice 7 — Offline S2 Materialization + Open Catalog Enrichment):** Flyway `V4`, `CoordSource.TAKEOUT_S2_APPROX`, pure-Java S2 cell decoder + DMS/decimal title coordinate parser, instant offline materialization of pending `COORDINATE_LOOKUP` CSV jobs (both during `ImportLocalDirectory` AND via a backfill/resolution trigger for already-queued jobs), batch Overpass-first spatial matching + Photon fallback `OpenPoiCatalogPort`, Wikimedia Commons / Wikipedia REST thumbnail resolver for `places.photo_url`, expanded PL/EN/multilingual heuristics, resumable batch runner (opt-in `enabled=false` in tests/CI, dry-run supported, no `150/day` cap), exposing `coordSource` and `photoUrl` on `AtlasPlaceView`, and enrichment progress/trigger endpoints on `ImportController`.
  3. **Track 3 (Final OpenAPI + UI Wire-up):** Regenerate `contracts/openapi.json` and `frontend/src/api/schema.d.ts`, render `photoUrl` banner + subtle approximate-location badge (`coordSource === "TAKEOUT_S2_APPROX"`) in `PlaceDetailCard`, wire the on-demand "Resolve & Enrich" trigger + progress indicator in `ImportStatusBadge` / `TravelStatsPanel`, and run full `./scripts/verify.sh` (+ Docker smoke).
- **Explicitly Deferred to v0.2 / v0.3 (Do NOT Build):**
  - Hiding/excluding non-travel lists (deferred to v0.2).
  - Manual category editing UI/API (deferred to v0.2; Slice 7 enrichment is read-only and never overwrites `category_source = 'MANUAL'`).
  - Marking countries/Admin-1 regions visited with zero saved pins (deferred to v0.2 `memories` / photo EXIF).
  - Trip plan creation action (keep `PlaceSelectionDialog` action bar extensible for v0.3, do not implement Trip Plans yet).

---

## Track 1 — Frontend-Only Map UX Polish & Drag-Box Selection

**Boundary:** Strictly `frontend/` only (zero backend or `contracts/openapi.json` edits). Uses existing OpenAPI types (`AtlasPlaceView`, `BulkVisitStatusRequest` with `VISITED` and `UNVISITED`, `RestoreVisitStatesRequest`).

### 1.1 View State & Basemap Toggle
- File: `frontend/src/features/atlas/viewState.ts` and `frontend/src/features/atlas/AtlasHealthPill.tsx`.
- Add `basemap: "atlas" | "street"` to `AtlasViewState` (default `"atlas"`), persisted in URL param `basemap` (`?basemap=offline` must continue to override with the offline fallback style for Playwright visual proof tests).
- Add a compact 2-way toggle (`Atlas` / `Street`) in `AtlasHealthPill` alongside the `Globe` / `Flat` projection toggle.
- Map styles in `AtlasMapView.tsx`:
  - `"atlas"` -> `https://tiles.openfreemap.org/styles/positron`
  - `"street"` -> `https://tiles.openfreemap.org/styles/liberty`
  - When `basemap` prop changes (and `?basemap=offline` is not active), call `map.setStyle(nextStyleUrl)` cleanly while resetting `styleLoadedRef.current = false`; the existing `style.load` listener re-attaches projection, sources, layers, category icon sprites, and bumps `styleRevision` so feature states & selected filters re-sync automatically.

### 1.2 Click Place -> Fly & Center With Drawer-Aware Padding
- File: `frontend/src/features/atlas/AtlasMapView.tsx` and `frontend/src/App.tsx`.
- Pass a `focusPlaceTarget: { key: string; lng: number; lat: number } | null` and `mapPadding: { top: number; right: number; bottom: number; left: number }` derived from open overlays (`statsOpen`, dossier drawer open, `PlaceDetailCard` open).
- Clicking a place in `RegionDossierDrawer` OR clicking an unclustered pin on the map sets `focusPlaceTarget` (with unique key so re-clicking the same place still re-centers).
- `AtlasMapView` runs `map.easeTo({ center: [lng, lat], zoom: Math.max(map.getZoom(), 14), padding: mapPadding, duration: prefersReducedMotion() ? 0 : 650 })` (respecting `prefers-reduced-motion`).
- Ensure `fitBoundsTarget` (region selection) also uses `mapPadding` so regions never hide behind open drawers.

### 1.3 Street-Zoom Region Fill Fade, Category Pin Icons & `z >= 12` Labels
- **Region fill fade:** On `atlas-countries-fill` and `atlas-admin1-fill`, wrap `fill-opacity` in an `["interpolate", ["linear"], ["zoom"], 9, <existing-expression>, 12, 0]` so choropleth fills fade cleanly to `0` at street zoom while subtle outlines remain readable. Keep neutral transparent/Warm-Paper for regions with `totalPlaces === 0`.
- **Category pin icons:**
  - Create `frontend/src/features/atlas/pinIcons.ts` generating crisp retina-scaled canvas/SVG icons (`pixelRatio: 2`) registered via `map.addImage(...)` on `style.load` (and re-registered on `styleimagemissing` / style switch) for each `PlaceCategory` (`RESTAURANT`, `CAFE_BAR`, `HOTEL`, `MUSEUM`, `LANDMARK`, `SACRAL`, `MOUNTAIN`, `WATER`, `NATURE_PARK`, `VIEWPOINT`; clean circle pin for `UNCATEGORIZED`).
  - Keep existing visit-status fill colors (`#1f6f54` Visited, `#c96a38` Bucket list, `#6d597a` Needs triage) and role ring colors (gold `#d4a017` Must-See/Preferred Hotel, rose `#b84a62` Favorite, `#faf9f6` Backlog) on `atlas-places-pins` circle layer, and overlay an `atlas-places-icons` symbol layer (`icon-allow-overlap: true`, `icon-ignore-placement: true`) rendering the white crisp category glyph centered inside each unclustered pin (at `zoom >= 8` or always on unclustered pins, sized cleanly to fit the circle radius).
  - Include `category` and `canonicalName` in `buildPlacesGeoJson(places)` feature properties.
- **Zoom-in place labels:**
  - When `hasGlyphs` is true, add an `atlas-places-labels` symbol layer (`minzoom: 12`, `filter: ["!", ["has", "point_count"]]`) showing `["get", "canonicalName"]` with `text-offset: [0, 1.35]`, `text-anchor: "top"`, `text-size: 11.5`, `text-allow-overlap: false`, `text-halo-color: "#faf9f6"`, `text-halo-width: 1.5`, `text-color: "#16181d"`.

### 1.4 Collapsible Map Legend Chip
- Create `frontend/src/features/atlas/MapLegend.tsx`.
- Placed bottom-right (`fixed bottom-4 right-4 z-10`, shifting left when `RegionDossierDrawer` is open so it never collides).
- Collapsed by default to a compact Warm-Paper pill (`Legend`); expands into a compact popover explaining:
  - Country/Region shading: Visited emerald gradient (more visited -> deeper shade), Bucket list terracotta, Unmapped neutral paper.
  - Pins: Visited / Bucket list / Needs triage fill colors + Gold ring (Must-See / Preferred Hotel) + Rose ring (Favorite).

### 1.5 Drag-Box Area Selection & Reusable `PlaceSelectionDialog`
- Refactor `BulkVisitDialog.tsx` into (or wrap with) a generic `PlaceSelectionDialog.tsx` accepting:
  - `title: string`, `places: AtlasPlaceView[]`, `isSubmitting: boolean`, `onClose: () => void`,
  - `onMarkVisited: (placeIds: string[]) => void`,
  - `onMarkUnvisited: (placeIds: string[]) => void`,
  - Optional future-extensible `secondaryActions` slot (ready for v0.3 `Add to Trip Plan`).
  - Shows per-place current visit status badge, counts of checked unvisited vs visited items, filter box, Check all / Uncheck all, and disables each action button when 0 applicable places are checked (respecting the backend max batch size of `500`, chunking sequentially or warning cleanly if >500).
- In `AtlasMapView.tsx` + `App.tsx`:
  - Add a compact `"Select area"` toggle button (e.g., in the top control bar or bottom-center action strip) AND support `Shift + drag` on the map canvas even when not toggled.
  - While dragging a box, disable MapLibre dragPan, render a crisp dashed emerald selection overlay rectangle, and on `mouseup` project the two corners via `map.unproject(...)` to find all currently filtered `places` inside that bounding box.
  - If matched places > 0, open `PlaceSelectionDialog` with those places (highlighting them on the map while open).
  - Both `"Mark visited"` and `"Mark unvisited"` call `useBulkUpdateVisitStatus` (`targetStatus: "VISITED"` or `"UNVISITED"`) and populate the existing `UndoToast` with `result.previousStates`.

### 1.6 `PlaceDetailCard` Visit Dates (Already on API)
- Update `frontend/src/features/atlas/PlaceDetailCard.tsx` to format and display `firstVisitedAt` / `lastVisitedAt` concisely when present (e.g., `"Visited Oct 2025"` or `"First Oct 2024 · Last Mar 2026"`).

### 1.7 Track 1 Tests
- Vitest unit tests for `viewState.ts` (`basemap` roundtrip), `PlaceSelectionDialog` (mark visited, mark unvisited, filter, empty guard), `MapLegend` (expand/collapse), `PlaceDetailCard` (visit dates), and `AtlasMapView` helper functions (bounding-box place filtering, drawer-aware padding calculation).
- Keep Playwright E2E selectors (`data-testid`s) intact and green under `pnpm --dir frontend run check:all`.

---

## Track 2 — Backend v0.1 Slice 7 (Offline S2 Materialization + Open Catalog Enrichment)

**Boundary:** Strictly `backend/` (plus `contracts/openapi.json` via `OpenApiExportTest` & `docs/modules/` via `ArchitectureTest`). Respect Modulith dependency direction (`ingestion -> atlas`, `atlas -> shared`, zero Spring/SQL imports in `..domain..`, immutable Java 25, JSpecify `@NullMarked`, zero code comments, no Mockito, >= 90% JaCoCo coverage).

### 2.1 Schema Migration & Shared Enum
- `backend/src/main/java/com/meanderatlas/shared/CoordSource.java`: add `TAKEOUT_S2_APPROX`.
- `backend/src/main/resources/db/migration/V4__add_takeout_s2_approx_coord_source.sql`:
  - Replace the `places.coord_source` CHECK constraint to allow `('TAKEOUT', 'TAKEOUT_S2_APPROX', 'TIMELINE', 'OPEN_CATALOG', 'GOOGLE_TEMP')`.
  - Add `CREATE INDEX idx_resolution_jobs_type_status_next_run ON resolution_jobs (job_type, status, next_run_at) WHERE status IN ('PENDING', 'FAILED');` for efficient resumable batch claiming.
- Update `AtlasPlaceView` to include `@NotNull CoordSource coordSource` and `@Nullable String photoUrl` (and map both columns in `JdbcAtlasRepository` / `AtlasPlaceRowMapper`).

### 2.2 Stage A — Offline Coordinate Extraction (Pure Java Domain, Zero Network)
Place pure-Java domain utilities in `com.meanderatlas.ingestion.domain`:
1. **`GoogleS2CellDecoder`** (pure math, no external S2 dependency needed — standard 64-bit S2 Hilbert-curve face + `(i, j)` -> `(s, t)` -> quadratic `(u, v)` -> `(x, y, z)` -> `(lat, lng)`):
   - Extract `hex1` from FTID `0x<hex1>:0x<hex2>` in Google Maps URLs.
   - Validate `cellId != 0`, `face in 0..5`, and valid non-zero sentinel bit.
   - Return `Optional<GeoPoint>` (verified empirically against 321 real mapped places: 128 m median, 74.1% within 1 km, 95.6% within 10 km).
2. **`TitleCoordinateParser`**:
   - Parse exact DMS coordinates (e.g., `47°55'12.3"N 106°54'19.8"E`, including Unicode prime/double-prime variants) and decimal coordinate titles (e.g., `47.9201, 106.9055`) from `raw_title`.
   - Returns `Optional<GeoPoint>` classified as exact `CoordSource.TAKEOUT`.
3. **Expanded `PlaceCategoryClassifier`**:
   - Expand PL/EN/multilingual keyword rules (e.g., French/Spanish/Italian/German/Asian common travel nouns: `miradouro`, `castillo`, `castello`, `burg`, `palacio`, `palazzo`, `iglesia`, `chiesa`, `eglise`, `berg`, `pic`, `cerro`, `playa`, `spiaggia`, `plage`, `lago`, `lac`, `see`, `cascada`, `cueva`, `grotte`, `mercado`, `market`, `bazar`, `shrine`, `onsen`, `ryokan`, `hostal`, `albergue`, `refugio`, `rifugio`, `hutte`, `chalet`, `cafe`, `boulangerie`, `gelato`, `izakaya`, `taqueria`, `trattoria`, `churrascaria`, `museum`, `museo`, `musee`, `galerie`, `jardin`, `giardino`, `parque`, `parco`, `nationalpark`, etc.) without false positives, keeping all unit tests clean and fast.
4. **Offline Materialization (Both at Import Time AND Backfill for Existing Queued Jobs):**
   - In `ImportLocalDirectory` Pass 2 (`reconcileCsvRow`): if `attachCsvMembershipByCid` returns `false`, try `TitleCoordinateParser` -> `CoordSource.TAKEOUT` (exact!), else `GoogleS2CellDecoder` -> `CoordSource.TAKEOUT_S2_APPROX` (approximate!). Upsert via `AtlasFacade.upsertImportedPlace(...)` (deduplicating by `google_cid` across lists automatically!) and enqueue an `OPEN_CATALOG_ENRICH` job pointing to `place_id` (instead of leaving it stuck as `COORDINATE_LOOKUP`). Only enqueue `COORDINATE_LOOKUP` when neither DMS nor S2 coords exist.
   - Add a dedicated use case **`MaterializePendingCoordinateJobs`** (exposed via `ImportFacade` and `POST /api/v1/imports/materialize-offline`): scans all existing `PENDING` `COORDINATE_LOOKUP` jobs in `resolution_jobs` + `import_records`, runs the exact same CID attach -> DMS title parse -> S2 cell decode -> `upsertImportedPlace` pipeline in bounded transactions, marks resolved `COORDINATE_LOOKUP` jobs `COMPLETED` with `place_id`, and enqueues `OPEN_CATALOG_ENRICH` jobs for all `TAKEOUT_S2_APPROX` + `HEURISTIC` / missing-photo places.
   - Also enqueue `OPEN_CATALOG_ENRICH` jobs idempotently (no duplicate `PENDING` jobs for the same `place_id`) for already-mapped `TAKEOUT` places that still have `category_source = 'HEURISTIC'` or `photo_url IS NULL` or `osm_id IS NULL`.

### 2.3 Stage B — Open Catalog Enrichment (`OpenPoiCatalogPort`)
Place port & domain matching logic in `com.meanderatlas.ingestion.domain.enrichment` and HTTP adapters in `com.meanderatlas.ingestion.adapter.http`:
- **Port:** `OpenPoiCatalogPort` with clean domain DTOs (`EnrichmentTarget`, `OpenPoiCandidate`, `OpenPoiMatch`, `PhotoLookupResult`):
  1. **Batch Overpass-first spatial lookup** (`OverpassPoiClient`):
     - Group claimed `OPEN_CATALOG_ENRICH` jobs into spatial batches (e.g., 20–30 nearby places per request, bounding box / `around:600` for `TAKEOUT` and `around:2500` for `TAKEOUT_S2_APPROX` with sensible radius cap).
     - Fetch OSM elements (`node`, `way`, `relation` with `out center tags;`) carrying `name` + relevant POI tags (`tourism`, `amenity`, `natural`, `historic`, `leisure`, `cuisine`, `building`, `shop`, `wikidata`, `wikimedia_commons`, `wikipedia`, `image`, `addr:city`, `opening_hours`).
     - Pure-Java deterministic **`OpenPoiMatcher`**: scores candidates by normalized name similarity (token overlap + folded Levenshtein/trigram) + haversine distance penalty; rejects low-confidence matches so we never snap a place to the wrong building.
     - Pure-Java **`OsmTagCategoryMapper`**: maps OSM tag combinations (`tourism`, `amenity`, `natural`, `historic`, `leisure`, `cuisine`, etc.) deterministically into `PlaceCategory` + freeform `subcategory`.
  2. **Photon fallback** (`PhotonPoiClient`):
     - Called ONLY for targets where Overpass returned no confident match (and for the ~13–40 `COORDINATE_LOOKUP` rows lacking S2/DMS coords).
     - Calls `GET /api?q=<title>&lat=<anchorLat>&lon=<anchorLng>&limit=5` (or global `GET /api?q=<title>&limit=5` when no anchor exists), applies the same `OpenPoiMatcher` confidence thresholds (stricter name score when global).
  3. **Open Photo Resolver** (`WikimediaPhotoClient`):
     - Resolves `photo_url` ONLY to safe, attribution-clean Wikimedia thumbnail URLs (`https://upload.wikimedia.org/...`):
       - Priority 1: OSM `wikimedia_commons` tag (`File:...`) -> Wikimedia Commons API `imageinfo` (`iiurlwidth=640`).
       - Priority 2: OSM `wikipedia` tag (`<lang>:<Title>`) -> Wikipedia REST `/api/rest_v1/page/summary/<Title>` `thumbnail.source`.
       - Priority 3: OSM `wikidata` tag (`Q...`) -> Wikidata `wbgetentities` `P18` claim -> Commons thumbnail.
       - Never hotlink arbitrary unverified `image` URLs unless hosted on `upload.wikimedia.org`.
- **Atlas Enrichment Write Boundary (`AtlasFacade.applyPlaceEnrichment`):**
  - Accepts `PlaceEnrichmentCommand` (`placeId`, optional refined `GeoPoint` + `CoordSource.OPEN_CATALOG`, optional `osmId`, optional `PlaceCategory` + `subcategory`, optional `cityName`, optional `photoUrl`, optional `openingHoursJson`).
  - **Strict Precedence Rules in SQL / Application:**
    - Coordinates: upgrade `geom` + `coord_source = 'OPEN_CATALOG'` ONLY when current `coord_source` is `'TAKEOUT_S2_APPROX'` (or `NULL`), never overwriting exact `'TAKEOUT'` or `'TIMELINE'` coordinates! Recompute `country_code` and `admin1_id` spatially when `geom` changes.
    - Category: update `category`, `subcategory`, `category_source = 'OPEN_CATALOG'` ONLY when current `category_source <> 'MANUAL'` (respecting ADR-0004).
    - `city_name`, `osm_id`, `photo_url`, `opening_hours_json`: fill when non-null (coalescing cleanly).
- **Execution & Controls (`RunEnrichmentBatch` use case + `EnrichmentProperties`):**
  - Configuration properties (`app.enrichment.*`):
    - `enabled` (default `false` so CI/tests never hit external APIs; set `APP_ENRICHMENT_ENABLED=true` in local `compose.yml` or trigger on-demand via REST!).
    - `batchSize` (default `25`), `requestDelayMs` (default `1100` polite pacing), `maxRetries` (default `3`), `overpassUrl`, `photonUrl`, `wikimediaUserAgent` (explicit descriptive `User-Agent: MeanderAtlas/0.1`).
  - Resumable queue processing via `resolution_jobs`: `PENDING -> IN_PROGRESS -> COMPLETED | FAILED (exponential backoff) | SKIPPED (unresolvable after max attempts)`.
  - REST endpoints on `ImportController`:
    - `POST /api/v1/imports/materialize-offline` -> runs `MaterializePendingCoordinateJobs`, returns summary (`materializedCount`, `exactFromTitleCount`, `approxFromS2Count`, `mergedByCidCount`, `remainingLookupCount`, `enqueuedEnrichmentCount`).
    - `POST /api/v1/imports/enrich?limit=50&dryRun=false` -> runs one bounded resumable batch of `RunEnrichmentBatch` (or kicks off async drain when `background=true` via virtual thread with single-flight guard so concurrent clicks never double-run!), returns `EnrichmentBatchResultView`.
  - Expand `ImportStatusView` cleanly with enrichment counters (`pendingCoordinateLookupCount`, `pendingEnrichmentCount`, `enrichedPlaceCount`, `approximatePlaceCount`, `enrichmentRunning`) while keeping `pendingResolutionCount` for backward clarity (or clearly documented in OpenAPI).

### 2.4 Track 2 Tests (Testing Trophy + >= 90% JaCoCo)
- Pure JUnit unit tests:
  - `GoogleS2CellDecoderTest` (known landmarks, face 0–5, zero/invalid inputs).
  - `TitleCoordinateParserTest` (DMS, Unicode primes, decimal lat/lng, non-coordinate titles).
  - `OpenPoiMatcherTest` & `OsmTagCategoryMapperTest` (high-confidence match, homonym distance rejection, multilingual tags, `MANUAL` precedence).
  - Expanded `PlaceCategoryClassifierTest`.
- In-memory Fake `OpenPoiCatalogPort` (NO Mockito!) for `RunEnrichmentBatch` & `MaterializePendingCoordinateJobs` unit + Spring Boot `@SpringBootTest(RANDOM_PORT)` PostGIS Testcontainers E2E tests covering:
  - Offline materialization of synthetic CSV jobs (`TAKEOUT_S2_APPROX` + DMS `TAKEOUT` + CID dedup).
  - Batch enrichment upgrading `TAKEOUT_S2_APPROX` -> `OPEN_CATALOG` + category + `city_name` + `photo_url`, preserving exact `TAKEOUT` coords and `MANUAL` categories, dry-run making zero DB writes, retry/backoff on transient errors, and transaction rollback tests.
- Regenerate `contracts/openapi.json` & `docs/modules/` via Gradle `test` and verify `./backend/gradlew -p backend spotlessCheck checkstyleMain checkstyleTest test jacocoTestReport jacocoTestCoverageVerification`.

---

## Track 3 — Final OpenAPI Sync & UI Wire-Up (After Tracks 1 & 2 Merge)

1. Run `pnpm --dir frontend run gen:api` to sync `frontend/src/api/schema.d.ts` with the updated `contracts/openapi.json` (`coordSource`, `photoUrl`, new `ImportStatusView` counters, `/api/v1/imports/materialize-offline`, `/api/v1/imports/enrich`).
2. **`PlaceDetailCard.tsx`**:
   - Render `photoUrl` as a clean rounded editorial photo header (with `loading="lazy"`, graceful error fallback hiding broken images, and subtle Wikimedia attribution cue).
   - Show a compact `"Approximate location"` pill when `place.coordSource === "TAKEOUT_S2_APPROX"` (auto-disappears once upgraded to `OPEN_CATALOG`).
3. **`ImportStatusBadge.tsx` & `useLocalImport.ts`**:
   - Display pending offline materialization / open-catalog enrichment progress cleanly (e.g., `"6,247 awaiting coordinates"` -> `"Materialize now"` button, `"7,100 pending enrichment"` -> `"Enrich batch"` / `"Enrich all"` button with live polling while `enrichmentRunning`).
4. **Full Verification Gate:**
   - Run `./scripts/verify.sh` (full suite + OpenAPI/doc drift + Playwright visual proof) and `./scripts/verify.sh --docker`.
   - Smoke-test against the live `meander-atlas-mvp` Docker stack (`http://localhost:8088`): trigger `/api/v1/imports/materialize-offline` so all ~6.1k places (including Mongolia!) appear immediately, and test a small `enrich?limit=25` batch against live Overpass/Photon/Wikimedia!
