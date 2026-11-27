import { queryOptions, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  type AtlasFilterParams,
  type AtlasPlaceView,
  fetchAtlasPlaces,
  fetchAtlasStats,
  fetchRegionDossier,
  fetchRegionSummaries,
  type PlaceVisitStateView,
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

export function regionDossierQuery(level: RegionLevel, code: string) {
  return queryOptions({
    queryKey: ["atlas", "dossier", level, code.trim()],
    queryFn: () => fetchRegionDossier(level, code.trim()),
    staleTime: 15_000,
  });
}

export function useRegionDossier(level: RegionLevel | "", code: string) {
  return useQuery({
    ...regionDossierQuery(level === "" ? "COUNTRY" : level, code),
    enabled: level !== "" && code.trim().length > 0,
  });
}

export function useAtlasStats() {
  return useQuery({
    queryKey: ["atlas", "stats"],
    queryFn: fetchAtlasStats,
    staleTime: 15_000,
  });
}

function useUpdatePlaceVisitStatus() {
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
    mutationFn: (snapshots: readonly VisitStateSnapshotView[]) => postRestoreVisitStates(snapshots),
    onSettled: async () => {
      await queryClient.invalidateQueries({ queryKey: ["atlas"] });
    },
  });
}

type NotifyVisitChange = (message: string, snapshots?: readonly VisitStateSnapshotView[]) => void;

function visitLabel(status: VisitStatus): string {
  return status === "VISITED" ? "visited" : "unvisited";
}

export function withVisitState(place: AtlasPlaceView, state: PlaceVisitStateView): AtlasPlaceView {
  return place.id === state.placeId
    ? {
        ...place,
        visitStatus: state.status,
        visitSource: state.source,
        visitCount: state.visitCount,
        firstVisitedAt: state.firstVisitedAt,
        lastVisitedAt: state.lastVisitedAt,
      }
    : place;
}

export function useVisitActions(notify: NotifyVisitChange) {
  const single = useUpdatePlaceVisitStatus();
  const bulk = useBulkUpdateVisitStatus();
  const restore = useRestoreVisitStates();
  return {
    isMutating: single.isPending || bulk.isPending || restore.isPending,
    isBulkUpdating: bulk.isPending,
    isRestoring: restore.isPending,
    updatePlace: (
      place: AtlasPlaceView,
      status: VisitStatus,
      onUpdated: (result: PlaceVisitStateView) => void = () => undefined,
    ) =>
      single.mutate(
        { placeId: place.id, status },
        {
          onSuccess: (result) => {
            onUpdated(result);
            notify(`Marked ${place.canonicalName} as ${visitLabel(status)}`, [
              result.previousState,
            ]);
          },
          onError: () => notify(`Could not update ${place.canonicalName}`),
        },
      ),
    updatePlaces: (placeIds: string[], targetStatus: VisitStatus, onSettled: () => void) =>
      bulk.mutate(
        { placeIds, targetStatus },
        {
          onSuccess: ({ updatedCount, previousStates }) =>
            notify(
              `Marked ${updatedCount} place${updatedCount === 1 ? "" : "s"} as ${visitLabel(targetStatus)}`,
              previousStates,
            ),
          onError: () => notify("Could not update selected places"),
          onSettled,
        },
      ),
    undo: (snapshots: readonly VisitStateSnapshotView[], onRestored: () => void) =>
      restore.mutate(snapshots, {
        onSuccess: onRestored,
        onError: () => notify("Could not undo visit changes"),
      }),
  };
}
