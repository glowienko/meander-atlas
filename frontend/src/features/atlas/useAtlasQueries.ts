import { queryOptions, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  type AtlasFilterParams,
  type AtlasPlaceView,
  type BulkAssignListRequest,
  fetchAtlasPlaces,
  fetchAtlasStats,
  fetchRegionDossier,
  fetchRegionSummaries,
  type PlaceCategory,
  type PlaceCategoryStateView,
  type PlaceListEntryRequest,
  type PlaceListsStateView,
  type PlaceVisitStateView,
  patchBulkVisitStatus,
  patchPlaceCategory,
  patchPlaceVisitStatus,
  postBulkAssignList,
  postRestoreVisitStates,
  putPlaceLists,
  type RegionLevel,
  type VisitStateSnapshotView,
  type VisitStatus,
} from "../../api/client";
import { categoryLabel } from "./atlasLabels";
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

function useUpdatePlaceCategory() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ placeId, category }: { placeId: string; category: PlaceCategory }) =>
      patchPlaceCategory(placeId, category),
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

export function withCategoryState(
  place: AtlasPlaceView,
  state: PlaceCategoryStateView,
): AtlasPlaceView {
  return place.id === state.placeId
    ? {
        ...place,
        category: state.category,
        subcategory: state.subcategory,
        categorySource: state.categorySource,
      }
    : place;
}

export function withListsState(place: AtlasPlaceView, state: PlaceListsStateView): AtlasPlaceView {
  return place.id === state.placeId
    ? {
        ...place,
        derivedRole: state.derivedRole,
        memberships: state.memberships,
      }
    : place;
}

export function useListActions(notify: NotifyVisitChange) {
  const queryClient = useQueryClient();
  const bulkAssignMutation = useMutation({
    mutationFn: (request: BulkAssignListRequest) => postBulkAssignList(request),
    onSettled: async () => {
      await queryClient.invalidateQueries({ queryKey: ["atlas"] });
    },
  });
  const updateSingleMutation = useMutation({
    mutationFn: ({
      placeId,
      lists,
    }: {
      placeId: string;
      lists: readonly PlaceListEntryRequest[];
    }) => putPlaceLists(placeId, lists),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["atlas"] });
    },
  });
  return {
    isMutating: bulkAssignMutation.isPending || updateSingleMutation.isPending,
    isBulkAssigning: bulkAssignMutation.isPending,
    bulkAssign: (request: BulkAssignListRequest, onSettled: () => void) =>
      bulkAssignMutation.mutate(request, {
        onSuccess: (result) => {
          const verb = request.mode === "MOVE" ? "Moved" : "Added";
          notify(
            `${verb} ${result.assignedCount} place${result.assignedCount === 1 ? "" : "s"} to ${result.targetListName}`,
          );
        },
        onError: () => notify("Could not update list memberships"),
        onSettled,
      }),
    updatePlaceLists: (
      place: AtlasPlaceView,
      lists: readonly PlaceListEntryRequest[],
      onUpdated: (result: PlaceListsStateView) => void = () => undefined,
    ) =>
      updateSingleMutation.mutate(
        { placeId: place.id, lists },
        {
          onSuccess: (result) => {
            onUpdated(result);
            notify(`Updated lists for ${place.canonicalName}`);
          },
          onError: () => notify(`Could not update lists for ${place.canonicalName}`),
        },
      ),
  };
}

export function useVisitActions(notify: NotifyVisitChange) {
  const single = useUpdatePlaceVisitStatus();
  const category = useUpdatePlaceCategory();
  const bulk = useBulkUpdateVisitStatus();
  const restore = useRestoreVisitStates();
  return {
    isMutating: single.isPending || category.isPending || bulk.isPending || restore.isPending,
    isBulkUpdating: bulk.isPending,
    isRestoring: restore.isPending,
    updateCategory: (
      place: AtlasPlaceView,
      nextCategory: PlaceCategory,
      onUpdated: (result: PlaceCategoryStateView) => void = () => undefined,
    ) =>
      category.mutate(
        { placeId: place.id, category: nextCategory },
        {
          onSuccess: (result) => {
            onUpdated(result);
            notify(`Updated ${place.canonicalName} category to ${categoryLabel(nextCategory)}`);
          },
          onError: () => notify(`Could not update category for ${place.canonicalName}`),
        },
      ),
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
