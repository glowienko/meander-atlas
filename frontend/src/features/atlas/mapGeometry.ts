import type { CSSProperties } from "react";
import type { AtlasPlaceView } from "../../api/client";

export interface MapPadding {
  top: number;
  right: number;
  bottom: number;
  left: number;
}

export interface MapOverlays {
  statsOpen: boolean;
  dossierOpen: boolean;
  placeDetailOpen: boolean;
}

export interface MapOverlayLayout extends MapOverlays {
  viewportWidth: number;
  viewportHeight: number;
}

export interface GeoBounds {
  minLng: number;
  minLat: number;
  maxLng: number;
  maxLat: number;
}

export interface ScreenPoint {
  x: number;
  y: number;
}

export interface ScreenRect {
  minX: number;
  minY: number;
  maxX: number;
  maxY: number;
}

const TOP_HEADER_PADDING = 112;
const DESKTOP_BREAKPOINT = 768;
const STATS_PANEL_LEFT_PADDING = 352;
const DOSSIER_DRAWER_RIGHT_PADDING = 400;
const PLACE_CARD_BOTTOM_PADDING = 240;
const PLACE_CARD_MOBILE_BOTTOM_PADDING = 220;
const EDGE_PADDING = 24;
const MOBILE_SIDE_PADDING = 16;
const MIN_USABLE_MAP_SPAN = 160;
const MIN_SPHERE_COS_THRESHOLD = 0.99;
const MAX_FIT_LNG_SPAN = 170;
const MAX_FIT_LAT_SPAN = 120;

export const OVERLAY_OFFSET_STYLE = {
  "--atlas-stats-offset": `${STATS_PANEL_LEFT_PADDING}px`,
  "--atlas-dossier-offset": `${DOSSIER_DRAWER_RIGHT_PADDING}px`,
} as CSSProperties;

function clampAxisPadding(first: number, second: number, totalSize: number): [number, number] {
  const safeFirst = Math.max(0, first);
  const safeSecond = Math.max(0, second);
  if (totalSize <= 0) {
    return [Math.round(safeFirst), Math.round(safeSecond)];
  }
  if (totalSize <= 2) {
    return [0, 0];
  }
  const minUsable = Math.max(1, Math.min(MIN_USABLE_MAP_SPAN, Math.floor(totalSize * 0.4)));
  const maxCombined = Math.max(0, Math.floor(totalSize) - minUsable);
  const flooredFirst = Math.floor(safeFirst);
  const flooredSecond = Math.floor(safeSecond);
  const sum = flooredFirst + flooredSecond;
  if (sum <= maxCombined || sum === 0) {
    return [flooredFirst, flooredSecond];
  }
  const scale = maxCombined / sum;
  return [Math.floor(flooredFirst * scale), Math.floor(flooredSecond * scale)];
}

export function clampMapPadding(
  padding: MapPadding,
  viewportWidth: number,
  viewportHeight: number,
): MapPadding {
  const [left, right] = clampAxisPadding(padding.left, padding.right, viewportWidth);
  const [top, bottom] = clampAxisPadding(padding.top, padding.bottom, viewportHeight);
  return { top, right, bottom, left };
}

export function computeMapPadding(layout: MapOverlayLayout, minimum = 0): MapPadding {
  const isDesktop = layout.viewportWidth >= DESKTOP_BREAKPOINT;
  const raw: MapPadding = isDesktop
    ? {
        top: TOP_HEADER_PADDING,
        left: layout.statsOpen ? STATS_PANEL_LEFT_PADDING : EDGE_PADDING,
        right: layout.dossierOpen ? DOSSIER_DRAWER_RIGHT_PADDING : EDGE_PADDING,
        bottom: layout.placeDetailOpen ? PLACE_CARD_BOTTOM_PADDING : EDGE_PADDING,
      }
    : {
        top: TOP_HEADER_PADDING,
        left: MOBILE_SIDE_PADDING,
        right: MOBILE_SIDE_PADDING,
        bottom: layout.placeDetailOpen ? PLACE_CARD_MOBILE_BOTTOM_PADDING : EDGE_PADDING,
      };
  const atLeast = (value: number) => Math.max(value, minimum);
  return clampMapPadding(
    {
      top: atLeast(raw.top),
      right: atLeast(raw.right),
      bottom: atLeast(raw.bottom),
      left: atLeast(raw.left),
    },
    layout.viewportWidth,
    layout.viewportHeight,
  );
}

export function isFittableBounds(bounds: GeoBounds): boolean {
  const lngSpan = bounds.maxLng - bounds.minLng;
  const latSpan = bounds.maxLat - bounds.minLat;
  return lngSpan > 0 && latSpan > 0 && lngSpan <= MAX_FIT_LNG_SPAN && latSpan <= MAX_FIT_LAT_SPAN;
}

export function normalizeLngNear(lng: number, referenceLng: number): number {
  return lng + 360 * Math.round((referenceLng - lng) / 360);
}

export function isSameGlobePoint(
  a: { lat: number; lng: number },
  b: { lat: number; lng: number },
  minCosAngle = MIN_SPHERE_COS_THRESHOLD,
): boolean {
  const toRad = Math.PI / 180;
  const lat1 = a.lat * toRad;
  const lng1 = a.lng * toRad;
  const lat2 = b.lat * toRad;
  const lng2 = b.lng * toRad;
  const cosAngle =
    Math.sin(lat1) * Math.sin(lat2) + Math.cos(lat1) * Math.cos(lat2) * Math.cos(lng1 - lng2);
  return cosAngle >= minCosAngle;
}

export function filterPlacesInScreenRect(
  places: readonly AtlasPlaceView[],
  rect: ScreenRect,
  project: (lngLat: [number, number]) => ScreenPoint,
  isVisible?: (place: AtlasPlaceView, point: ScreenPoint) => boolean,
): AtlasPlaceView[] {
  const minX = Math.min(rect.minX, rect.maxX);
  const maxX = Math.max(rect.minX, rect.maxX);
  const minY = Math.min(rect.minY, rect.maxY);
  const maxY = Math.max(rect.minY, rect.maxY);
  return places.filter((place) => {
    const point = project([place.lng, place.lat]);
    if (
      !Number.isFinite(point.x) ||
      !Number.isFinite(point.y) ||
      point.x < minX ||
      point.x > maxX ||
      point.y < minY ||
      point.y > maxY
    ) {
      return false;
    }
    return isVisible ? isVisible(place, point) : true;
  });
}
