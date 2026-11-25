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

function unwrapResponse<T>(
  result: { data?: T; error?: unknown; response: Response },
  label: string,
): T {
  if (result.error || !result.response.ok || result.data === undefined) {
    throw new Error(`${label} failed with status ${result.response.status}`);
  }
  return result.data;
}

export async function fetchAtlasHealth(): Promise<AtlasHealthView> {
  return unwrapResponse(await apiClient.GET("/api/v1/atlas/health"), "Atlas health request");
}

export async function fetchAtlasPlaces(filters: AtlasFilterParams): Promise<AtlasPlacesResponse> {
  return unwrapResponse(
    await apiClient.GET("/api/v1/atlas/places", {
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
    }),
    "Atlas places request",
  );
}

export async function fetchRegionSummaries(
  level: RegionLevel,
  countryCode: string | undefined,
  filters: AtlasFilterParams,
): Promise<RegionSummaryView[]> {
  return unwrapResponse(
    await apiClient.GET("/api/v1/atlas/regions", {
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
    }),
    "Atlas regions request",
  );
}

export async function fetchRegionDossier(
  level: RegionLevel,
  code: string,
): Promise<RegionDossierView> {
  return unwrapResponse(
    await apiClient.GET("/api/v1/atlas/regions/{level}/{code}", {
      params: {
        path: { level, code },
      },
    }),
    "Region dossier request",
  );
}

export async function fetchAtlasStats(): Promise<AtlasStatsView> {
  return unwrapResponse(await apiClient.GET("/api/v1/atlas/stats"), "Atlas stats request");
}

export async function patchPlaceVisitStatus(
  placeId: string,
  status: VisitStatus,
): Promise<PlaceVisitStateView> {
  return unwrapResponse(
    await apiClient.PATCH("/api/v1/atlas/places/{id}/visit-status", {
      params: { path: { id: placeId } },
      body: { status },
    }),
    "Update place status",
  );
}

export async function patchBulkVisitStatus(
  placeIds: string[],
  targetStatus: VisitStatus,
): Promise<BulkVisitStatusResultView> {
  return unwrapResponse(
    await apiClient.PATCH("/api/v1/atlas/places/bulk-visit-status", {
      body: { placeIds, targetStatus },
    }),
    "Bulk update visit status",
  );
}

export async function postRestoreVisitStates(
  snapshots: VisitStateSnapshotView[],
): Promise<RestoreVisitStatesResultView> {
  return unwrapResponse(
    await apiClient.POST("/api/v1/atlas/places/bulk-visit-status/restore", {
      body: { snapshots },
    }),
    "Restore visit states",
  );
}

export async function fetchImportStatus(): Promise<ImportStatusView> {
  return unwrapResponse(await apiClient.GET("/api/v1/imports/status"), "Import status request");
}

export async function postLocalImport(options?: {
  account?: string;
  force?: boolean;
}): Promise<LocalImportSummaryView> {
  return unwrapResponse(
    await apiClient.POST("/api/v1/imports/local", {
      params: {
        query: {
          account: nonEmpty(options?.account),
          force: options?.force,
        },
      },
    }),
    "Local import request",
  );
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
