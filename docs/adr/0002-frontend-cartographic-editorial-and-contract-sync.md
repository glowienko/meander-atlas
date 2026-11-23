# ADR-0002: Cartographic Editorial Frontend, Raw MapLibre, and OpenAPI Sync

## Context
The frontend must render a smooth 3D globe and 2D map with zero paid tile keys while preventing frontend/backend API schema drift across fast agent-driven slices.

## Decision
Use React 19, Vite 8, strict TypeScript (`noUncheckedIndexedAccess`), Tailwind CSS v4 (Warm-Paper `#FAF9F6` / Obsidian `#0F1115`), Biome 2, a TypeScript `ts.createScanner` no-comments gate, raw `maplibre-gl` with OpenFreeMap vector styles and Vite `?url` worker bundling, URL-persisted UI view state, and `openapi-typescript` + `openapi-fetch` synced from `contracts/openapi.json`.

## Consequences
- Any backend API contract change immediately fails typechecking or `./scripts/verify.sh` drift checks until `src/api/schema.d.ts` is regenerated.
- Raw `maplibre-gl` avoids wrapper lag for native 3D globe ⇄ 2D mercator transitions and MVT feature-state styling.
