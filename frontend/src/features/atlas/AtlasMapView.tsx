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
import type { AtlasProjection, RegionScope } from "./viewState";

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
  places: AtlasPlaceView[];
  countrySummaries: RegionSummaryView[];
  admin1Summaries: RegionSummaryView[];
  selectedCountryCode: string;
  selectedRegionLevel: RegionScope | "";
  selectedRegionCode: string;
  selectedPlaceId: string | null;
  fitBoundsTarget: FitBoundsTarget | null;
  onSelectPlace: (placeId: string) => void;
  onSelectRegion: (level: RegionLevel, code: string) => void;
  onViewportChange: (viewport: MapViewport) => void;
}

const POSITRON_STYLE_URL = "https://tiles.openfreemap.org/styles/positron";
const COUNTRIES_SOURCE_ID = "atlas-countries";
const ADMIN1_SOURCE_ID = "atlas-admin1";
const PLACES_SOURCE_ID = "atlas-places";
const SOURCE_LAYER_REGIONS = "regions";

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

function resolveStyle(): string | StyleSpecification {
  const params = new URLSearchParams(window.location.search);
  if (params.get("basemap") === "offline") {
    return createFallbackStyle();
  }
  return POSITRON_STYLE_URL;
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
      },
    })),
  };
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

function syncSourcesAndLayers(
  map: MapLibreMap,
  initialCountryCode: string,
  initialPlaces: AtlasPlaceView[],
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
            "case",
            [">", ["coalesce", ["feature-state", "visitedPlaces"], 0], 0],
            ["+", 0.16, ["*", ["coalesce", ["feature-state", "completionRatio"], 0], 0.28]],
            [">", ["coalesce", ["feature-state", "totalPlaces"], 0], 0],
            0.12,
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
            "case",
            [">", ["coalesce", ["feature-state", "visitedPlaces"], 0], 0],
            ["+", 0.18, ["*", ["coalesce", ["feature-state", "completionRatio"], 0], 0.3]],
            [">", ["coalesce", ["feature-state", "totalPlaces"], 0], 0],
            0.14,
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
  }
}

export function AtlasMapView({
  projection,
  places,
  countrySummaries,
  admin1Summaries,
  selectedCountryCode,
  selectedRegionLevel,
  selectedRegionCode,
  selectedPlaceId,
  fitBoundsTarget,
  onSelectPlace,
  onSelectRegion,
  onViewportChange,
}: AtlasMapViewProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<MapLibreMap | null>(null);
  const styleLoadedRef = useRef(false);
  const [styleRevision, setStyleRevision] = useState(0);

  const projectionRef = useRef(projection);
  const placesRef = useRef(places);
  const selectedCountryCodeRef = useRef(selectedCountryCode);
  const onSelectPlaceRef = useRef(onSelectPlace);
  const onSelectRegionRef = useRef(onSelectRegion);
  const onViewportChangeRef = useRef(onViewportChange);
  const lastFitKeyRef = useRef<string | null>(null);

  projectionRef.current = projection;
  placesRef.current = places;
  selectedCountryCodeRef.current = selectedCountryCode;
  onSelectPlaceRef.current = onSelectPlace;
  onSelectRegionRef.current = onSelectRegion;
  onViewportChangeRef.current = onViewportChange;

  useEffect(() => {
    const container = containerRef.current;
    if (!container) {
      return;
    }

    styleLoadedRef.current = false;
    delete container.dataset.mapLoaded;
    delete container.dataset.styleLoaded;
    delete container.dataset.projection;

    const map = new MapLibreMap({
      container,
      style: resolveStyle(),
      center: [12, 34],
      zoom: 1.7,
      attributionControl: { compact: true },
    });

    map.on("style.load", () => {
      styleLoadedRef.current = true;
      map.setProjection({ type: projectionRef.current });
      syncSourcesAndLayers(map, selectedCountryCodeRef.current, placesRef.current);
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
      const candidateLayers = [
        "atlas-places-pins",
        "atlas-places-clusters",
        ...(selectedCountryCodeRef.current.length > 0 ? ["atlas-admin1-fill"] : []),
        "atlas-countries-fill",
      ].filter((layerId) => Boolean(map.getLayer(layerId)));
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
      if (layerId === "atlas-places-pins" && typeof props.id === "string") {
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
            map.easeTo({ center: [lng, lat], zoom, duration: 500 });
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
      const hoverLayers = [
        "atlas-places-pins",
        "atlas-places-clusters",
        ...(selectedCountryCodeRef.current.length > 0 ? ["atlas-admin1-fill"] : []),
        "atlas-countries-fill",
      ].filter((layerId) => Boolean(map.getLayer(layerId)));
      if (hoverLayers.length === 0) {
        return;
      }
      const hits = map.queryRenderedFeatures(event.point, { layers: hoverLayers });
      map.getCanvas().style.cursor = hits.length > 0 ? "pointer" : "";
    });

    mapRef.current = map;

    return () => {
      styleLoadedRef.current = false;
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
    if (map.getSource(COUNTRIES_SOURCE_ID)) {
      map.removeFeatureState({ source: COUNTRIES_SOURCE_ID, sourceLayer: SOURCE_LAYER_REGIONS });
      for (const summary of countrySummaries) {
        map.setFeatureState(
          {
            source: COUNTRIES_SOURCE_ID,
            sourceLayer: SOURCE_LAYER_REGIONS,
            id: summary.code,
          },
          {
            totalPlaces: summary.totalPlaces,
            visitedPlaces: summary.visitedPlaces,
            completionRatio: summary.completionRatio,
            selected: summary.code === selectedCountryCode,
          },
        );
      }
      if (
        selectedCountryCode.length > 0 &&
        !countrySummaries.some((s) => s.code === selectedCountryCode)
      ) {
        map.setFeatureState(
          {
            source: COUNTRIES_SOURCE_ID,
            sourceLayer: SOURCE_LAYER_REGIONS,
            id: selectedCountryCode,
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

    if (map.getSource(ADMIN1_SOURCE_ID)) {
      map.removeFeatureState({ source: ADMIN1_SOURCE_ID, sourceLayer: SOURCE_LAYER_REGIONS });
      for (const summary of admin1Summaries) {
        map.setFeatureState(
          {
            source: ADMIN1_SOURCE_ID,
            sourceLayer: SOURCE_LAYER_REGIONS,
            id: summary.code,
          },
          {
            totalPlaces: summary.totalPlaces,
            visitedPlaces: summary.visitedPlaces,
            completionRatio: summary.completionRatio,
            selected: selectedRegionLevel === "ADMIN1" && summary.code === selectedRegionCode,
          },
        );
      }
    }
  }, [
    countrySummaries,
    admin1Summaries,
    selectedCountryCode,
    selectedRegionLevel,
    selectedRegionCode,
    styleRevision,
  ]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map || !styleLoadedRef.current || !fitBoundsTarget) {
      return;
    }
    if (lastFitKeyRef.current === fitBoundsTarget.key) {
      return;
    }
    lastFitKeyRef.current = fitBoundsTarget.key;

    const lngSpan = fitBoundsTarget.maxLng - fitBoundsTarget.minLng;
    const latSpan = fitBoundsTarget.maxLat - fitBoundsTarget.minLat;
    if (lngSpan > 0 && latSpan > 0 && lngSpan <= 170 && latSpan <= 120) {
      map.fitBounds(
        [
          [fitBoundsTarget.minLng, fitBoundsTarget.minLat],
          [fitBoundsTarget.maxLng, fitBoundsTarget.maxLat],
        ],
        { padding: 72, maxZoom: 9, duration: 750 },
      );
    }
  }, [fitBoundsTarget]);

  return (
    <section className="h-full w-full" aria-label="MeanderAtlas world globe">
      <div ref={containerRef} data-testid="atlas-map-canvas" className="h-full w-full" />
    </section>
  );
}
