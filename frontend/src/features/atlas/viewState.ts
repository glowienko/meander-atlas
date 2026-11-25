export type AtlasProjection = "globe" | "mercator";
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

const VALID_VISIT_STATUSES = new Set<string>(["UNVISITED", "VISITED", "NEEDS_TRIAGE"]);
const VALID_DERIVED_ROLES = new Set<string>(["PREFERRED_HOTEL", "MUST_SEE", "FAVORITE", "BACKLOG"]);
const VALID_CATEGORIES = new Set<string>([
  "RESTAURANT",
  "CAFE_BAR",
  "HOTEL",
  "MUSEUM",
  "LANDMARK",
  "SACRAL",
  "MOUNTAIN",
  "WATER",
  "NATURE_PARK",
  "VIEWPOINT",
  "UNCATEGORIZED",
]);
const VALID_REGION_LEVELS = new Set<string>(["COUNTRY", "ADMIN1"]);

export function defaultAtlasViewState(): AtlasViewState {
  return {
    projection: "globe",
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

function setOrDelete(params: URLSearchParams, key: string, value: string): void {
  if (value.length > 0) {
    params.set(key, value);
  } else {
    params.delete(key);
  }
}
