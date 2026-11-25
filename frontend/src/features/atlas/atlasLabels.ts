import type { DerivedRole, PlaceCategory, VisitStatus } from "../../api/client";

export const CATEGORY_OPTIONS: readonly { value: PlaceCategory; label: string }[] = [
  { value: "RESTAURANT", label: "Restaurants" },
  { value: "CAFE_BAR", label: "Cafes & Bars" },
  { value: "HOTEL", label: "Hotels & Stays" },
  { value: "MUSEUM", label: "Museums" },
  { value: "LANDMARK", label: "Landmarks" },
  { value: "SACRAL", label: "Temples & Churches" },
  { value: "MOUNTAIN", label: "Mountains & Peaks" },
  { value: "WATER", label: "Water & Coasts" },
  { value: "NATURE_PARK", label: "Nature & Parks" },
  { value: "VIEWPOINT", label: "Viewpoints" },
  { value: "UNCATEGORIZED", label: "Uncategorized" },
];

export const VISIT_STATUS_OPTIONS: readonly { value: VisitStatus; label: string }[] = [
  { value: "VISITED", label: "Visited" },
  { value: "UNVISITED", label: "Bucket list" },
  { value: "NEEDS_TRIAGE", label: "Needs triage" },
];

export const DERIVED_ROLE_OPTIONS: readonly { value: DerivedRole; label: string }[] = [
  { value: "MUST_SEE", label: "Must-see ★" },
  { value: "PREFERRED_HOTEL", label: "Preferred hotel ★" },
  { value: "FAVORITE", label: "Favorite ♥" },
  { value: "BACKLOG", label: "Backlog" },
];

const CATEGORY_MAP = new Map<PlaceCategory, string>(
  CATEGORY_OPTIONS.map((item) => [item.value, item.label]),
);
const STATUS_MAP = new Map<VisitStatus, string>(
  VISIT_STATUS_OPTIONS.map((item) => [item.value, item.label]),
);
const ROLE_MAP = new Map<DerivedRole, string>(
  DERIVED_ROLE_OPTIONS.map((item) => [item.value, item.label]),
);

export function categoryLabel(category: PlaceCategory): string {
  return CATEGORY_MAP.get(category) ?? category;
}

export function visitStatusLabel(status: VisitStatus): string {
  return STATUS_MAP.get(status) ?? status;
}

export function derivedRoleLabel(role: DerivedRole): string {
  return ROLE_MAP.get(role) ?? role;
}
