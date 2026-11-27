import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  fetchImportStatus,
  postLocalImport,
  postMaterializeOffline,
  postRunEnrichment,
} from "../../api/client";

const ENRICHMENT_POLL_INTERVAL_MS = 3_000;

export function useImportStatus() {
  return useQuery({
    queryKey: ["ingestion", "status"],
    queryFn: fetchImportStatus,
    staleTime: 15_000,
    refetchInterval: (query) =>
      query.state.data?.enrichmentRunning ? ENRICHMENT_POLL_INTERVAL_MS : false,
  });
}

export function useTriggerLocalImport() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (options?: { account?: string; force?: boolean }) => postLocalImport(options),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["atlas"] }),
        queryClient.invalidateQueries({ queryKey: ["ingestion"] }),
      ]);
    },
  });
}

export function useMaterializeOffline() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => postMaterializeOffline(),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["atlas"] }),
        queryClient.invalidateQueries({ queryKey: ["ingestion"] }),
      ]);
    },
  });
}

export function useRunEnrichment() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (options?: { limit?: number; dryRun?: boolean; background?: boolean }) =>
      postRunEnrichment(options),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["atlas"] }),
        queryClient.invalidateQueries({ queryKey: ["ingestion"] }),
      ]);
    },
  });
}
