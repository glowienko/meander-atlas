import createClient from "openapi-fetch";
import type { components, paths } from "./schema";

export type AtlasHealthView = components["schemas"]["AtlasHealthView"];
export type AtlasPlaceView = components["schemas"]["AtlasPlaceView"];
export type AtlasPlacesResponse = components["schemas"]["AtlasPlacesResponse"];
export type RegionSummaryView = components["schemas"]["RegionSummaryView"];
export type RegionDossierView = components["schemas"]["RegionDossierView"];
export type AtlasStatsView = components["schemas"]["AtlasStatsView"];
export type AccountListFacetView = components["schemas"]["AccountListFacetView"];
export type PlaceVisitStateView = components["schemas"]["PlaceVisitStateView"];
export type BulkVisitStatusResultView = components["schemas"]["BulkVisitStatusResultView"];
export type RestoreVisitStatesResultView = components["schemas"]["RestoreVisitStatesResultView"];
export type VisitStateSnapshotView = components["schemas"]["VisitStateSnapshotView"];
export type AccountImportSummaryView = components["schemas"]["AccountImportSummaryView"];
export type LocalImportSummaryView = components["schemas"]["LocalImportSummaryView"];
export type ImportStatusView = components["schemas"]["ImportStatusView"];
export type VisitStatus = AtlasPlaceView["visitStatus"];
export type DerivedRole = AtlasPlaceView["derivedRole"];
export type PlaceCategory = AtlasPlaceView["category"];
export type RegionLevel = RegionSummaryView["level"];

export interface AtlasFilterParams {
  q?: string;
  visitStatus?: VisitStatus | "";
  derivedRole?: DerivedRole | "";
  category?: PlaceCategory | "";
  account?: string;
  list?: string;
  countryCode?: string;
}

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

export async function fetchAtlasPlaces(filters: AtlasFilterParams): Promise<AtlasPlacesResponse> {
  const { data, error, response } = await apiClient.GET("/api/v1/atlas/places", {
    params: {
      query: {
        q: nonEmpty(filters.q),
        visitStatus: nonEmptyEnum(filters.visitStatus),
        derivedRole: nonEmptyEnum(filters.derivedRole),
        category: nonEmptyEnum(filters.category),
        account: nonEmpty(filters.account),
        list: nonEmpty(filters.list),
        countryCode: nonEmpty(filters.countryCode),
      },
    },
  });
  if (error || !response.ok || !data) {
    throw new Error(`Atlas places request failed with status ${response.status}`);
  }
  return data;
}

export async function fetchRegionSummaries(
  level: RegionLevel,
  countryCode: string | undefined,
  filters: AtlasFilterParams,
): Promise<RegionSummaryView[]> {
  const { data, error, response } = await apiClient.GET("/api/v1/atlas/regions", {
    params: {
      query: {
        level,
        countryCode: nonEmpty(countryCode),
        q: nonEmpty(filters.q),
        derivedRole: nonEmptyEnum(filters.derivedRole),
        category: nonEmptyEnum(filters.category),
        account: nonEmpty(filters.account),
        list: nonEmpty(filters.list),
      },
    },
  });
  if (error || !response.ok || !data) {
    throw new Error(`Atlas regions request failed with status ${response.status}`);
  }
  return data;
}

export async function fetchRegionDossier(
  level: RegionLevel,
  code: string,
): Promise<RegionDossierView> {
  const { data, error, response } = await apiClient.GET("/api/v1/atlas/regions/{level}/{code}", {
    params: {
      path: { level, code },
    },
  });
  if (error || !response.ok || !data) {
    throw new Error(`Region dossier request failed with status ${response.status}`);
  }
  return data;
}

export async function fetchAtlasStats(): Promise<AtlasStatsView> {
  const { data, error, response } = await apiClient.GET("/api/v1/atlas/stats");
  if (error || !response.ok || !data) {
    throw new Error(`Atlas stats request failed with status ${response.status}`);
  }
  return data;
}

export async function patchPlaceVisitStatus(
  placeId: string,
  status: VisitStatus,
): Promise<PlaceVisitStateView> {
  const { data, error, response } = await apiClient.PATCH(
    "/api/v1/atlas/places/{id}/visit-status",
    {
      params: { path: { id: placeId } },
      body: { status },
    },
  );
  if (error || !response.ok || !data) {
    throw new Error(`Update place status failed with status ${response.status}`);
  }
  return data;
}

export async function patchBulkVisitStatus(
  placeIds: string[],
  targetStatus: VisitStatus,
): Promise<BulkVisitStatusResultView> {
  const { data, error, response } = await apiClient.PATCH(
    "/api/v1/atlas/places/bulk-visit-status",
    {
      body: { placeIds, targetStatus },
    },
  );
  if (error || !response.ok || !data) {
    throw new Error(`Bulk update visit status failed with status ${response.status}`);
  }
  return data;
}

export async function postRestoreVisitStates(
  snapshots: VisitStateSnapshotView[],
): Promise<RestoreVisitStatesResultView> {
  const { data, error, response } = await apiClient.POST(
    "/api/v1/atlas/places/bulk-visit-status/restore",
    {
      body: { snapshots },
    },
  );
  if (error || !response.ok || !data) {
    throw new Error(`Restore visit states failed with status ${response.status}`);
  }
  return data;
}

export async function fetchImportStatus(): Promise<ImportStatusView> {
  const { data, error, response } = await apiClient.GET("/api/v1/imports/status");
  if (error || !response.ok || !data) {
    throw new Error(`Import status request failed with status ${response.status}`);
  }
  return data;
}

export async function postLocalImport(options?: {
  account?: string;
  force?: boolean;
}): Promise<LocalImportSummaryView> {
  const { data, error, response } = await apiClient.POST("/api/v1/imports/local", {
    params: {
      query: {
        account: nonEmpty(options?.account),
        force: options?.force,
      },
    },
  });
  if (error || !response.ok || !data) {
    throw new Error(`Local import request failed with status ${response.status}`);
  }
  return data;
}

function nonEmpty(value: string | undefined): string | undefined {
  if (!value) {
    return undefined;
  }
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : undefined;
}

function nonEmptyEnum<T extends string>(value: T | "" | undefined): T | undefined {
  return value && value.length > 0 ? value : undefined;
}
