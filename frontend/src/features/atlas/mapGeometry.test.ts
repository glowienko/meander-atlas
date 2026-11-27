import { describe, expect, it } from "vitest";
import type { AtlasPlaceView } from "../../api/client";
import {
  clampMapPadding,
  computeMapPadding,
  filterPlacesInScreenRect,
  isSameGlobePoint,
  normalizeLngNear,
} from "./mapGeometry";

const BASE_PLACE: AtlasPlaceView = {
  id: "11111111-1111-1111-1111-111111111111",
  canonicalName: "Wawel",
  lat: 50.054,
  lng: 19.935,
  category: "LANDMARK",
  categorySource: "HEURISTIC",
  coordSource: "TAKEOUT",
  derivedRole: "MUST_SEE",
  visitStatus: "UNVISITED",
  visitCount: 0,
  countryCode: "PL",
  memberships: [],
};

function makePlace(id: string, canonicalName: string, lat: number, lng: number): AtlasPlaceView {
  return { ...BASE_PLACE, id, canonicalName, lat, lng };
}

function projectRotated(
  lng: number,
  lat: number,
  centerLng: number,
  centerLat: number,
  bearingDeg: number,
  pitchDeg = 0,
): { x: number; y: number } {
  const rad = (bearingDeg * Math.PI) / 180;
  const dx = (lng - centerLng) * 10;
  const dy = -(lat - centerLat) * 10;
  const cos = Math.cos(rad);
  const sin = Math.sin(rad);
  const rx = dx * cos + dy * sin;
  const ry = (-dx * sin + dy * cos) * Math.cos((pitchDeg * Math.PI) / 180);
  return { x: 400 + rx, y: 300 + ry };
}

describe("mapGeometry", () => {
  it("computes drawer-aware padding on desktop viewports", () => {
    expect(
      computeMapPadding({
        statsOpen: false,
        dossierOpen: false,
        placeDetailOpen: false,
        viewportWidth: 1280,
        viewportHeight: 800,
      }),
    ).toEqual({ top: 112, right: 24, bottom: 24, left: 24 });

    expect(
      computeMapPadding({
        statsOpen: true,
        dossierOpen: true,
        placeDetailOpen: true,
        viewportWidth: 1280,
        viewportHeight: 800,
      }),
    ).toEqual({ top: 112, right: 400, bottom: 240, left: 352 });
  });

  it("computes mobile padding and clamps proportionally on narrow or short viewports", () => {
    expect(
      computeMapPadding({
        statsOpen: true,
        dossierOpen: true,
        placeDetailOpen: true,
        viewportWidth: 375,
        viewportHeight: 667,
      }),
    ).toEqual({ top: 112, right: 16, bottom: 220, left: 16 });

    const clamped = clampMapPadding({ top: 112, right: 400, bottom: 240, left: 352 }, 768, 500);
    expect(clamped.left + clamped.right).toBeLessThanOrEqual(768 - 160);
    expect(clamped.top + clamped.bottom).toBeLessThanOrEqual(500 - 160);
    expect(clamped.right).toBeGreaterThan(clamped.left);

    const tiny = clampMapPadding({ top: 200, right: 200, bottom: 200, left: 200 }, 240, 240);
    expect(tiny.left + tiny.right).toBeLessThanOrEqual(144);
    expect(tiny.top + tiny.bottom).toBeLessThanOrEqual(144);
  });

  it("guarantees left+right < width and top+bottom < height for tiny and arbitrary positive viewports", () => {
    const heavy = { top: 112, right: 400, bottom: 240, left: 352 };

    expect(clampMapPadding(heavy, 1, 1)).toEqual({ top: 0, right: 0, bottom: 0, left: 0 });
    expect(clampMapPadding(heavy, 2, 2)).toEqual({ top: 0, right: 0, bottom: 0, left: 0 });

    const three = clampMapPadding(heavy, 3, 3);
    expect(three.left + three.right).toBeLessThan(3);
    expect(three.top + three.bottom).toBeLessThan(3);

    for (const width of [1, 2, 2.5, 3, 3.7, 4, 5, 10, 25, 50, 120, 320, 1280]) {
      for (const height of [1, 2, 2.5, 3, 3.7, 4, 5, 10, 25, 50, 120, 480, 800]) {
        const padded = computeMapPadding({
          statsOpen: true,
          dossierOpen: true,
          placeDetailOpen: true,
          viewportWidth: width,
          viewportHeight: height,
        });
        expect(padded.left).toBeGreaterThanOrEqual(0);
        expect(padded.right).toBeGreaterThanOrEqual(0);
        expect(padded.top).toBeGreaterThanOrEqual(0);
        expect(padded.bottom).toBeGreaterThanOrEqual(0);
        expect(padded.left + padded.right).toBeLessThan(width);
        expect(padded.top + padded.bottom).toBeLessThan(height);
      }
    }
  });

  it("filters places in screen rect under 90-deg and 180-deg bearing rotation and pitch", () => {
    const inside = makePlace("inside", "Inside", 25, 15);
    const tokyo = makePlace("tokyo", "Tokyo", 25, 139.7);
    const nyc = makePlace("nyc", "NYC", 25, -74);
    const southEdge = makePlace("south", "South", 19, 15);

    const rect180 = { minX: 350, minY: 250, maxX: 450, maxY: 350 };
    expect(
      filterPlacesInScreenRect([inside, tokyo, nyc, southEdge], rect180, ([lng, lat]) =>
        projectRotated(lng, lat, 15, 25, 180),
      ).map((p) => p.id),
    ).toEqual(["inside"]);

    const northEast = makePlace("ne", "NorthEast", 28, 18);
    const southWest = makePlace("sw", "SouthWest", 22, 12);
    expect(
      filterPlacesInScreenRect([inside, northEast, southWest, tokyo], rect180, ([lng, lat]) =>
        projectRotated(lng, lat, 15, 25, 90),
      ).map((p) => p.id),
    ).toEqual(["inside", "ne", "sw"]);

    const pitchedFar = makePlace("pitched-far", "PitchedFar", 33, 15);
    expect(
      filterPlacesInScreenRect([inside, pitchedFar], rect180, ([lng, lat]) =>
        projectRotated(lng, lat, 15, 25, 0, 60),
      ).map((p) => p.id),
    ).toEqual(["inside", "pitched-far"]);
    expect(
      filterPlacesInScreenRect([inside, pitchedFar], rect180, ([lng, lat]) =>
        projectRotated(lng, lat, 15, 25, 0, 0),
      ).map((p) => p.id),
    ).toEqual(["inside"]);
  });

  it("handles antimeridian wrapping, non-finite projections, and globe back-hemisphere occlusion", () => {
    const fiji = makePlace("fiji", "Suva", -18.14, 178.44);
    const samoa = makePlace("samoa", "Apia", -13.83, -171.75);
    const krakow = BASE_PLACE;
    const antipode = makePlace("antipode", "Antipode", -50.054, -160.065);

    const centerLng = 175;
    expect(
      filterPlacesInScreenRect(
        [krakow, fiji, samoa],
        { minX: 300, minY: 200, maxX: 600, maxY: 450 },
        ([lng, lat]) => projectRotated(normalizeLngNear(lng, centerLng), lat, centerLng, -15, 0),
      ).map((p) => p.id),
    ).toEqual(["fiji", "samoa"]);

    expect(
      filterPlacesInScreenRect(
        [krakow, antipode],
        { minX: 0, minY: 0, maxX: 800, maxY: 600 },
        () => ({ x: 400, y: 300 }),
        (place) => isSameGlobePoint(place, { lat: 50.054, lng: 19.935 }),
      ).map((p) => p.id),
    ).toEqual([krakow.id]);

    expect(
      filterPlacesInScreenRect([krakow], { minX: 0, minY: 0, maxX: 800, maxY: 600 }, () => ({
        x: Number.NaN,
        y: 300,
      })),
    ).toEqual([]);
  });
});
