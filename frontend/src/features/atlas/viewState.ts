export type AtlasProjection = "globe" | "mercator";
export type AtlasTheme = "light" | "dark";

export interface AtlasViewState {
  projection: AtlasProjection;
  theme: AtlasTheme;
}

export function readAtlasViewState(search: string): AtlasViewState {
  const params = new URLSearchParams(search);
  const projectionParam = params.get("view");
  const themeParam = params.get("theme");

  return {
    projection: projectionParam === "map" ? "mercator" : "globe",
    theme: themeParam === "dark" ? "dark" : "light",
  };
}

export function writeAtlasViewState(state: AtlasViewState, currentSearch: string): string {
  const params = new URLSearchParams(currentSearch);
  params.set("view", state.projection === "mercator" ? "map" : "globe");
  params.set("theme", state.theme);
  return `?${params.toString()}`;
}
