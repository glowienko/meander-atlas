import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  type AtlasFilterParams,
  fetchAtlasPlaces,
  fetchAtlasStats,
  fetchRegionDossier,
  fetchRegionSummaries,
  patchBulkVisitStatus,
  patchPlaceVisitStatus,
  postRestoreVisitStates,
  type RegionLevel,
  type VisitStateSnapshotView,
  type VisitStatus,
} from "../../api/client";
import type { AtlasViewState } from "./viewState";

export function toFilterParams(state: AtlasViewState): AtlasFilterParams {
  return {
    q: state.q,
    visitStatus: state.visitStatus,
    derivedRole: state.derivedRole,
    category: state.category,
    account: state.account,
    list: state.list,
  };
}

export function useAtlasPlaces(filters: AtlasFilterParams) {
  return useQuery({
    queryKey: ["atlas", "places", filters],
    queryFn: () => fetchAtlasPlaces(filters),
    staleTime: 15_000,
  });
}

export function useRegionSummaries(
  level: RegionLevel,
  countryCode: string | undefined,
  filters: AtlasFilterParams,
  enabled = true,
) {
  return useQuery({
    queryKey: ["atlas", "regions", level, countryCode ?? "", filters],
    queryFn: () => fetchRegionSummaries(level, countryCode, filters),
    enabled,
    staleTime: 15_000,
  });
}

export function useRegionDossier(level: RegionLevel | "", code: string) {
  const normalizedCode = code.trim();
  return useQuery({
    queryKey: ["atlas", "dossier", level, normalizedCode],
    queryFn: () => fetchRegionDossier(level as RegionLevel, normalizedCode),
    enabled: level !== "" && normalizedCode.length > 0,
    staleTime: 15_000,
  });
}

export function useAtlasStats() {
  return useQuery({
    queryKey: ["atlas", "stats"],
    queryFn: fetchAtlasStats,
    staleTime: 15_000,
  });
}

export function useUpdatePlaceVisitStatus() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ placeId, status }: { placeId: string; status: VisitStatus }) =>
      patchPlaceVisitStatus(placeId, status),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["atlas"] });
    },
  });
}

export function useBulkUpdateVisitStatus() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ placeIds, targetStatus }: { placeIds: string[]; targetStatus: VisitStatus }) =>
      patchBulkVisitStatus(placeIds, targetStatus),
    onSettled: async () => {
      await queryClient.invalidateQueries({ queryKey: ["atlas"] });
    },
  });
}

export function useRestoreVisitStates() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (snapshots: VisitStateSnapshotView[]) => postRestoreVisitStates(snapshots),
    onSettled: async () => {
      await queryClient.invalidateQueries({ queryKey: ["atlas"] });
    },
  });
}
