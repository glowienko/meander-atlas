import { Map as MapLibreMap, type StyleSpecification } from "maplibre-gl";
import { useEffect, useRef } from "react";
import type { AtlasProjection, AtlasTheme } from "./viewState";

interface AtlasMapViewProps {
  projection: AtlasProjection;
  theme: AtlasTheme;
}

const STYLE_URLS: Record<AtlasTheme, string> = {
  light: "https://tiles.openfreemap.org/styles/positron",
  dark: "https://tiles.openfreemap.org/styles/dark",
};

function createFallbackStyle(theme: AtlasTheme): StyleSpecification {
  return {
    version: 8,
    sources: {},
    layers: [
      {
        id: "background",
        type: "background",
        paint: {
          "background-color": theme === "dark" ? "#0f1115" : "#faf9f6",
        },
      },
    ],
  };
}

function resolveStyle(theme: AtlasTheme): string | StyleSpecification {
  const params = new URLSearchParams(window.location.search);
  if (params.get("basemap") === "offline") {
    return createFallbackStyle(theme);
  }
  return STYLE_URLS[theme];
}

export function AtlasMapView({ projection, theme }: AtlasMapViewProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<MapLibreMap | null>(null);
  const projectionRef = useRef<AtlasProjection>(projection);
  const initialThemeRef = useRef<AtlasTheme>(theme);
  const appliedThemeRef = useRef<AtlasTheme>(theme);

  projectionRef.current = projection;

  useEffect(() => {
    const container = containerRef.current;
    if (!container) {
      return;
    }

    const map = new MapLibreMap({
      container,
      style: resolveStyle(initialThemeRef.current),
      center: [12, 34],
      zoom: 1.7,
      attributionControl: { compact: true },
    });

    map.on("style.load", () => {
      map.setProjection({ type: projectionRef.current });
    });

    mapRef.current = map;

    return () => {
      map.remove();
      mapRef.current = null;
    };
  }, []);

  useEffect(() => {
    const map = mapRef.current;
    if (!map) {
      return;
    }
    if (map.isStyleLoaded()) {
      map.setProjection({ type: projection });
    }
  }, [projection]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map || appliedThemeRef.current === theme) {
      return;
    }
    appliedThemeRef.current = theme;
    map.setStyle(resolveStyle(theme));
  }, [theme]);

  return (
    <section className="h-full w-full" aria-label="MeanderAtlas world globe">
      <div ref={containerRef} data-testid="atlas-map-canvas" className="h-full w-full" />
    </section>
  );
}
