import type {
  ExpressionSpecification,
  FilterSpecification,
  GeoJSONSource,
  LayerSpecification,
  Map as MapLibreMap,
  PointLike,
  VectorSourceSpecification,
  VectorTileSource,
} from "maplibre-gl";
import type { AtlasPlaceView, RegionLevel, RegionSummaryView } from "../../api/client";
import {
  ACCENT,
  FAVORITE_RING,
  INK,
  MUST_SEE_RING,
  PAPER,
  STATUS_COLORS,
  withAlpha,
} from "./atlasPalette";
import { pinIconId } from "./pinIcons";
import type { RegionScope } from "./viewState";

export const STREET_ZOOM = 9;
export const PLACE_DETAIL_ZOOM = 12;

const CLUSTER_RADIUS_PX = 44;
const COUNTRIES_SOURCE_ID = "atlas-countries";
const ADMIN1_SOURCE_ID = "atlas-admin1";
const PLACES_SOURCE_ID = "atlas-places";
const SOURCE_LAYER_REGIONS = "regions";

const LAYER = {
  countriesFill: "atlas-countries-fill",
  countriesOutline: "atlas-countries-outline",
  admin1Fill: "atlas-admin1-fill",
  admin1Outline: "atlas-admin1-outline",
  clusters: "atlas-places-clusters",
  clusterCount: "atlas-places-cluster-count",
  selected: "atlas-places-selected",
  areaSelected: "atlas-places-area-selected",
  pins: "atlas-places-pins",
  icons: "atlas-places-icons",
  labels: "atlas-places-labels",
} as const;

const ADMIN1_LAYERS = [LAYER.admin1Fill, LAYER.admin1Outline];

export type MapHit =
  | { kind: "place"; placeId: string }
  | { kind: "cluster"; clusterId: number; center: [number, number] }
  | { kind: "region"; level: RegionLevel; code: string };

export interface RegionSelection {
  countrySummaries: readonly RegionSummaryView[];
  admin1Summaries: readonly RegionSummaryView[];
  selectedCountryCode: string;
  selectedRegionLevel: RegionScope | "";
  selectedRegionCode: string;
}

const NOT_CLUSTER: ExpressionSpecification = ["!", ["has", "point_count"]];
const IS_CLUSTER: ExpressionSpecification = ["has", "point_count"];
const HAS_VISITED: ExpressionSpecification = [
  ">",
  ["coalesce", ["feature-state", "visitedPlaces"], 0],
  0,
];
const HAS_PLACES: ExpressionSpecification = [
  ">",
  ["coalesce", ["feature-state", "totalPlaces"], 0],
  0,
];
const IS_SELECTED: ExpressionSpecification = ["boolean", ["feature-state", "selected"], false];

function byRole<T extends number | string>(highlight: T, favorite: T, fallback: T) {
  return [
    "match",
    ["get", "derivedRole"],
    ["PREFERRED_HOTEL", "MUST_SEE"],
    highlight,
    "FAVORITE",
    favorite,
    fallback,
  ] as ExpressionSpecification;
}

function regionTileUrl(level: RegionLevel, countryCode = ""): string {
  const base = `${window.location.origin}/api/v1/atlas/tiles/regions/${level}/{z}/{x}/{y}.mvt`;
  return countryCode.length > 0 ? `${base}?countryCode=${encodeURIComponent(countryCode)}` : base;
}

function regionSource(tiles: string[]): VectorSourceSpecification {
  return { type: "vector", tiles, promoteId: "code", minzoom: 0, maxzoom: 14 };
}

function regionFillLayer(
  id: string,
  source: string,
  opacity: { visited: number; completionBoost: number; bucketList: number },
): LayerSpecification {
  return {
    id,
    type: "fill",
    source,
    "source-layer": SOURCE_LAYER_REGIONS,
    paint: {
      "fill-color": [
        "case",
        HAS_VISITED,
        STATUS_COLORS.VISITED,
        HAS_PLACES,
        STATUS_COLORS.UNVISITED,
        "transparent",
      ],
      "fill-opacity": [
        "interpolate",
        ["linear"],
        ["zoom"],
        STREET_ZOOM,
        [
          "case",
          HAS_VISITED,
          [
            "+",
            opacity.visited,
            ["*", ["coalesce", ["feature-state", "completionRatio"], 0], opacity.completionBoost],
          ],
          HAS_PLACES,
          opacity.bucketList,
          0,
        ],
        PLACE_DETAIL_ZOOM,
        0,
      ],
    },
  };
}

function regionOutlineLayer(
  id: string,
  source: string,
  lineColor: ExpressionSpecification,
  baseWidth: number,
): LayerSpecification {
  return {
    id,
    type: "line",
    source,
    "source-layer": SOURCE_LAYER_REGIONS,
    paint: {
      "line-color": lineColor,
      "line-width": ["case", IS_SELECTED, 2, baseWidth],
    },
  };
}

function selectedPlaceFilter(placeId: string): FilterSpecification {
  return ["all", NOT_CLUSTER, ["==", ["get", "id"], placeId]];
}

function highlightedPlacesFilter(placeIds: readonly string[]): FilterSpecification {
  return ["all", NOT_CLUSTER, ["in", ["get", "id"], ["literal", [...placeIds]]]];
}

function placeRing(id: string, filter: FilterSpecification, radius: number, fillAlpha: number) {
  return {
    id,
    type: "circle",
    source: PLACES_SOURCE_ID,
    filter,
    paint: {
      "circle-radius": radius,
      "circle-color": withAlpha(ACCENT, fillAlpha),
      "circle-stroke-color": ACCENT,
      "circle-stroke-width": 2,
    },
  } satisfies LayerSpecification;
}

const REGION_LAYERS: readonly LayerSpecification[] = [
  regionFillLayer(LAYER.countriesFill, COUNTRIES_SOURCE_ID, {
    visited: 0.16,
    completionBoost: 0.28,
    bucketList: 0.12,
  }),
  regionOutlineLayer(
    LAYER.countriesOutline,
    COUNTRIES_SOURCE_ID,
    [
      "case",
      IS_SELECTED,
      ACCENT,
      HAS_VISITED,
      withAlpha(ACCENT, 0.5),
      HAS_PLACES,
      withAlpha(STATUS_COLORS.UNVISITED, 0.55),
      withAlpha(INK, 0.08),
    ],
    0.8,
  ),
  {
    ...regionFillLayer(LAYER.admin1Fill, ADMIN1_SOURCE_ID, {
      visited: 0.18,
      completionBoost: 0.3,
      bucketList: 0.14,
    }),
    layout: { visibility: "none" },
  },
  {
    ...regionOutlineLayer(
      LAYER.admin1Outline,
      ADMIN1_SOURCE_ID,
      ["case", IS_SELECTED, ACCENT, withAlpha(INK, 0.25)],
      0.9,
    ),
    layout: { visibility: "none" },
  },
];

const PLACE_LAYERS: readonly LayerSpecification[] = [
  {
    id: LAYER.clusters,
    type: "circle",
    source: PLACES_SOURCE_ID,
    filter: IS_CLUSTER,
    paint: {
      "circle-color": INK,
      "circle-radius": ["step", ["get", "point_count"], 15, 25, 19, 100, 23],
      "circle-stroke-color": PAPER,
      "circle-stroke-width": 2,
    },
  },
  placeRing(LAYER.selected, selectedPlaceFilter(""), 13, 0.18),
  placeRing(LAYER.areaSelected, highlightedPlacesFilter([]), 12, 0.22),
  {
    id: LAYER.pins,
    type: "circle",
    source: PLACES_SOURCE_ID,
    filter: NOT_CLUSTER,
    paint: {
      "circle-color": [
        "match",
        ["get", "visitStatus"],
        "VISITED",
        STATUS_COLORS.VISITED,
        "NEEDS_TRIAGE",
        STATUS_COLORS.NEEDS_TRIAGE,
        STATUS_COLORS.UNVISITED,
      ],
      "circle-radius": byRole(7.5, 6.5, 5.5),
      "circle-stroke-color": byRole(MUST_SEE_RING, FAVORITE_RING, PAPER),
      "circle-stroke-width": byRole(2.5, 2, 1.5),
    },
  },
  {
    id: LAYER.icons,
    type: "symbol",
    source: PLACES_SOURCE_ID,
    filter: ["all", NOT_CLUSTER, ["!=", ["get", "icon"], ""]],
    layout: {
      "icon-image": ["get", "icon"],
      "icon-size": byRole(0.85, 0.75, 0.65),
      "icon-allow-overlap": true,
      "icon-ignore-placement": true,
    },
  },
];

const TEXT_LAYERS: readonly LayerSpecification[] = [
  {
    id: LAYER.clusterCount,
    type: "symbol",
    source: PLACES_SOURCE_ID,
    filter: IS_CLUSTER,
    layout: {
      "text-field": "{point_count_abbreviated}",
      "text-font": ["Noto Sans Bold"],
      "text-size": 11,
    },
    paint: { "text-color": PAPER },
  },
  {
    id: LAYER.labels,
    type: "symbol",
    source: PLACES_SOURCE_ID,
    minzoom: PLACE_DETAIL_ZOOM,
    filter: NOT_CLUSTER,
    layout: {
      "text-field": ["get", "canonicalName"],
      "text-font": ["Noto Sans Regular"],
      "text-size": 11.5,
      "text-offset": [0, 1.35],
      "text-anchor": "top",
      "text-allow-overlap": false,
    },
    paint: { "text-color": INK, "text-halo-color": PAPER, "text-halo-width": 1.5 },
  },
];

type GeoJsonData = Parameters<GeoJSONSource["setData"]>[0];

function buildPlacesGeoJson(places: readonly AtlasPlaceView[]): GeoJsonData {
  return {
    type: "FeatureCollection",
    features: places.map((place) => ({
      type: "Feature",
      geometry: { type: "Point", coordinates: [place.lng, place.lat] },
      properties: {
        id: place.id,
        visitStatus: place.visitStatus,
        derivedRole: place.derivedRole,
        icon: pinIconId(place.category),
        canonicalName: place.canonicalName,
      },
    })),
  };
}

export function addAtlasLayers(map: MapLibreMap): void {
  if (map.getSource(PLACES_SOURCE_ID)) {
    return;
  }
  const style = map.getStyle();
  const firstSymbolId = style.layers.find((layer) => layer.type === "symbol")?.id;
  map.addSource(COUNTRIES_SOURCE_ID, regionSource([regionTileUrl("COUNTRY")]));
  map.addSource(ADMIN1_SOURCE_ID, regionSource([]));
  map.addSource(PLACES_SOURCE_ID, {
    type: "geojson",
    data: buildPlacesGeoJson([]),
    cluster: true,
    clusterMaxZoom: PLACE_DETAIL_ZOOM,
    clusterRadius: CLUSTER_RADIUS_PX,
  });
  for (const layer of REGION_LAYERS) {
    map.addLayer(layer, firstSymbolId);
  }
  for (const layer of [...PLACE_LAYERS, ...(style.glyphs ? TEXT_LAYERS : [])]) {
    map.addLayer(layer);
  }
}

export function setPlaces(map: MapLibreMap, places: readonly AtlasPlaceView[]): void {
  map.getSource<GeoJSONSource>(PLACES_SOURCE_ID)?.setData(buildPlacesGeoJson(places));
}

export function setPlaceHighlights(
  map: MapLibreMap,
  selectedPlaceId: string | null,
  highlightedPlaceIds: readonly string[],
): void {
  if (map.getLayer(LAYER.selected) && map.getLayer(LAYER.areaSelected)) {
    map.setFilter(LAYER.selected, selectedPlaceFilter(selectedPlaceId ?? ""));
    map.setFilter(LAYER.areaSelected, highlightedPlacesFilter(highlightedPlaceIds));
  }
}

export function showAdmin1Country(map: MapLibreMap, countryCode: string): void {
  if (countryCode.length > 0) {
    map
      .getSource<VectorTileSource>(ADMIN1_SOURCE_ID)
      ?.setTiles([regionTileUrl("ADMIN1", countryCode)]);
  }
  for (const layerId of ADMIN1_LAYERS.filter((id) => map.getLayer(id))) {
    map.setLayoutProperty(layerId, "visibility", countryCode.length > 0 ? "visible" : "none");
  }
}

function syncRegionFeatureStates(
  map: MapLibreMap,
  source: string,
  summaries: readonly RegionSummaryView[],
  selectedCode: string,
): void {
  if (!map.getSource(source)) {
    return;
  }
  map.removeFeatureState({ source, sourceLayer: SOURCE_LAYER_REGIONS });
  const states = summaries.some((summary) => summary.code === selectedCode)
    ? summaries
    : [...summaries, { code: selectedCode, totalPlaces: 0, visitedPlaces: 0, completionRatio: 0 }];
  for (const state of states.filter((summary) => summary.code.length > 0)) {
    map.setFeatureState(
      { source, sourceLayer: SOURCE_LAYER_REGIONS, id: state.code },
      {
        totalPlaces: state.totalPlaces,
        visitedPlaces: state.visitedPlaces,
        completionRatio: state.completionRatio,
        selected: state.code === selectedCode,
      },
    );
  }
}

export function syncRegionStates(map: MapLibreMap, selection: RegionSelection): void {
  syncRegionFeatureStates(
    map,
    COUNTRIES_SOURCE_ID,
    selection.countrySummaries,
    selection.selectedCountryCode,
  );
  syncRegionFeatureStates(
    map,
    ADMIN1_SOURCE_ID,
    selection.admin1Summaries,
    selection.selectedRegionLevel === "ADMIN1" ? selection.selectedRegionCode : "",
  );
}

function interactiveLayerIds(map: MapLibreMap, selectedCountryCode: string): string[] {
  return [
    LAYER.icons,
    LAYER.pins,
    LAYER.clusters,
    ...(selectedCountryCode.length > 0 ? [LAYER.admin1Fill] : []),
    LAYER.countriesFill,
  ].filter((layerId) => Boolean(map.getLayer(layerId)));
}

export function hitAt(
  map: MapLibreMap,
  point: PointLike,
  selectedCountryCode: string,
): MapHit | null {
  const layers = interactiveLayerIds(map, selectedCountryCode);
  const feature = layers.length > 0 ? map.queryRenderedFeatures(point, { layers })[0] : undefined;
  const layerId = feature?.layer.id;
  const { id, code, cluster_id: clusterId } = feature?.properties ?? {};
  if ((layerId === LAYER.icons || layerId === LAYER.pins) && typeof id === "string") {
    return { kind: "place", placeId: id };
  }
  if (layerId === LAYER.clusters && typeof clusterId === "number") {
    const [lng = 0, lat = 0] =
      feature?.geometry.type === "Point" ? feature.geometry.coordinates : [];
    return { kind: "cluster", clusterId, center: [lng, lat] };
  }
  if (
    (layerId === LAYER.admin1Fill || layerId === LAYER.countriesFill) &&
    typeof code === "string"
  ) {
    return { kind: "region", level: layerId === LAYER.admin1Fill ? "ADMIN1" : "COUNTRY", code };
  }
  return null;
}

export function clusterExpansionZoom(map: MapLibreMap, clusterId: number) {
  return map.getSource<GeoJSONSource>(PLACES_SOURCE_ID)?.getClusterExpansionZoom(clusterId);
}
