export type AtlasProjection = "globe" | "mercator";

export interface AtlasViewState {
  projection: AtlasProjection;
}

export function readAtlasViewState(search: string): AtlasViewState {
  const params = new URLSearchParams(search);
  const projectionParam = params.get("view");

  return {
    projection: projectionParam === "map" ? "mercator" : "globe",
  };
}

export function writeAtlasViewState(state: AtlasViewState, currentSearch: string): string {
  const params = new URLSearchParams(currentSearch);
  if (state.projection === "mercator") {
    params.set("view", "map");
  } else {
    params.delete("view");
  }
  const serialized = params.toString();
  return serialized.length > 0 ? `?${serialized}` : "";
}
