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
export type OfflineMaterializationSummaryView =
  components["schemas"]["OfflineMaterializationSummaryView"];
export type EnrichmentBatchResultView = components["schemas"]["EnrichmentBatchResultView"];
export type CoordSource = AtlasPlaceView["coordSource"];
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

export const BULK_BATCH_SIZE = 500;

export function chunkItems<T>(items: readonly T[], size = BULK_BATCH_SIZE): T[][] {
  const safeSize = Math.max(1, Math.floor(size));
  const chunks: T[][] = [];
  for (let i = 0; i < items.length; i += safeSize) {
    chunks.push(items.slice(i, i + safeSize));
  }
  return chunks;
}

export async function patchBulkVisitStatus(
  placeIds: string[],
  targetStatus: VisitStatus,
): Promise<BulkVisitStatusResultView> {
  if (placeIds.length === 0) {
    return { updatedCount: 0, previousStates: [] };
  }
  let updatedCount = 0;
  const previousStates: VisitStateSnapshotView[] = [];
  for (const batch of chunkItems(placeIds, BULK_BATCH_SIZE)) {
    const result = unwrapResponse(
      await apiClient.PATCH("/api/v1/atlas/places/bulk-visit-status", {
        body: { placeIds: batch, targetStatus },
      }),
      "Bulk update visit status",
    );
    updatedCount += result.updatedCount;
    previousStates.push(...result.previousStates);
  }
  return { updatedCount, previousStates };
}

export async function postRestoreVisitStates(
  snapshots: VisitStateSnapshotView[],
): Promise<RestoreVisitStatesResultView> {
  if (snapshots.length === 0) {
    return { restoredCount: 0 };
  }
  let restoredCount = 0;
  for (const batch of chunkItems(snapshots, BULK_BATCH_SIZE)) {
    const result = unwrapResponse(
      await apiClient.POST("/api/v1/atlas/places/bulk-visit-status/restore", {
        body: { snapshots: batch },
      }),
      "Restore visit states",
    );
    restoredCount += result.restoredCount;
  }
  return { restoredCount };
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

export async function postMaterializeOffline(): Promise<OfflineMaterializationSummaryView> {
  return unwrapResponse(
    await apiClient.POST("/api/v1/imports/materialize-offline"),
    "Offline materialization request",
  );
}

export async function postRunEnrichment(options?: {
  limit?: number;
  dryRun?: boolean;
  background?: boolean;
}): Promise<EnrichmentBatchResultView> {
  return unwrapResponse(
    await apiClient.POST("/api/v1/imports/enrich", {
      params: {
        query: {
          limit: options?.limit,
          dryRun: options?.dryRun,
          background: options?.background,
        },
      },
    }),
    "Enrichment batch request",
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
