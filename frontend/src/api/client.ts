import createClient from "openapi-fetch";
import type { components, paths } from "./schema";

export type AtlasHealthView = components["schemas"]["AtlasHealthView"];

export const apiClient = createClient<paths>({
  baseUrl: "",
});

export async function fetchAtlasHealth(): Promise<AtlasHealthView> {
  const { data, error, response } = await apiClient.GET("/api/v1/atlas/health");
  if (error || !response.ok || !data) {
    throw new Error(`Atlas health request failed with status ${response.status}`);
  }
  return data;
}
