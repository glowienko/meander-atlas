import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  type EnrichmentBatchResultView,
  fetchImportStatus,
  type ImportStatusView,
  type LocalImportSummaryView,
  type OfflineMaterializationSummaryView,
  postLocalImport,
  postMaterializeOffline,
  postRunEnrichment,
} from "../../api/client";

export interface EnrichmentOptions {
  limit: number;
  background: boolean;
}

type Notify = (message: string) => void;

const ENRICHMENT_POLL_INTERVAL_MS = 3_000;
const IMPORT_STATUS_KEY = ["ingestion", "status"];

function enrichmentAdvanced(previous: ImportStatusView | undefined, next: ImportStatusView) {
  const finishedRun = (previous?.enrichmentRunning ?? false) && !next.enrichmentRunning;
  const progressedDuringRun =
    next.enrichmentRunning && next.enrichedPlaceCount > (previous?.enrichedPlaceCount ?? 0);
  return finishedRun || progressedDuringRun;
}

export function useImportStatus() {
  const queryClient = useQueryClient();
  return useQuery({
    queryKey: IMPORT_STATUS_KEY,
    queryFn: async () => {
      const previous = queryClient.getQueryData<ImportStatusView>(IMPORT_STATUS_KEY);
      const status = await fetchImportStatus();
      if (enrichmentAdvanced(previous, status)) {
        void queryClient.invalidateQueries({ queryKey: ["atlas"] });
      }
      return status;
    },
    staleTime: 15_000,
    refetchInterval: (query) =>
      query.state.data?.enrichmentRunning ? ENRICHMENT_POLL_INTERVAL_MS : false,
  });
}

function describeLocalImport(result: LocalImportSummaryView): string {
  if (result.accounts.length === 0) {
    return "No local import accounts found";
  }
  if (result.accounts.every((account) => account.skipped)) {
    return "Local folder already up to date";
  }
  return `Synced ${result.totalPlacesCreated} new, ${result.totalPlacesMerged} merged · ${result.totalQueuedResolutionJobs} awaiting coordinates`;
}

function describeMaterialization(result: OfflineMaterializationSummaryView): string {
  return `Materialized ${result.materializedCount} places (${result.exactFromTitleCount} exact, ${result.approxFromS2Count} approx, ${result.mergedByCidCount} merged)`;
}

function describeEnrichment(result: EnrichmentBatchResultView): string {
  if (!result.enabled) {
    return "Open-catalog enrichment is disabled on the server";
  }
  if (result.alreadyRunning) {
    return "Enrichment is already running in the background";
  }
  if (result.startedInBackground) {
    return `Background enrichment started (${result.remainingPendingCount} remaining)`;
  }
  return `Enriched ${result.matchedCount}/${result.claimedCount} (${result.upgradedCoordsCount} coords, ${result.upgradedCategoryCount} categories, ${result.photosAddedCount} photos)`;
}

function useIngestionMutation<TVariables, TResult>(
  mutationFn: (variables: TVariables) => Promise<TResult>,
  describe: (result: TResult) => string,
  failureMessage: string,
  notify: Notify,
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: async (result) => {
      notify(describe(result));
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["atlas"] }),
        queryClient.invalidateQueries({ queryKey: ["ingestion"] }),
      ]);
    },
    onError: () => notify(failureMessage),
  });
}

export function useImportActions(notify: Notify) {
  return {
    localImport: useIngestionMutation<void, LocalImportSummaryView>(
      () => postLocalImport(),
      describeLocalImport,
      "Local folder sync failed",
      notify,
    ),
    materialize: useIngestionMutation<void, OfflineMaterializationSummaryView>(
      () => postMaterializeOffline(),
      describeMaterialization,
      "Offline materialization failed",
      notify,
    ),
    enrichment: useIngestionMutation(
      (options: EnrichmentOptions) => postRunEnrichment(options),
      describeEnrichment,
      "Enrichment request failed",
      notify,
    ),
  };
}
