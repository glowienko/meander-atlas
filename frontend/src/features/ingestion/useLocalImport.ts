import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  fetchImportStatus,
  type ImportStatusView,
  type LocalImportSummaryView,
  postLocalImport,
} from "../../api/client";

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

export function useImportActions(notify: Notify) {
  const queryClient = useQueryClient();
  const localImport = useMutation({
    mutationFn: () => postLocalImport(),
    onSuccess: async (result) => {
      notify(describeLocalImport(result));
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["atlas"] }),
        queryClient.invalidateQueries({ queryKey: ["ingestion"] }),
      ]);
    },
    onError: () => notify("Local folder sync failed"),
  });
  return { localImport };
}
