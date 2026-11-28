import type { Map as MapLibreMap } from "maplibre-gl";
import type { PlaceCategory } from "../../api/client";

export type IconPlaceCategory = Exclude<PlaceCategory, "UNCATEGORIZED">;

type GlyphDrawer = (ctx: CanvasRenderingContext2D) => void;

export const PIN_ICON_IDS: Readonly<Record<IconPlaceCategory, string>> = {
  RESTAURANT: "pin-icon-restaurant",
  CAFE_BAR: "pin-icon-cafe-bar",
  HOTEL: "pin-icon-hotel",
  MUSEUM: "pin-icon-museum",
  LANDMARK: "pin-icon-landmark",
  SACRAL: "pin-icon-sacral",
  MOUNTAIN: "pin-icon-mountain",
  WATER: "pin-icon-water",
  NATURE_PARK: "pin-icon-nature-park",
  VIEWPOINT: "pin-icon-viewpoint",
  GROCERIES: "pin-icon-groceries",
  PHARMACY: "pin-icon-pharmacy",
  RESTROOM: "pin-icon-restroom",
  TRANSPORT: "pin-icon-transport",
  PARKING: "pin-icon-parking",
  SHOPPING: "pin-icon-shopping",
};

export const PIN_ICON_CATEGORY_LIST = Object.keys(PIN_ICON_IDS) as readonly IconPlaceCategory[];

const ICON_CANVAS_SIZE = 24;
const ICON_PIXEL_RATIO = 2;

const CATEGORY_BY_ICON_ID = new Map<string, IconPlaceCategory>(
  PIN_ICON_CATEGORY_LIST.map((category) => [PIN_ICON_IDS[category], category]),
);

const GLYPH_DRAWERS: Readonly<Record<IconPlaceCategory, GlyphDrawer>> = {
  RESTAURANT(ctx) {
    ctx.beginPath();
    ctx.moveTo(7.5, 4.5);
    ctx.lineTo(7.5, 19.5);
    ctx.moveTo(5.5, 4.5);
    ctx.lineTo(5.5, 9.5);
    ctx.moveTo(9.5, 4.5);
    ctx.lineTo(9.5, 9.5);
    ctx.moveTo(5.5, 9.5);
    ctx.lineTo(9.5, 9.5);
    ctx.moveTo(16.5, 4.5);
    ctx.lineTo(16.5, 19.5);
    ctx.moveTo(16.5, 4.5);
    ctx.quadraticCurveTo(19.5, 7, 16.5, 12);
    ctx.stroke();
  },
  CAFE_BAR(ctx) {
    ctx.beginPath();
    ctx.moveTo(5.5, 9);
    ctx.lineTo(15.5, 9);
    ctx.lineTo(14.5, 16);
    ctx.quadraticCurveTo(10.5, 17.5, 6.5, 16);
    ctx.closePath();
    ctx.moveTo(15.5, 10);
    ctx.arc(16.5, 12, 2.2, -Math.PI / 2, Math.PI / 2);
    ctx.moveTo(5, 19);
    ctx.lineTo(17, 19);
    ctx.stroke();
  },
  HOTEL(ctx) {
    ctx.beginPath();
    ctx.moveTo(4, 7);
    ctx.lineTo(4, 18);
    ctx.moveTo(4, 14.5);
    ctx.lineTo(20, 14.5);
    ctx.moveTo(20, 11.5);
    ctx.lineTo(20, 18);
    ctx.moveTo(6.5, 11.5);
    ctx.lineTo(11, 11.5);
    ctx.moveTo(12, 11.5);
    ctx.lineTo(19, 11.5);
    ctx.stroke();
  },
  MUSEUM(ctx) {
    ctx.beginPath();
    ctx.moveTo(4, 9);
    ctx.lineTo(12, 4.5);
    ctx.lineTo(20, 9);
    ctx.closePath();
    ctx.moveTo(6.5, 9.5);
    ctx.lineTo(6.5, 17.5);
    ctx.moveTo(12, 9.5);
    ctx.lineTo(12, 17.5);
    ctx.moveTo(17.5, 9.5);
    ctx.lineTo(17.5, 17.5);
    ctx.moveTo(4, 18.5);
    ctx.lineTo(20, 18.5);
    ctx.stroke();
  },
  LANDMARK(ctx) {
    ctx.beginPath();
    ctx.moveTo(12, 4);
    ctx.lineTo(14.5, 8.5);
    ctx.lineTo(15.5, 18.5);
    ctx.lineTo(8.5, 18.5);
    ctx.lineTo(9.5, 8.5);
    ctx.closePath();
    ctx.moveTo(6, 19);
    ctx.lineTo(18, 19);
    ctx.stroke();
  },
  SACRAL(ctx) {
    ctx.beginPath();
    ctx.moveTo(12, 4);
    ctx.lineTo(12, 8.5);
    ctx.moveTo(9.8, 6.2);
    ctx.lineTo(14.2, 6.2);
    ctx.moveTo(7.5, 13);
    ctx.arc(12, 13, 4.5, Math.PI, 0);
    ctx.lineTo(16.5, 18.5);
    ctx.lineTo(7.5, 18.5);
    ctx.closePath();
    ctx.stroke();
  },
  MOUNTAIN(ctx) {
    ctx.beginPath();
    ctx.moveTo(3.5, 18);
    ctx.lineTo(9.5, 7);
    ctx.lineTo(14, 14.5);
    ctx.lineTo(16.5, 10.5);
    ctx.lineTo(20.5, 18);
    ctx.closePath();
    ctx.stroke();
  },
  WATER(ctx) {
    ctx.beginPath();
    ctx.moveTo(4, 10);
    ctx.quadraticCurveTo(6.5, 7.5, 9, 10);
    ctx.quadraticCurveTo(11.5, 12.5, 14, 10);
    ctx.quadraticCurveTo(16.5, 7.5, 20, 10);
    ctx.moveTo(4, 15);
    ctx.quadraticCurveTo(6.5, 12.5, 9, 15);
    ctx.quadraticCurveTo(11.5, 17.5, 14, 15);
    ctx.quadraticCurveTo(16.5, 12.5, 20, 15);
    ctx.stroke();
  },
  NATURE_PARK(ctx) {
    ctx.beginPath();
    ctx.moveTo(12, 4.5);
    ctx.lineTo(17, 11);
    ctx.lineTo(14.5, 11);
    ctx.lineTo(18.5, 16);
    ctx.lineTo(5.5, 16);
    ctx.lineTo(9.5, 11);
    ctx.lineTo(7, 11);
    ctx.closePath();
    ctx.moveTo(12, 16);
    ctx.lineTo(12, 19.5);
    ctx.stroke();
  },
  VIEWPOINT(ctx) {
    ctx.beginPath();
    ctx.moveTo(4, 12);
    ctx.quadraticCurveTo(12, 5.5, 20, 12);
    ctx.quadraticCurveTo(12, 18.5, 4, 12);
    ctx.stroke();
    ctx.beginPath();
    ctx.arc(12, 12, 2.5, 0, Math.PI * 2);
    ctx.fill();
  },
  GROCERIES(ctx) {
    ctx.beginPath();
    ctx.moveTo(4, 6);
    ctx.lineTo(6.5, 6);
    ctx.lineTo(8.5, 15);
    ctx.lineTo(18, 15);
    ctx.lineTo(19.5, 9);
    ctx.lineTo(7.2, 9);
    ctx.stroke();
    ctx.beginPath();
    ctx.arc(9.5, 18.2, 1.4, 0, Math.PI * 2);
    ctx.arc(16.8, 18.2, 1.4, 0, Math.PI * 2);
    ctx.fill();
  },
  PHARMACY(ctx) {
    ctx.beginPath();
    ctx.moveTo(12, 5.5);
    ctx.lineTo(12, 18.5);
    ctx.moveTo(5.5, 12);
    ctx.lineTo(18.5, 12);
    ctx.stroke();
  },
  RESTROOM(ctx) {
    ctx.beginPath();
    ctx.arc(8.5, 6.5, 1.8, 0, Math.PI * 2);
    ctx.arc(15.5, 6.5, 1.8, 0, Math.PI * 2);
    ctx.fill();
    ctx.beginPath();
    ctx.moveTo(8.5, 9.5);
    ctx.lineTo(8.5, 18.5);
    ctx.moveTo(6.5, 13.5);
    ctx.lineTo(10.5, 13.5);
    ctx.moveTo(15.5, 9.5);
    ctx.lineTo(13.5, 15);
    ctx.lineTo(17.5, 15);
    ctx.closePath();
    ctx.moveTo(15.5, 15);
    ctx.lineTo(15.5, 18.5);
    ctx.stroke();
  },
  TRANSPORT(ctx) {
    ctx.beginPath();
    ctx.moveTo(7, 5);
    ctx.lineTo(17, 5);
    ctx.lineTo(17, 15.5);
    ctx.lineTo(7, 15.5);
    ctx.closePath();
    ctx.moveTo(7, 10.5);
    ctx.lineTo(17, 10.5);
    ctx.moveTo(9, 15.5);
    ctx.lineTo(7, 19);
    ctx.moveTo(15, 15.5);
    ctx.lineTo(17, 19);
    ctx.stroke();
  },
  PARKING(ctx) {
    ctx.beginPath();
    ctx.moveTo(8, 18.5);
    ctx.lineTo(8, 5.5);
    ctx.lineTo(13.5, 5.5);
    ctx.arc(13.5, 9.2, 3.7, -Math.PI / 2, Math.PI / 2);
    ctx.lineTo(8, 12.9);
    ctx.stroke();
  },
  SHOPPING(ctx) {
    ctx.beginPath();
    ctx.moveTo(6, 9);
    ctx.lineTo(18, 9);
    ctx.lineTo(17, 19);
    ctx.lineTo(7, 19);
    ctx.closePath();
    ctx.moveTo(9, 9);
    ctx.arc(12, 9, 3, Math.PI, 0);
    ctx.stroke();
  },
};

export function pinIconId(category: PlaceCategory): string {
  return category === "UNCATEGORIZED" ? "" : PIN_ICON_IDS[category];
}

function drawCategoryGlyph(ctx: CanvasRenderingContext2D, category: IconPlaceCategory): void {
  ctx.strokeStyle = "#ffffff";
  ctx.fillStyle = "#ffffff";
  ctx.lineWidth = 2;
  ctx.lineCap = "round";
  ctx.lineJoin = "round";
  GLYPH_DRAWERS[category](ctx);
}

function createCategoryIconImageData(category: IconPlaceCategory): ImageData | null {
  if (typeof document === "undefined") {
    return null;
  }
  const canvas = document.createElement("canvas");
  canvas.width = ICON_CANVAS_SIZE;
  canvas.height = ICON_CANVAS_SIZE;
  const ctx = canvas.getContext("2d");
  if (!ctx) {
    return null;
  }
  drawCategoryGlyph(ctx, category);
  return ctx.getImageData(0, 0, ICON_CANVAS_SIZE, ICON_CANVAS_SIZE);
}

export function registerCategoryPinIcon(map: MapLibreMap, id: string): void {
  const category = CATEGORY_BY_ICON_ID.get(id);
  if (!category || map.hasImage(id)) {
    return;
  }
  const imageData = createCategoryIconImageData(category);
  if (!imageData) {
    return;
  }
  map.addImage(
    id,
    {
      width: ICON_CANVAS_SIZE,
      height: ICON_CANVAS_SIZE,
      data: imageData.data,
    },
    { pixelRatio: ICON_PIXEL_RATIO },
  );
}

export function registerCategoryPinIcons(map: MapLibreMap): void {
  for (const category of PIN_ICON_CATEGORY_LIST) {
    registerCategoryPinIcon(map, PIN_ICON_IDS[category]);
  }
}
