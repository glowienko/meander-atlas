import {
  type GeoJSONSource,
  Map as MapLibreMap,
  type StyleSpecification,
  setWorkerUrl,
  type VectorTileSource,
} from "maplibre-gl";
import maplibreWorkerUrl from "maplibre-gl/dist/maplibre-gl-worker.mjs?url";
import { useEffect, useRef, useState } from "react";
import type { AtlasPlaceView, RegionLevel, RegionSummaryView } from "../../api/client";
import {
  clampMapPadding,
  type FocusPlaceTarget,
  filterPlacesInScreenRect,
  isSameGlobePoint,
  type MapPadding,
  normalizeLngNear,
} from "./mapGeometry";
import {
  PIN_ICON_CATEGORY_LIST,
  PIN_ICON_IDS,
  registerCategoryPinIcon,
  registerCategoryPinIcons,
} from "./pinIcons";
import type { AtlasBasemap, AtlasProjection, RegionScope } from "./viewState";

setWorkerUrl(maplibreWorkerUrl);

export interface MapViewport {
  zoom: number;
  minLng: number;
  minLat: number;
  maxLng: number;
  maxLat: number;
}

export interface FitBoundsTarget {
  key: string;
  minLng: number;
  minLat: number;
  maxLng: number;
  maxLat: number;
}

interface AtlasMapViewProps {
  projection: AtlasProjection;
  basemap: AtlasBasemap;
  places: AtlasPlaceView[];
  countrySummaries: RegionSummaryView[];
  admin1Summaries: RegionSummaryView[];
  selectedCountryCode: string;
  selectedRegionLevel: RegionScope | "";
  selectedRegionCode: string;
  selectedPlaceId: string | null;
  highlightedPlaceIds?: readonly string[];
  fitBoundsTarget: FitBoundsTarget | null;
  focusPlaceTarget: FocusPlaceTarget | null;
  mapPadding: MapPadding;
  areaSelectMode: boolean;
  onSelectPlace: (placeId: string) => void;
  onSelectRegion: (level: RegionLevel, code: string) => void;
  onViewportChange: (viewport: MapViewport) => void;
  onSelectAreaPlaces: (places: AtlasPlaceView[]) => void;
}

interface DragBoxState {
  startX: number;
  startY: number;
  currentX: number;
  currentY: number;
}

const POSITRON_STYLE_URL = "https://tiles.openfreemap.org/styles/positron";
const LIBERTY_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty";
const COUNTRIES_SOURCE_ID = "atlas-countries";
const ADMIN1_SOURCE_ID = "atlas-admin1";
const PLACES_SOURCE_ID = "atlas-places";
const SOURCE_LAYER_REGIONS = "regions";
const MIN_DRAG_BOX_PX = 6;
const MIN_FOCUS_ZOOM = 14;
const MIN_FIT_BOUNDS_PADDING = 48;

function createFallbackStyle(): StyleSpecification {
  return {
    version: 8,
    sources: {},
    layers: [
      {
        id: "background",
        type: "background",
        paint: {
          "background-color": "#faf9f6",
        },
      },
    ],
  };
}

function isOfflineBasemap(): boolean {
  const params = new URLSearchParams(window.location.search);
  return params.get("basemap") === "offline";
}

function resolveStyle(basemap: AtlasBasemap): string | StyleSpecification {
  if (isOfflineBasemap()) {
    return createFallbackStyle();
  }
  return basemap === "street" ? LIBERTY_STYLE_URL : POSITRON_STYLE_URL;
}

function countryTileUrl(): string {
  return `${window.location.origin}/api/v1/atlas/tiles/regions/COUNTRY/{z}/{x}/{y}.mvt`;
}

function admin1TileUrl(countryCode: string): string {
  return `${window.location.origin}/api/v1/atlas/tiles/regions/ADMIN1/{z}/{x}/{y}.mvt?countryCode=${encodeURIComponent(countryCode)}`;
}

type GeoJsonData = Parameters<GeoJSONSource["setData"]>[0];

function buildPlacesGeoJson(places: AtlasPlaceView[]): GeoJsonData {
  return {
    type: "FeatureCollection",
    features: places.map((place) => ({
      type: "Feature",
      geometry: {
        type: "Point",
        coordinates: [place.lng, place.lat],
      },
      properties: {
        id: place.id,
        visitStatus: place.visitStatus,
        derivedRole: place.derivedRole,
        category: place.category,
        canonicalName: place.canonicalName,
      },
    })),
  };
}

function prefersReducedMotion(): boolean {
  return (
    typeof window !== "undefined" &&
    typeof window.matchMedia === "function" &&
    window.matchMedia("(prefers-reduced-motion: reduce)").matches
  );
}

function emitViewport(map: MapLibreMap, callback: (viewport: MapViewport) => void): void {
  const bounds = map.getBounds();
  callback({
    zoom: map.getZoom(),
    minLng: bounds.getWest(),
    minLat: bounds.getSouth(),
    maxLng: bounds.getEast(),
    maxLat: bounds.getNorth(),
  });
}

function interactiveLayerIds(map: MapLibreMap, selectedCountryCode: string): string[] {
  return [
    "atlas-places-icons",
    "atlas-places-pins",
    "atlas-places-clusters",
    ...(selectedCountryCode.length > 0 ? ["atlas-admin1-fill"] : []),
    "atlas-countries-fill",
  ].filter((layerId) => Boolean(map.getLayer(layerId)));
}

function syncRegionFeatureStates(
  map: MapLibreMap,
  sourceId: string,
  summaries: RegionSummaryView[],
  isSelected: (code: string) => boolean,
  fallbackSelectedCode?: string,
): void {
  if (!map.getSource(sourceId)) {
    return;
  }
  map.removeFeatureState({ source: sourceId, sourceLayer: SOURCE_LAYER_REGIONS });
  for (const summary of summaries) {
    map.setFeatureState(
      {
        source: sourceId,
        sourceLayer: SOURCE_LAYER_REGIONS,
        id: summary.code,
      },
      {
        totalPlaces: summary.totalPlaces,
        visitedPlaces: summary.visitedPlaces,
        completionRatio: summary.completionRatio,
        selected: isSelected(summary.code),
      },
    );
  }
  if (
    fallbackSelectedCode &&
    fallbackSelectedCode.length > 0 &&
    !summaries.some((summary) => summary.code === fallbackSelectedCode)
  ) {
    map.setFeatureState(
      {
        source: sourceId,
        sourceLayer: SOURCE_LAYER_REGIONS,
        id: fallbackSelectedCode,
      },
      {
        totalPlaces: 0,
        visitedPlaces: 0,
        completionRatio: 0,
        selected: true,
      },
    );
  }
}

function syncSourcesAndLayers(
  map: MapLibreMap,
  initialCountryCode: string,
  initialPlaces: AtlasPlaceView[],
  initialHighlightedPlaceIds: readonly string[],
): void {
  const firstSymbolId = map.getStyle()?.layers?.find((layer) => layer.type === "symbol")?.id;
  const hasGlyphs = Boolean(map.getStyle()?.glyphs);

  if (!map.getSource(COUNTRIES_SOURCE_ID)) {
    map.addSource(COUNTRIES_SOURCE_ID, {
      type: "vector",
      tiles: [countryTileUrl()],
      promoteId: "code",
      minzoom: 0,
      maxzoom: 14,
    });
    map.addLayer(
      {
        id: "atlas-countries-fill",
        type: "fill",
        source: COUNTRIES_SOURCE_ID,
        "source-layer": SOURCE_LAYER_REGIONS,
        paint: {
          "fill-color": [
            "case",
            [">", ["coalesce", ["feature-state", "visitedPlaces"], 0], 0],
            "#1f6f54",
            [">", ["coalesce", ["feature-state", "totalPlaces"], 0], 0],
            "#c96a38",
            "transparent",
          ],
          "fill-opacity": [
            "interpolate",
            ["linear"],
            ["zoom"],
            9,
            [
              "case",
              [">", ["coalesce", ["feature-state", "visitedPlaces"], 0], 0],
              ["+", 0.16, ["*", ["coalesce", ["feature-state", "completionRatio"], 0], 0.28]],
              [">", ["coalesce", ["feature-state", "totalPlaces"], 0], 0],
              0.12,
              0,
            ],
            12,
            0,
          ],
        },
      },
      firstSymbolId,
    );
    map.addLayer(
      {
        id: "atlas-countries-outline",
        type: "line",
        source: COUNTRIES_SOURCE_ID,
        "source-layer": SOURCE_LAYER_REGIONS,
        paint: {
          "line-color": [
            "case",
            ["boolean", ["feature-state", "selected"], false],
            "#1d5b4c",
            [">", ["coalesce", ["feature-state", "visitedPlaces"], 0], 0],
            "rgba(29, 91, 76, 0.5)",
            [">", ["coalesce", ["feature-state", "totalPlaces"], 0], 0],
            "rgba(201, 106, 56, 0.55)",
            "rgba(22, 24, 29, 0.08)",
          ],
          "line-width": ["case", ["boolean", ["feature-state", "selected"], false], 2, 0.8],
        },
      },
      firstSymbolId,
    );
  }

  if (!map.getSource(ADMIN1_SOURCE_ID)) {
    const admin1Tiles = initialCountryCode.length > 0 ? [admin1TileUrl(initialCountryCode)] : [];
    const visibility = initialCountryCode.length > 0 ? "visible" : "none";
    map.addSource(ADMIN1_SOURCE_ID, {
      type: "vector",
      tiles: admin1Tiles,
      promoteId: "code",
      minzoom: 0,
      maxzoom: 14,
    });
    map.addLayer(
      {
        id: "atlas-admin1-fill",
        type: "fill",
        source: ADMIN1_SOURCE_ID,
        "source-layer": SOURCE_LAYER_REGIONS,
        layout: { visibility },
        paint: {
          "fill-color": [
            "case",
            [">", ["coalesce", ["feature-state", "visitedPlaces"], 0], 0],
            "#1f6f54",
            [">", ["coalesce", ["feature-state", "totalPlaces"], 0], 0],
            "#c96a38",
            "transparent",
          ],
          "fill-opacity": [
            "interpolate",
            ["linear"],
            ["zoom"],
            9,
            [
              "case",
              [">", ["coalesce", ["feature-state", "visitedPlaces"], 0], 0],
              ["+", 0.18, ["*", ["coalesce", ["feature-state", "completionRatio"], 0], 0.3]],
              [">", ["coalesce", ["feature-state", "totalPlaces"], 0], 0],
              0.14,
              0,
            ],
            12,
            0,
          ],
        },
      },
      firstSymbolId,
    );
    map.addLayer(
      {
        id: "atlas-admin1-outline",
        type: "line",
        source: ADMIN1_SOURCE_ID,
        "source-layer": SOURCE_LAYER_REGIONS,
        layout: { visibility },
        paint: {
          "line-color": [
            "case",
            ["boolean", ["feature-state", "selected"], false],
            "#1d5b4c",
            "rgba(22, 24, 29, 0.25)",
          ],
          "line-width": ["case", ["boolean", ["feature-state", "selected"], false], 2, 0.9],
        },
      },
      firstSymbolId,
    );
  }

  if (!map.getSource(PLACES_SOURCE_ID)) {
    map.addSource(PLACES_SOURCE_ID, {
      type: "geojson",
      data: buildPlacesGeoJson(initialPlaces),
      cluster: true,
      clusterMaxZoom: 12,
      clusterRadius: 44,
    });
    map.addLayer({
      id: "atlas-places-clusters",
      type: "circle",
      source: PLACES_SOURCE_ID,
      filter: ["has", "point_count"],
      paint: {
        "circle-color": "#16181d",
        "circle-radius": ["step", ["get", "point_count"], 15, 25, 19, 100, 23],
        "circle-stroke-color": "#faf9f6",
        "circle-stroke-width": 2,
      },
    });
    if (hasGlyphs) {
      map.addLayer({
        id: "atlas-places-cluster-count",
        type: "symbol",
        source: PLACES_SOURCE_ID,
        filter: ["has", "point_count"],
        layout: {
          "text-field": "{point_count_abbreviated}",
          "text-font": ["Noto Sans Bold"],
          "text-size": 11,
        },
        paint: {
          "text-color": "#faf9f6",
        },
      });
    }
    map.addLayer({
      id: "atlas-places-selected",
      type: "circle",
      source: PLACES_SOURCE_ID,
      filter: ["all", ["!", ["has", "point_count"]], ["==", ["get", "id"], ""]],
      paint: {
        "circle-radius": 13,
        "circle-color": "rgba(29, 91, 76, 0.18)",
        "circle-stroke-color": "#1d5b4c",
        "circle-stroke-width": 2,
      },
    });
    map.addLayer({
      id: "atlas-places-area-selected",
      type: "circle",
      source: PLACES_SOURCE_ID,
      filter: [
        "all",
        ["!", ["has", "point_count"]],
        ["in", ["get", "id"], ["literal", [...initialHighlightedPlaceIds]]],
      ],
      paint: {
        "circle-radius": 12,
        "circle-color": "rgba(29, 91, 76, 0.22)",
        "circle-stroke-color": "#1d5b4c",
        "circle-stroke-width": 2,
      },
    });
    map.addLayer({
      id: "atlas-places-pins",
      type: "circle",
      source: PLACES_SOURCE_ID,
      filter: ["!", ["has", "point_count"]],
      paint: {
        "circle-color": [
          "match",
          ["get", "visitStatus"],
          "VISITED",
          "#1f6f54",
          "NEEDS_TRIAGE",
          "#6d597a",
          "#c96a38",
        ],
        "circle-radius": [
          "match",
          ["get", "derivedRole"],
          ["PREFERRED_HOTEL", "MUST_SEE"],
          7.5,
          "FAVORITE",
          6.5,
          5.5,
        ],
        "circle-stroke-color": [
          "match",
          ["get", "derivedRole"],
          ["PREFERRED_HOTEL", "MUST_SEE"],
          "#d4a017",
          "FAVORITE",
          "#b84a62",
          "#faf9f6",
        ],
        "circle-stroke-width": [
          "match",
          ["get", "derivedRole"],
          ["PREFERRED_HOTEL", "MUST_SEE"],
          2.5,
          "FAVORITE",
          2,
          1.5,
        ],
      },
    });
    map.addLayer({
      id: "atlas-places-icons",
      type: "symbol",
      source: PLACES_SOURCE_ID,
      filter: [
        "all",
        ["!", ["has", "point_count"]],
        ["in", ["get", "category"], ["literal", [...PIN_ICON_CATEGORY_LIST]]],
      ],
      layout: {
        "icon-image": [
          "match",
          ["get", "category"],
          "RESTAURANT",
          PIN_ICON_IDS.RESTAURANT,
          "CAFE_BAR",
          PIN_ICON_IDS.CAFE_BAR,
          "HOTEL",
          PIN_ICON_IDS.HOTEL,
          "MUSEUM",
          PIN_ICON_IDS.MUSEUM,
          "LANDMARK",
          PIN_ICON_IDS.LANDMARK,
          "SACRAL",
          PIN_ICON_IDS.SACRAL,
          "MOUNTAIN",
          PIN_ICON_IDS.MOUNTAIN,
          "WATER",
          PIN_ICON_IDS.WATER,
          "NATURE_PARK",
          PIN_ICON_IDS.NATURE_PARK,
          "VIEWPOINT",
          PIN_ICON_IDS.VIEWPOINT,
          PIN_ICON_IDS.LANDMARK,
        ],
        "icon-size": [
          "match",
          ["get", "derivedRole"],
          ["PREFERRED_HOTEL", "MUST_SEE"],
          0.85,
          "FAVORITE",
          0.75,
          0.65,
        ],
        "icon-allow-overlap": true,
        "icon-ignore-placement": true,
      },
    });
    if (hasGlyphs) {
      map.addLayer({
        id: "atlas-places-labels",
        type: "symbol",
        source: PLACES_SOURCE_ID,
        minzoom: 12,
        filter: ["!", ["has", "point_count"]],
        layout: {
          "text-field": ["get", "canonicalName"],
          "text-font": ["Noto Sans Regular"],
          "text-size": 11.5,
          "text-offset": [0, 1.35],
          "text-anchor": "top",
          "text-allow-overlap": false,
        },
        paint: {
          "text-color": "#16181d",
          "text-halo-color": "#faf9f6",
          "text-halo-width": 1.5,
        },
      });
    }
  }
}

export function AtlasMapView({
  projection,
  basemap,
  places,
  countrySummaries,
  admin1Summaries,
  selectedCountryCode,
  selectedRegionLevel,
  selectedRegionCode,
  selectedPlaceId,
  highlightedPlaceIds = [],
  fitBoundsTarget,
  focusPlaceTarget,
  mapPadding,
  areaSelectMode,
  onSelectPlace,
  onSelectRegion,
  onViewportChange,
  onSelectAreaPlaces,
}: AtlasMapViewProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<MapLibreMap | null>(null);
  const styleLoadedRef = useRef(false);
  const [styleRevision, setStyleRevision] = useState(0);
  const [dragBox, setDragBox] = useState<DragBoxState | null>(null);

  const projectionRef = useRef(projection);
  const basemapRef = useRef(basemap);
  const appliedBasemapRef = useRef<AtlasBasemap | null>(null);
  const placesRef = useRef(places);
  const selectedCountryCodeRef = useRef(selectedCountryCode);
  const highlightedPlaceIdsRef = useRef(highlightedPlaceIds);
  const mapPaddingRef = useRef(mapPadding);
  const areaSelectModeRef = useRef(areaSelectMode);
  const suppressNextMapClickRef = useRef(false);
  const activeDragCleanupRef = useRef<(() => void) | null>(null);
  const onSelectPlaceRef = useRef(onSelectPlace);
  const onSelectRegionRef = useRef(onSelectRegion);
  const onViewportChangeRef = useRef(onViewportChange);
  const onSelectAreaPlacesRef = useRef(onSelectAreaPlaces);
  const lastFitKeyRef = useRef<string | null>(null);
  const lastFocusKeyRef = useRef<string | null>(null);

  projectionRef.current = projection;
  basemapRef.current = basemap;
  placesRef.current = places;
  selectedCountryCodeRef.current = selectedCountryCode;
  highlightedPlaceIdsRef.current = highlightedPlaceIds;
  mapPaddingRef.current = mapPadding;
  areaSelectModeRef.current = areaSelectMode;
  onSelectPlaceRef.current = onSelectPlace;
  onSelectRegionRef.current = onSelectRegion;
  onViewportChangeRef.current = onViewportChange;
  onSelectAreaPlacesRef.current = onSelectAreaPlaces;

  useEffect(() => {
    const container = containerRef.current;
    if (!container) {
      return;
    }

    styleLoadedRef.current = false;
    delete container.dataset.mapLoaded;
    delete container.dataset.styleLoaded;
    delete container.dataset.projection;

    const initialBasemap = basemapRef.current;
    appliedBasemapRef.current = initialBasemap;

    const map = new MapLibreMap({
      container,
      style: resolveStyle(initialBasemap),
      center: [12, 34],
      zoom: 1.7,
      boxZoom: false,
      attributionControl: { compact: true },
    });

    map.on("styleimagemissing", (event) => {
      registerCategoryPinIcon(map, event.id);
    });

    map.on("style.load", () => {
      styleLoadedRef.current = true;
      map.setProjection({ type: projectionRef.current });
      registerCategoryPinIcons(map);
      syncSourcesAndLayers(
        map,
        selectedCountryCodeRef.current,
        placesRef.current,
        highlightedPlaceIdsRef.current,
      );
      container.dataset.styleLoaded = "true";
      container.dataset.projection = projectionRef.current;
      emitViewport(map, onViewportChangeRef.current);
      setStyleRevision((rev) => rev + 1);
    });

    map.once("load", () => {
      container.dataset.mapLoaded = "true";
    });

    map.on("moveend", () => {
      emitViewport(map, onViewportChangeRef.current);
    });

    map.on("click", (event) => {
      if (suppressNextMapClickRef.current) {
        suppressNextMapClickRef.current = false;
        return;
      }

      const candidateLayers = interactiveLayerIds(map, selectedCountryCodeRef.current);
      if (candidateLayers.length === 0) {
        return;
      }

      const features = map.queryRenderedFeatures(event.point, { layers: candidateLayers });
      const top = features[0];
      if (!top) {
        return;
      }

      const layerId = top.layer.id;
      const props = top.properties ?? {};
      if (
        (layerId === "atlas-places-pins" || layerId === "atlas-places-icons") &&
        typeof props.id === "string"
      ) {
        onSelectPlaceRef.current(props.id);
        return;
      }
      if (
        layerId === "atlas-places-clusters" &&
        typeof props.cluster_id === "number" &&
        top.geometry.type === "Point"
      ) {
        const source = map.getSource(PLACES_SOURCE_ID) as GeoJSONSource | undefined;
        const coords = top.geometry.coordinates;
        if (source && coords.length >= 2) {
          const lng = Number(coords[0]);
          const lat = Number(coords[1]);
          void source.getClusterExpansionZoom(props.cluster_id).then((zoom) => {
            map.easeTo({
              center: [lng, lat],
              zoom,
              duration: prefersReducedMotion() ? 0 : 500,
            });
          });
        }
        return;
      }
      if (layerId === "atlas-admin1-fill" && typeof props.code === "string") {
        onSelectRegionRef.current("ADMIN1", props.code);
        return;
      }
      if (layerId === "atlas-countries-fill" && typeof props.code === "string") {
        onSelectRegionRef.current("COUNTRY", props.code);
      }
    });

    map.on("mousemove", (event) => {
      if (areaSelectModeRef.current) {
        map.getCanvas().style.cursor = "crosshair";
        return;
      }
      const hoverLayers = interactiveLayerIds(map, selectedCountryCodeRef.current);
      if (hoverLayers.length === 0) {
        return;
      }
      const hits = map.queryRenderedFeatures(event.point, { layers: hoverLayers });
      map.getCanvas().style.cursor = hits.length > 0 ? "pointer" : "";
    });

    mapRef.current = map;

    return () => {
      styleLoadedRef.current = false;
      appliedBasemapRef.current = null;
      map.remove();
      mapRef.current = null;
      delete container.dataset.mapLoaded;
      delete container.dataset.styleLoaded;
      delete container.dataset.projection;
    };
  }, []);

  useEffect(() => {
    const map = mapRef.current;
    const container = containerRef.current;
    if (!map || isOfflineBasemap() || appliedBasemapRef.current === basemap) {
      return;
    }
    appliedBasemapRef.current = basemap;
    styleLoadedRef.current = false;
    if (container) {
      delete container.dataset.styleLoaded;
    }
    map.setStyle(resolveStyle(basemap), { diff: false });
  }, [basemap]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map) {
      return;
    }
    if (areaSelectMode) {
      map.dragPan.disable();
      map.getCanvas().style.cursor = "crosshair";
    } else {
      map.dragPan.enable();
      map.getCanvas().style.cursor = "";
    }
  }, [areaSelectMode]);

  useEffect(() => {
    const container = containerRef.current;
    if (!container) {
      return;
    }

    const handleMouseDown = (event: MouseEvent) => {
      const map = mapRef.current;
      if (
        event.button !== 0 ||
        (!areaSelectModeRef.current && !event.shiftKey) ||
        !map ||
        !styleLoadedRef.current
      ) {
        return;
      }

      activeDragCleanupRef.current?.();

      event.preventDefault();
      event.stopPropagation();
      map.dragPan.disable();

      const rect = container.getBoundingClientRect();
      const clampX = (clientX: number) => Math.max(0, Math.min(rect.width, clientX - rect.left));
      const clampY = (clientY: number) => Math.max(0, Math.min(rect.height, clientY - rect.top));

      const startX = clampX(event.clientX);
      const startY = clampY(event.clientY);
      let latestX = startX;
      let latestY = startY;

      setDragBox({ startX, startY, currentX: startX, currentY: startY });

      const handleMouseMove = (moveEvent: MouseEvent) => {
        latestX = clampX(moveEvent.clientX);
        latestY = clampY(moveEvent.clientY);
        setDragBox({ startX, startY, currentX: latestX, currentY: latestY });
      };

      const removeDragListeners = () => {
        window.removeEventListener("mousemove", handleMouseMove);
        window.removeEventListener("mouseup", handleMouseUp);
        activeDragCleanupRef.current = null;
      };

      const abortActiveDrag = () => {
        removeDragListeners();
        setDragBox(null);
        if (!areaSelectModeRef.current) {
          mapRef.current?.dragPan.enable();
        }
      };

      const handleMouseUp = () => {
        removeDragListeners();
        setDragBox(null);

        if (!areaSelectModeRef.current) {
          map.dragPan.enable();
        }

        if (
          Math.abs(latestX - startX) >= MIN_DRAG_BOX_PX &&
          Math.abs(latestY - startY) >= MIN_DRAG_BOX_PX
        ) {
          suppressNextMapClickRef.current = true;
          window.setTimeout(() => {
            suppressNextMapClickRef.current = false;
          }, 0);

          const centerLng = map.getCenter().lng;
          const matched = filterPlacesInScreenRect(
            placesRef.current,
            {
              minX: Math.min(startX, latestX),
              minY: Math.min(startY, latestY),
              maxX: Math.max(startX, latestX),
              maxY: Math.max(startY, latestY),
            },
            ([lng, lat]) => map.project([normalizeLngNear(lng, centerLng), lat]),
            projectionRef.current === "globe"
              ? (place, point) => isSameGlobePoint(place, map.unproject([point.x, point.y]))
              : undefined,
          );
          if (matched.length > 0) {
            onSelectAreaPlacesRef.current(matched);
          }
        }
      };

      activeDragCleanupRef.current = abortActiveDrag;
      window.addEventListener("mousemove", handleMouseMove);
      window.addEventListener("mouseup", handleMouseUp);
    };

    container.addEventListener("mousedown", handleMouseDown, true);
    return () => {
      container.removeEventListener("mousedown", handleMouseDown, true);
      activeDragCleanupRef.current?.();
    };
  }, []);

  useEffect(() => {
    const map = mapRef.current;
    const container = containerRef.current;
    if (!map || !styleLoadedRef.current) {
      return;
    }
    map.setProjection({ type: projection });
    if (container) {
      container.dataset.projection = projection;
    }
  }, [projection]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map || !styleLoadedRef.current || styleRevision === 0) {
      return;
    }
    const source = map.getSource(PLACES_SOURCE_ID) as GeoJSONSource | undefined;
    source?.setData(buildPlacesGeoJson(places));
  }, [places, styleRevision]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map || !styleLoadedRef.current || styleRevision === 0) {
      return;
    }
    if (map.getLayer("atlas-places-selected")) {
      map.setFilter("atlas-places-selected", [
        "all",
        ["!", ["has", "point_count"]],
        ["==", ["get", "id"], selectedPlaceId ?? ""],
      ]);
    }
  }, [selectedPlaceId, styleRevision]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map || !styleLoadedRef.current || styleRevision === 0) {
      return;
    }
    if (map.getLayer("atlas-places-area-selected")) {
      map.setFilter("atlas-places-area-selected", [
        "all",
        ["!", ["has", "point_count"]],
        ["in", ["get", "id"], ["literal", [...highlightedPlaceIds]]],
      ]);
    }
  }, [highlightedPlaceIds, styleRevision]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map || !styleLoadedRef.current || styleRevision === 0) {
      return;
    }
    const admin1Source = map.getSource(ADMIN1_SOURCE_ID) as VectorTileSource | undefined;
    if (admin1Source) {
      if (selectedCountryCode.length > 0) {
        admin1Source.setTiles([admin1TileUrl(selectedCountryCode)]);
        if (map.getLayer("atlas-admin1-fill")) {
          map.setLayoutProperty("atlas-admin1-fill", "visibility", "visible");
        }
        if (map.getLayer("atlas-admin1-outline")) {
          map.setLayoutProperty("atlas-admin1-outline", "visibility", "visible");
        }
      } else {
        if (map.getLayer("atlas-admin1-fill")) {
          map.setLayoutProperty("atlas-admin1-fill", "visibility", "none");
        }
        if (map.getLayer("atlas-admin1-outline")) {
          map.setLayoutProperty("atlas-admin1-outline", "visibility", "none");
        }
      }
    }
  }, [selectedCountryCode, styleRevision]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map || !styleLoadedRef.current || styleRevision === 0) {
      return;
    }
    syncRegionFeatureStates(
      map,
      COUNTRIES_SOURCE_ID,
      countrySummaries,
      (code) => code === selectedCountryCode,
      selectedCountryCode,
    );
    syncRegionFeatureStates(
      map,
      ADMIN1_SOURCE_ID,
      admin1Summaries,
      (code) => selectedRegionLevel === "ADMIN1" && code === selectedRegionCode,
    );
  }, [
    countrySummaries,
    admin1Summaries,
    selectedCountryCode,
    selectedRegionLevel,
    selectedRegionCode,
    styleRevision,
  ]);

  useEffect(() => {
    if (!focusPlaceTarget) {
      lastFocusKeyRef.current = null;
      return;
    }
    const map = mapRef.current;
    if (!map || !styleLoadedRef.current || styleRevision === 0) {
      return;
    }
    if (lastFocusKeyRef.current === focusPlaceTarget.key) {
      return;
    }
    lastFocusKeyRef.current = focusPlaceTarget.key;

    const padding = clampMapPadding(
      mapPaddingRef.current,
      containerRef.current?.clientWidth ?? 0,
      containerRef.current?.clientHeight ?? 0,
    );
    map.easeTo({
      center: [focusPlaceTarget.lng, focusPlaceTarget.lat],
      zoom: Math.max(map.getZoom(), MIN_FOCUS_ZOOM),
      padding,
      duration: prefersReducedMotion() ? 0 : 650,
    });
  }, [focusPlaceTarget, styleRevision]);

  useEffect(() => {
    if (!fitBoundsTarget) {
      lastFitKeyRef.current = null;
      return;
    }
    const map = mapRef.current;
    if (!map || !styleLoadedRef.current || styleRevision === 0) {
      return;
    }
    if (lastFitKeyRef.current === fitBoundsTarget.key) {
      return;
    }
    lastFitKeyRef.current = fitBoundsTarget.key;

    const lngSpan = fitBoundsTarget.maxLng - fitBoundsTarget.minLng;
    const latSpan = fitBoundsTarget.maxLat - fitBoundsTarget.minLat;
    if (lngSpan > 0 && latSpan > 0 && lngSpan <= 170 && latSpan <= 120) {
      const padding = clampMapPadding(
        {
          top: Math.max(mapPaddingRef.current.top, MIN_FIT_BOUNDS_PADDING),
          right: Math.max(mapPaddingRef.current.right, MIN_FIT_BOUNDS_PADDING),
          bottom: Math.max(mapPaddingRef.current.bottom, MIN_FIT_BOUNDS_PADDING),
          left: Math.max(mapPaddingRef.current.left, MIN_FIT_BOUNDS_PADDING),
        },
        containerRef.current?.clientWidth ?? 0,
        containerRef.current?.clientHeight ?? 0,
      );
      map.fitBounds(
        [
          [fitBoundsTarget.minLng, fitBoundsTarget.minLat],
          [fitBoundsTarget.maxLng, fitBoundsTarget.maxLat],
        ],
        { padding, maxZoom: 9, duration: prefersReducedMotion() ? 0 : 750 },
      );
    }
  }, [fitBoundsTarget, styleRevision]);

  const selectionRect = dragBox
    ? {
        left: Math.min(dragBox.startX, dragBox.currentX),
        top: Math.min(dragBox.startY, dragBox.currentY),
        width: Math.abs(dragBox.currentX - dragBox.startX),
        height: Math.abs(dragBox.currentY - dragBox.startY),
      }
    : null;

  return (
    <section className="relative h-full w-full" aria-label="MeanderAtlas world globe">
      <div ref={containerRef} data-testid="atlas-map-canvas" className="h-full w-full" />
      {selectionRect && selectionRect.width > 0 && selectionRect.height > 0 && (
        <div
          data-testid="area-selection-box"
          className="pointer-events-none absolute z-10 rounded border-2 border-dashed"
          style={{
            left: `${selectionRect.left}px`,
            top: `${selectionRect.top}px`,
            width: `${selectionRect.width}px`,
            height: `${selectionRect.height}px`,
            borderColor: "#1d5b4c",
            backgroundColor: "rgba(29, 91, 76, 0.14)",
          }}
        />
      )}
    </section>
  );
}
