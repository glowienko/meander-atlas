# ADR-0002: Cartographic Editorial Frontend, Raw MapLibre, and OpenAPI Sync

## Context
The frontend must render a smooth 3D globe and 2D map with zero paid tile keys while preventing API schema drift across fast agent-driven slices.

## Decision
Use React 19, Vite 8, strict TypeScript (`noUncheckedIndexedAccess`), Tailwind CSS v4 (Warm-Paper `#FAF9F6` light palette — deferring dark theme to avoid `map.setStyle()` custom-layer teardowns), Biome 2, a TypeScript AST `ts.createSourceFile` no-comments gate with unit tests, raw `maplibre-gl` with OpenFreeMap Positron + Vite `?url` worker bundling + `styleLoadedRef` projection state, URL-persisted view state, and `openapi-typescript` + `openapi-fetch` synced from `contracts/openapi.json`.

## Consequences
- Any backend API contract change fails typechecking or `./scripts/verify.sh` drift checks until `src/api/schema.d.ts` is regenerated.
- Raw `maplibre-gl` avoids wrapper lag for native 3D globe ⇄ 2D mercator transitions and MVT feature-state styling without style-reload complexity.
