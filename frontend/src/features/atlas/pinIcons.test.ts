import type { Map as MapLibreMap } from "maplibre-gl";
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  PIN_ICON_CATEGORY_LIST,
  PIN_ICON_IDS,
  registerCategoryPinIcon,
  registerCategoryPinIcons,
} from "./pinIcons";

describe("pinIcons", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("defines unique icon IDs for all 16 non-UNCATEGORIZED categories", () => {
    expect(PIN_ICON_CATEGORY_LIST).toHaveLength(16);
    expect(Object.keys(PIN_ICON_IDS)).toHaveLength(16);
    const uniqueIds = new Set(Object.values(PIN_ICON_IDS));
    expect(uniqueIds.size).toBe(16);
  });

  it("handles missing canvas 2d context gracefully and ignores unknown icon IDs", () => {
    const originalCreateElement = document.createElement.bind(document);
    vi.spyOn(document, "createElement").mockImplementation((tagName: string) => {
      if (tagName === "canvas") {
        return {
          width: 0,
          height: 0,
          getContext: () => null,
        } as unknown as HTMLCanvasElement;
      }
      return originalCreateElement(tagName);
    });

    const addImage = vi.fn();
    const fakeMap = {
      hasImage: () => false,
      addImage,
    } as unknown as MapLibreMap;

    registerCategoryPinIcons(fakeMap);
    registerCategoryPinIcon(fakeMap, "unknown-sprite");
    expect(addImage).not.toHaveBeenCalled();
  });

  it("registers all 16 retina icons when canvas 2d context is available and skips duplicates", () => {
    const registered = new Set<string>();
    const addImage = vi.fn((id: string) => {
      registered.add(id);
    });
    const fakeMap = {
      hasImage: (id: string) => registered.has(id),
      addImage,
    } as unknown as MapLibreMap;

    const fakeContext = {
      strokeStyle: "",
      fillStyle: "",
      lineWidth: 1,
      lineCap: "butt",
      lineJoin: "miter",
      beginPath: vi.fn(),
      moveTo: vi.fn(),
      lineTo: vi.fn(),
      quadraticCurveTo: vi.fn(),
      arc: vi.fn(),
      closePath: vi.fn(),
      stroke: vi.fn(),
      fill: vi.fn(),
      getImageData: vi.fn(() => ({
        width: 24,
        height: 24,
        data: new Uint8ClampedArray(24 * 24 * 4),
      })),
    } as unknown as CanvasRenderingContext2D;

    const originalCreateElement = document.createElement.bind(document);
    vi.spyOn(document, "createElement").mockImplementation((tagName: string) => {
      if (tagName === "canvas") {
        return {
          width: 0,
          height: 0,
          getContext: () => fakeContext,
        } as unknown as HTMLCanvasElement;
      }
      return originalCreateElement(tagName);
    });

    registerCategoryPinIcons(fakeMap);
    expect(addImage).toHaveBeenCalledTimes(16);
    for (const category of PIN_ICON_CATEGORY_LIST) {
      expect(addImage).toHaveBeenCalledWith(
        PIN_ICON_IDS[category],
        expect.objectContaining({ width: 24, height: 24 }),
        { pixelRatio: 2 },
      );
    }

    registerCategoryPinIcons(fakeMap);
    expect(addImage).toHaveBeenCalledTimes(16);
  });
});
