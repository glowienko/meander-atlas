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
export type PlaceCategoryStateView = components["schemas"]["PlaceCategoryStateView"];
export type BulkVisitStatusResultView = components["schemas"]["BulkVisitStatusResultView"];
export type RestoreVisitStatesResultView = components["schemas"]["RestoreVisitStatesResultView"];
export type VisitStateSnapshotView = components["schemas"]["VisitStateSnapshotView"];
export type AccountImportSummaryView = components["schemas"]["AccountImportSummaryView"];
export type LocalImportSummaryView = components["schemas"]["LocalImportSummaryView"];
export type ImportStatusView = components["schemas"]["ImportStatusView"];
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

export async function patchPlaceCategory(
  placeId: string,
  category: PlaceCategory,
  subcategory?: string,
): Promise<PlaceCategoryStateView> {
  return unwrapResponse(
    await apiClient.PATCH("/api/v1/atlas/places/{id}/category", {
      params: { path: { id: placeId } },
      body: { category, subcategory },
    }),
    "Update place category",
  );
}

export const BULK_BATCH_SIZE = 500;

export function chunkItems<T>(items: readonly T[], size = BULK_BATCH_SIZE): T[][] {
  const safeSize = Math.max(1, Math.floor(size));
  return Array.from({ length: Math.ceil(items.length / safeSize) }, (_, index) =>
    items.slice(index * safeSize, (index + 1) * safeSize),
  );
}

async function sendInChunks<T, R>(
  items: readonly T[],
  send: (chunk: T[]) => Promise<R>,
  rollback: (applied: readonly R[]) => Promise<unknown> = async () => undefined,
): Promise<R[]> {
  const sendRemaining = async (chunks: T[][], applied: R[]): Promise<R[]> => {
    const [chunk, ...rest] = chunks;
    if (!chunk) {
      return applied;
    }
    const result = await send(chunk).catch(async (error: unknown) => {
      await rollback(applied).catch(() => undefined);
      throw error;
    });
    return sendRemaining(rest, [...applied, result]);
  };
  return sendRemaining(chunkItems(items), []);
}

function sum(values: readonly number[]): number {
  return values.reduce((total, value) => total + value, 0);
}

export async function patchBulkVisitStatus(
  placeIds: string[],
  targetStatus: VisitStatus,
): Promise<BulkVisitStatusResultView> {
  const results = await sendInChunks(
    placeIds,
    async (batch) =>
      unwrapResponse(
        await apiClient.PATCH("/api/v1/atlas/places/bulk-visit-status", {
          body: { placeIds: batch, targetStatus },
        }),
        "Bulk update visit status",
      ),
    (applied) => postRestoreVisitStates(applied.flatMap((result) => result.previousStates)),
  );
  return {
    updatedCount: sum(results.map((result) => result.updatedCount)),
    previousStates: results.flatMap((result) => result.previousStates),
  };
}

export async function postRestoreVisitStates(
  snapshots: readonly VisitStateSnapshotView[],
): Promise<RestoreVisitStatesResultView> {
  const results = await sendInChunks(snapshots, async (batch) =>
    unwrapResponse(
      await apiClient.POST("/api/v1/atlas/places/bulk-visit-status/restore", {
        body: { snapshots: batch },
      }),
      "Restore visit states",
    ),
  );
  return { restoredCount: sum(results.map((result) => result.restoredCount)) };
}

export async function fetchImportStatus(): Promise<ImportStatusView> {
  return unwrapResponse(await apiClient.GET("/api/v1/imports/status"), "Import status request");
}

export async function postLocalImport(): Promise<LocalImportSummaryView> {
  return unwrapResponse(await apiClient.POST("/api/v1/imports/local"), "Local import request");
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
