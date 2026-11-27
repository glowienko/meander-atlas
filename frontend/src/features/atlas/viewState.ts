import { CATEGORY_OPTIONS, DERIVED_ROLE_OPTIONS, VISIT_STATUS_OPTIONS } from "./atlasLabels";

export type AtlasProjection = "globe" | "mercator";
export type AtlasBasemap = "atlas" | "street";
export type RegionScope = "COUNTRY" | "ADMIN1";
export type VisitStatusFilter = "UNVISITED" | "VISITED" | "NEEDS_TRIAGE" | "";
export type DerivedRoleFilter = "PREFERRED_HOTEL" | "MUST_SEE" | "FAVORITE" | "BACKLOG" | "";
export type PlaceCategoryFilter =
  | "RESTAURANT"
  | "CAFE_BAR"
  | "HOTEL"
  | "MUSEUM"
  | "LANDMARK"
  | "SACRAL"
  | "MOUNTAIN"
  | "WATER"
  | "NATURE_PARK"
  | "VIEWPOINT"
  | "UNCATEGORIZED"
  | "";

export interface AtlasViewState {
  projection: AtlasProjection;
  basemap: AtlasBasemap;
  q: string;
  visitStatus: VisitStatusFilter;
  derivedRole: DerivedRoleFilter;
  category: PlaceCategoryFilter;
  account: string;
  list: string;
  regionLevel: RegionScope | "";
  regionCode: string;
  statsOpen: boolean;
}

const VALID_VISIT_STATUSES = new Set<string>(VISIT_STATUS_OPTIONS.map((o) => o.value));
const VALID_DERIVED_ROLES = new Set<string>(DERIVED_ROLE_OPTIONS.map((o) => o.value));
const VALID_CATEGORIES = new Set<string>(CATEGORY_OPTIONS.map((o) => o.value));
const VALID_REGION_LEVELS = new Set<string>(["COUNTRY", "ADMIN1"]);

export function defaultAtlasViewState(): AtlasViewState {
  return {
    projection: "globe",
    basemap: "atlas",
    q: "",
    visitStatus: "",
    derivedRole: "",
    category: "",
    account: "",
    list: "",
    regionLevel: "",
    regionCode: "",
    statsOpen: false,
  };
}

export function readAtlasViewState(search: string): AtlasViewState {
  const params = new URLSearchParams(search);
  const projectionParam = params.get("view");
  const basemapParam = params.get("basemap");
  const statusParam = params.get("status") ?? "";
  const roleParam = params.get("role") ?? "";
  const categoryParam = params.get("category") ?? "";
  const regionLevelParam = params.get("regionLevel") ?? "";
  const regionCodeParam = params.get("region")?.trim() ?? "";

  const regionLevel = VALID_REGION_LEVELS.has(regionLevelParam)
    ? (regionLevelParam as RegionScope)
    : regionCodeParam.length > 0
      ? "COUNTRY"
      : "";

  return {
    projection: projectionParam === "map" ? "mercator" : "globe",
    basemap: basemapParam === "street" ? "street" : "atlas",
    q: params.get("q") ?? "",
    visitStatus: VALID_VISIT_STATUSES.has(statusParam) ? (statusParam as VisitStatusFilter) : "",
    derivedRole: VALID_DERIVED_ROLES.has(roleParam) ? (roleParam as DerivedRoleFilter) : "",
    category: VALID_CATEGORIES.has(categoryParam) ? (categoryParam as PlaceCategoryFilter) : "",
    account: params.get("account") ?? "",
    list: params.get("list") ?? "",
    regionLevel: regionCodeParam.length > 0 ? regionLevel : "",
    regionCode: regionLevel !== "" ? regionCodeParam : "",
    statsOpen: params.get("stats") === "1",
  };
}

export function writeAtlasViewState(state: AtlasViewState, currentSearch: string): string {
  const params = new URLSearchParams(currentSearch);

  setOrDelete(params, "view", state.projection === "mercator" ? "map" : "");
  if (params.get("basemap") !== "offline") {
    setOrDelete(params, "basemap", state.basemap === "street" ? "street" : "");
  }
  setOrDelete(params, "q", state.q.trim());
  setOrDelete(params, "status", state.visitStatus);
  setOrDelete(params, "role", state.derivedRole);
  setOrDelete(params, "category", state.category);
  setOrDelete(params, "account", state.account);
  setOrDelete(params, "list", state.list);

  if (state.regionLevel !== "" && state.regionCode.trim().length > 0) {
    params.set("regionLevel", state.regionLevel);
    params.set("region", state.regionCode.trim());
  } else {
    params.delete("regionLevel");
    params.delete("region");
  }

  setOrDelete(params, "stats", state.statsOpen ? "1" : "");

  const serialized = params.toString();
  return serialized.length > 0 ? `?${serialized}` : "";
}

export function hasActiveFilters(state: AtlasViewState): boolean {
  return (
    state.q.trim().length > 0 ||
    state.visitStatus !== "" ||
    state.derivedRole !== "" ||
    state.category !== "" ||
    state.account.length > 0 ||
    state.list.length > 0
  );
}

export function clearAtlasFilters(state: AtlasViewState): AtlasViewState {
  return {
    ...state,
    q: "",
    visitStatus: "",
    derivedRole: "",
    category: "",
    account: "",
    list: "",
  };
}

export function isPlaceInViewport(
  place: { lat: number; lng: number },
  viewport: { minLng: number; minLat: number; maxLng: number; maxLat: number },
): boolean {
  if (place.lat < viewport.minLat || place.lat > viewport.maxLat) {
    return false;
  }
  if (viewport.minLng >= -180 && viewport.maxLng <= 180) {
    if (viewport.minLng <= viewport.maxLng) {
      return place.lng >= viewport.minLng && place.lng <= viewport.maxLng;
    }
    return place.lng >= viewport.minLng || place.lng <= viewport.maxLng;
  }
  const span = viewport.maxLng - viewport.minLng;
  if (span >= 360) {
    return true;
  }
  const arcSpan = span >= 0 ? span : ((span % 360) + 360) % 360;
  const delta = (((place.lng - viewport.minLng) % 360) + 360) % 360;
  return delta <= arcSpan;
}

function setOrDelete(params: URLSearchParams, key: string, value: string): void {
  if (value.length > 0) {
    params.set(key, value);
  } else {
    params.delete(key);
  }
}
