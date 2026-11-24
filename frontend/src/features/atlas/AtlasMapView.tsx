import { Map as MapLibreMap, type StyleSpecification, setWorkerUrl } from "maplibre-gl";
import maplibreWorkerUrl from "maplibre-gl/dist/maplibre-gl-worker.mjs?url";
import { useEffect, useRef } from "react";
import type { AtlasProjection } from "./viewState";

setWorkerUrl(maplibreWorkerUrl);

interface AtlasMapViewProps {
  projection: AtlasProjection;
}

const POSITRON_STYLE_URL = "https://tiles.openfreemap.org/styles/positron";

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

export function AtlasMapView({ projection }: AtlasMapViewProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<MapLibreMap | null>(null);
  const styleLoadedRef = useRef(false);
  const projectionRef = useRef<AtlasProjection>(projection);

  projectionRef.current = projection;

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
      container.dataset.styleLoaded = "true";
      container.dataset.projection = projectionRef.current;
    });

    map.once("load", () => {
      container.dataset.mapLoaded = "true";
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

  return (
    <section className="h-full w-full" aria-label="MeanderAtlas world globe">
      <div ref={containerRef} data-testid="atlas-map-canvas" className="h-full w-full" />
    </section>
  );
}
