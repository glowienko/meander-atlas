import { Map as MapLibreMap, type Point, type StyleSpecification, setWorkerUrl } from "maplibre-gl";
import maplibreWorkerUrl from "maplibre-gl/dist/maplibre-gl-worker.mjs?url";
import {
  type Ref,
  type RefObject,
  useEffect,
  useEffectEvent,
  useImperativeHandle,
  useRef,
  useState,
} from "react";
import type { AtlasPlaceView, RegionLevel } from "../../api/client";
import { PAPER } from "./atlasPalette";
import {
  computeMapPadding,
  filterPlacesInScreenRect,
  type GeoBounds,
  isFittableBounds,
  isSameGlobePoint,
  type MapOverlays,
  normalizeLngNear,
  type ScreenRect,
} from "./mapGeometry";
import {
  addAtlasLayers,
  clusterExpansionZoom,
  hitAt,
  type MapHit,
  type RegionSelection,
  STREET_ZOOM,
  setPlaceHighlights,
  setPlaces,
  showAdmin1Country,
  syncRegionStates,
} from "./mapLayers";
import { registerCategoryPinIcon, registerCategoryPinIcons } from "./pinIcons";
import type { AtlasProjection, MapBasemap } from "./viewState";

setWorkerUrl(maplibreWorkerUrl);

export interface MapViewport extends GeoBounds {
  zoom: number;
}

export interface AtlasMapHandle {
  flyTo: (point: { lng: number; lat: number }, overlays: MapOverlays) => void;
  fitBounds: (bounds: GeoBounds, overlays: MapOverlays) => void;
  resetView: () => void;
}

interface AtlasMapViewProps extends RegionSelection {
  ref: Ref<AtlasMapHandle>;
  projection: AtlasProjection;
  basemap: MapBasemap;
  places: readonly AtlasPlaceView[];
  selectedPlaceId: string | null;
  highlightedPlaceIds: readonly string[];
  areaSelectMode: boolean;
  onSelectPlace: (placeId: string) => void;
  onSelectRegion: (level: RegionLevel, code: string) => void;
  onViewportChange: (viewport: MapViewport) => void;
  onSelectAreaPlaces: (places: AtlasPlaceView[]) => void;
}

interface DragBox {
  startX: number;
  startY: number;
  currentX: number;
  currentY: number;
}

const OFFLINE_STYLE: StyleSpecification = {
  version: 8,
  sources: {},
  layers: [{ id: "background", type: "background", paint: { "background-color": PAPER } }],
};
const STYLE_BY_BASEMAP: Readonly<Record<MapBasemap, string | StyleSpecification>> = {
  atlas: "https://tiles.openfreemap.org/styles/positron",
  street: "https://tiles.openfreemap.org/styles/liberty",
  offline: OFFLINE_STYLE,
};
const DEFAULT_CENTER: [number, number] = [12, 34];
const DEFAULT_ZOOM = 1.4;
const MIN_DRAG_BOX_PX = 6;
const MIN_FOCUS_ZOOM = 14;
const MIN_FIT_BOUNDS_PADDING = 48;
const EASE_MS = { cluster: 500, focus: 650, reset: 700, fit: 750 } as const;

function easeDuration(ms: number): number {
  return window.matchMedia?.("(prefers-reduced-motion: reduce)").matches ? 0 : ms;
}

function viewportOf(map: MapLibreMap): MapViewport {
  const bounds = map.getBounds();
  return {
    zoom: map.getZoom(),
    minLng: bounds.getWest(),
    minLat: bounds.getSouth(),
    maxLng: bounds.getEast(),
    maxLat: bounds.getNorth(),
  };
}

function overlayPadding(map: MapLibreMap, overlays: MapOverlays, minimum = 0) {
  const container = map.getContainer();
  return computeMapPadding(
    { ...overlays, viewportWidth: container.clientWidth, viewportHeight: container.clientHeight },
    minimum,
  );
}

function boxRect(box: DragBox): ScreenRect {
  return {
    minX: Math.min(box.startX, box.currentX),
    minY: Math.min(box.startY, box.currentY),
    maxX: Math.max(box.startX, box.currentX),
    maxY: Math.max(box.startY, box.currentY),
  };
}

function styledMap(
  mapRef: RefObject<MapLibreMap | null>,
  styleLoadedRef: RefObject<boolean>,
  styleRevision: number,
): MapLibreMap | null {
  return styleRevision > 0 && styleLoadedRef.current ? mapRef.current : null;
}

export function AtlasMapView({
  ref,
  projection,
  basemap,
  places,
  countrySummaries,
  admin1Summaries,
  selectedCountryCode,
  selectedRegionLevel,
  selectedRegionCode,
  selectedPlaceId,
  highlightedPlaceIds,
  areaSelectMode,
  onSelectPlace,
  onSelectRegion,
  onViewportChange,
  onSelectAreaPlaces,
}: AtlasMapViewProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<MapLibreMap | null>(null);
  const styleLoadedRef = useRef(false);
  const suppressNextClickRef = useRef(false);
  const [styleRevision, setStyleRevision] = useState(0);
  const [dragBox, setDragBox] = useState<DragBox | null>(null);

  const emitViewport = useEffectEvent((map: MapLibreMap) => onViewportChange(viewportOf(map)));

  const zoomToCluster = (map: MapLibreMap, hit: Extract<MapHit, { kind: "cluster" }>) => {
    void clusterExpansionZoom(map, hit.clusterId)?.then((zoom) => {
      if (mapRef.current === map) {
        map.easeTo({ center: hit.center, zoom, duration: easeDuration(EASE_MS.cluster) });
      }
    });
  };

  const handleMapClick = useEffectEvent((map: MapLibreMap, point: Point) => {
    if (suppressNextClickRef.current) {
      suppressNextClickRef.current = false;
      return;
    }
    const hit = hitAt(map, point, selectedCountryCode);
    if (hit?.kind === "place") {
      onSelectPlace(hit.placeId);
    } else if (hit?.kind === "region") {
      onSelectRegion(hit.level, hit.code);
    } else if (hit?.kind === "cluster") {
      zoomToCluster(map, hit);
    }
  });

  const updateHoverCursor = useEffectEvent((map: MapLibreMap, point: Point) => {
    const hovered = !areaSelectMode && hitAt(map, point, selectedCountryCode) !== null;
    map.getCanvas().style.cursor = areaSelectMode ? "crosshair" : hovered ? "pointer" : "";
  });

  const startsAreaDrag = useEffectEvent(
    (event: PointerEvent) =>
      event.button === 0 && styleLoadedRef.current && (areaSelectMode || event.shiftKey),
  );

  const releaseDragPan = useEffectEvent((map: MapLibreMap) => {
    if (!areaSelectMode) {
      map.dragPan.enable();
    }
  });

  const selectPlacesInRect = useEffectEvent((map: MapLibreMap, rect: ScreenRect) => {
    const centerLng = map.getCenter().lng;
    const matched = filterPlacesInScreenRect(
      places,
      rect,
      ([lng, lat]) => map.project([normalizeLngNear(lng, centerLng), lat]),
      projection === "globe"
        ? (place, point) => isSameGlobePoint(place, map.unproject([point.x, point.y]))
        : undefined,
    );
    if (matched.length > 0) {
      onSelectAreaPlaces(matched);
    }
  });

  useImperativeHandle(ref, () => {
    const withMap = (run: (map: MapLibreMap) => void) => {
      if (mapRef.current) {
        run(mapRef.current);
      }
    };
    return {
      flyTo: (point, overlays) =>
        withMap((map) =>
          map.easeTo({
            center: [point.lng, point.lat],
            zoom: Math.max(map.getZoom(), MIN_FOCUS_ZOOM),
            padding: overlayPadding(map, overlays),
            duration: easeDuration(EASE_MS.focus),
          }),
        ),
      fitBounds: (bounds, overlays) =>
        withMap((map) => {
          if (isFittableBounds(bounds)) {
            map.fitBounds(
              [
                [bounds.minLng, bounds.minLat],
                [bounds.maxLng, bounds.maxLat],
              ],
              {
                padding: overlayPadding(map, overlays, MIN_FIT_BOUNDS_PADDING),
                maxZoom: STREET_ZOOM,
                duration: easeDuration(EASE_MS.fit),
              },
            );
          }
        }),
      resetView: () =>
        withMap((map) => {
          map.stop();
          map.easeTo({
            center: DEFAULT_CENTER,
            zoom: DEFAULT_ZOOM,
            bearing: 0,
            pitch: 0,
            padding: { top: 0, right: 0, bottom: 0, left: 0 },
            duration: easeDuration(EASE_MS.reset),
          });
        }),
    };
  }, []);

  useEffect(() => {
    const container = containerRef.current;
    if (!container) {
      return;
    }
    const map = new MapLibreMap({
      container,
      center: DEFAULT_CENTER,
      zoom: DEFAULT_ZOOM,
      boxZoom: false,
      attributionControl: { compact: true },
    });
    let hoverFrame = 0;

    map.on("styleimagemissing", (event) => registerCategoryPinIcon(map, event.id));
    map.on("style.load", () => {
      styleLoadedRef.current = true;
      registerCategoryPinIcons(map);
      addAtlasLayers(map);
      container.dataset.styleLoaded = "true";
      emitViewport(map);
      setStyleRevision((revision) => revision + 1);
    });
    map.once("load", () => {
      container.dataset.mapLoaded = "true";
    });
    map.on("moveend", () => emitViewport(map));
    map.on("click", (event) => handleMapClick(map, event.point));
    map.on("mousemove", (event) => {
      cancelAnimationFrame(hoverFrame);
      hoverFrame = requestAnimationFrame(() => updateHoverCursor(map, event.point));
    });
    mapRef.current = map;

    return () => {
      cancelAnimationFrame(hoverFrame);
      styleLoadedRef.current = false;
      mapRef.current = null;
      map.remove();
      delete container.dataset.mapLoaded;
      delete container.dataset.styleLoaded;
      delete container.dataset.projection;
    };
  }, []);

  useEffect(() => {
    const map = mapRef.current;
    if (!map) {
      return;
    }
    styleLoadedRef.current = false;
    delete map.getContainer().dataset.styleLoaded;
    map.setStyle(STYLE_BY_BASEMAP[basemap], { diff: false });
  }, [basemap]);

  useEffect(() => {
    const map = mapRef.current;
    if (map) {
      map.dragPan[areaSelectMode ? "disable" : "enable"]();
      map.getCanvas().style.cursor = areaSelectMode ? "crosshair" : "";
    }
  }, [areaSelectMode]);

  useEffect(() => {
    const container = containerRef.current;
    const map = mapRef.current;
    if (!container || !map) {
      return;
    }
    let drag: { pointerId: number; box: DragBox } | null = null;
    let clickResetTimer = 0;

    const pointIn = (event: PointerEvent) => {
      const rect = container.getBoundingClientRect();
      return {
        x: Math.max(0, Math.min(rect.width, event.clientX - rect.left)),
        y: Math.max(0, Math.min(rect.height, event.clientY - rect.top)),
      };
    };
    const endDrag = () => {
      if (drag && container.hasPointerCapture(drag.pointerId)) {
        container.releasePointerCapture(drag.pointerId);
      }
      drag = null;
      setDragBox(null);
      releaseDragPan(map);
    };
    const handlePointerDown = (event: PointerEvent) => {
      if (drag || !startsAreaDrag(event)) {
        return;
      }
      event.preventDefault();
      event.stopPropagation();
      map.dragPan.disable();
      container.setPointerCapture(event.pointerId);
      const { x, y } = pointIn(event);
      drag = {
        pointerId: event.pointerId,
        box: { startX: x, startY: y, currentX: x, currentY: y },
      };
      setDragBox(drag.box);
    };
    const handlePointerMove = (event: PointerEvent) => {
      if (drag?.pointerId !== event.pointerId) {
        return;
      }
      const { x, y } = pointIn(event);
      drag = { ...drag, box: { ...drag.box, currentX: x, currentY: y } };
      setDragBox(drag.box);
    };
    const handlePointerUp = (event: PointerEvent) => {
      if (drag?.pointerId !== event.pointerId) {
        return;
      }
      const rect = boxRect(drag.box);
      endDrag();
      if (rect.maxX - rect.minX >= MIN_DRAG_BOX_PX && rect.maxY - rect.minY >= MIN_DRAG_BOX_PX) {
        suppressNextClickRef.current = true;
        clickResetTimer = window.setTimeout(() => {
          suppressNextClickRef.current = false;
        }, 0);
        selectPlacesInRect(map, rect);
      }
    };
    const handlePointerCancel = (event: PointerEvent) => {
      if (drag?.pointerId === event.pointerId) {
        endDrag();
      }
    };
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape" && drag) {
        endDrag();
      }
    };

    container.addEventListener("pointerdown", handlePointerDown, true);
    container.addEventListener("pointermove", handlePointerMove);
    container.addEventListener("pointerup", handlePointerUp);
    container.addEventListener("pointercancel", handlePointerCancel);
    window.addEventListener("keydown", handleKeyDown);
    return () => {
      container.removeEventListener("pointerdown", handlePointerDown, true);
      container.removeEventListener("pointermove", handlePointerMove);
      container.removeEventListener("pointerup", handlePointerUp);
      container.removeEventListener("pointercancel", handlePointerCancel);
      window.removeEventListener("keydown", handleKeyDown);
      window.clearTimeout(clickResetTimer);
      drag = null;
    };
  }, []);

  useEffect(() => {
    const map = styledMap(mapRef, styleLoadedRef, styleRevision);
    if (map) {
      map.setProjection({ type: projection });
      map.getContainer().dataset.projection = projection;
    }
  }, [projection, styleRevision]);

  useEffect(() => {
    const map = styledMap(mapRef, styleLoadedRef, styleRevision);
    if (map) {
      setPlaces(map, places);
    }
  }, [places, styleRevision]);

  useEffect(() => {
    const map = styledMap(mapRef, styleLoadedRef, styleRevision);
    if (map) {
      setPlaceHighlights(map, selectedPlaceId, highlightedPlaceIds);
    }
  }, [selectedPlaceId, highlightedPlaceIds, styleRevision]);

  useEffect(() => {
    const map = styledMap(mapRef, styleLoadedRef, styleRevision);
    if (map) {
      showAdmin1Country(map, selectedCountryCode);
    }
  }, [selectedCountryCode, styleRevision]);

  useEffect(() => {
    const map = styledMap(mapRef, styleLoadedRef, styleRevision);
    if (map) {
      syncRegionStates(map, {
        countrySummaries,
        admin1Summaries,
        selectedCountryCode,
        selectedRegionLevel,
        selectedRegionCode,
      });
    }
  }, [
    countrySummaries,
    admin1Summaries,
    selectedCountryCode,
    selectedRegionLevel,
    selectedRegionCode,
    styleRevision,
  ]);

  const selectionRect = dragBox ? boxRect(dragBox) : null;

  return (
    <section className="relative h-full w-full" aria-label="MeanderAtlas world globe">
      <div ref={containerRef} data-testid="atlas-map-canvas" className="h-full w-full" />
      {selectionRect &&
        selectionRect.maxX > selectionRect.minX &&
        selectionRect.maxY > selectionRect.minY && (
          <div
            data-testid="area-selection-box"
            className="pointer-events-none absolute z-10 rounded border-2 border-dashed border-atlas-accent bg-atlas-accent/15"
            style={{
              left: selectionRect.minX,
              top: selectionRect.minY,
              width: selectionRect.maxX - selectionRect.minX,
              height: selectionRect.maxY - selectionRect.minY,
            }}
          />
        )}
    </section>
  );
}
