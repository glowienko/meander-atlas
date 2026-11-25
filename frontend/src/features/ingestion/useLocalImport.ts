import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { fetchImportStatus, postLocalImport } from "../../api/client";

export function useImportStatus() {
  return useQuery({
    queryKey: ["ingestion", "status"],
    queryFn: fetchImportStatus,
    staleTime: 15_000,
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
