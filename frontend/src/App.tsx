import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { CheckCircle2 } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import type {
  AtlasPlaceView,
  RegionLevel,
  VisitStateSnapshotView,
  VisitStatus,
} from "./api/client";
import { AtlasFilterBar } from "./features/atlas/AtlasFilterBar";
import { AtlasHealthPill } from "./features/atlas/AtlasHealthPill";
import {
  AtlasMapView,
  type FitBoundsTarget,
  type MapViewport,
} from "./features/atlas/AtlasMapView";
import { BulkVisitDialog } from "./features/atlas/BulkVisitDialog";
import { PlaceDetailCard } from "./features/atlas/PlaceDetailCard";
import { RegionDossierDrawer } from "./features/atlas/RegionDossierDrawer";
import { TravelStatsPanel } from "./features/atlas/TravelStatsPanel";
import { UndoToast } from "./features/atlas/UndoToast";
import { useAtlasHealth } from "./features/atlas/useAtlasHealth";
import {
  toFilterParams,
  useAtlasPlaces,
  useAtlasStats,
  useBulkUpdateVisitStatus,
  useRegionDossier,
  useRegionSummaries,
  useRestoreVisitStates,
  useUpdatePlaceVisitStatus,
} from "./features/atlas/useAtlasQueries";
import {
  type AtlasProjection,
  clearAtlasFilters,
  isPlaceInViewport,
  readAtlasViewState,
  writeAtlasViewState,
} from "./features/atlas/viewState";
import { ImportStatusBadge } from "./features/ingestion/ImportStatusBadge";
import { SyncImportsButton } from "./features/ingestion/SyncImportsButton";
import { useImportStatus, useTriggerLocalImport } from "./features/ingestion/useLocalImport";

interface UndoToastState {
  message: string;
  snapshots: VisitStateSnapshotView[];
}

interface BulkDialogState {
  title: string;
  places: AtlasPlaceView[];
}

const VIEWPORT_BULK_MIN_ZOOM = 9;
const VIEWPORT_BULK_MAX_PLACES = 200;

function AtlasShell() {
  const [viewState, setViewState] = useState(() => readAtlasViewState(window.location.search));
  const [selectedPlaceId, setSelectedPlaceId] = useState<string | null>(null);
  const [selectedPlaceFallback, setSelectedPlaceFallback] = useState<AtlasPlaceView | null>(null);
  const [viewport, setViewport] = useState<MapViewport | null>(null);
  const [bulkDialog, setBulkDialog] = useState<BulkDialogState | null>(null);
  const [undoToast, setUndoToast] = useState<UndoToastState | null>(null);

  const filterParams = useMemo(() => toFilterParams(viewState), [viewState]);

  const healthQuery = useAtlasHealth();
  const placesQuery = useAtlasPlaces(filterParams);
  const statsQuery = useAtlasStats();
  const countrySummariesQuery = useRegionSummaries("COUNTRY", undefined, filterParams);
  const dossierQuery = useRegionDossier(viewState.regionLevel, viewState.regionCode);

  const selectedCountryCode = useMemo(() => {
    if (viewState.regionLevel === "COUNTRY") {
      return viewState.regionCode;
    }
    if (viewState.regionLevel === "ADMIN1") {
      return dossierQuery.data?.region.countryCode ?? "";
    }
    return "";
  }, [viewState.regionLevel, viewState.regionCode, dossierQuery.data]);

  const admin1SummariesQuery = useRegionSummaries(
    "ADMIN1",
    selectedCountryCode,
    filterParams,
    selectedCountryCode.length > 0,
  );

  const updateVisitMutation = useUpdatePlaceVisitStatus();
  const bulkVisitMutation = useBulkUpdateVisitStatus();
  const restoreVisitMutation = useRestoreVisitStates();
  const importStatusQuery = useImportStatus();
  const localImportMutation = useTriggerLocalImport();

  useEffect(() => {
    const nextSearch = writeAtlasViewState(viewState, window.location.search);
    if (window.location.search !== nextSearch) {
      window.history.replaceState(
        null,
        "",
        `${window.location.pathname}${nextSearch}${window.location.hash}`,
      );
    }
  }, [viewState]);

  const places = placesQuery.data?.places ?? [];
  const dossierPlaces = dossierQuery.data?.places ?? [];
  const selectedPlace = useMemo(() => {
    if (!selectedPlaceId) {
      return null;
    }
    return (
      places.find((p) => p.id === selectedPlaceId) ??
      dossierPlaces.find((p) => p.id === selectedPlaceId) ??
      (selectedPlaceFallback?.id === selectedPlaceId ? selectedPlaceFallback : null)
    );
  }, [places, dossierPlaces, selectedPlaceId, selectedPlaceFallback]);

  const fitBoundsTarget: FitBoundsTarget | null = useMemo(() => {
    const region = dossierQuery.data?.region;
    if (!region) {
      return null;
    }
    return {
      key: `${region.level}:${region.code}`,
      minLng: region.minLng,
      minLat: region.minLat,
      maxLng: region.maxLng,
      maxLat: region.maxLat,
    };
  }, [dossierQuery.data]);

  const visibleUnvisitedPlaces = useMemo(() => {
    if (!viewport || viewport.zoom < VIEWPORT_BULK_MIN_ZOOM) {
      return [];
    }
    return places.filter(
      (place) => place.visitStatus !== "VISITED" && isPlaceInViewport(place, viewport),
    );
  }, [places, viewport]);

  const handleProjectionChange = (projection: AtlasProjection) => {
    setViewState((prev) => ({ ...prev, projection }));
  };

  const handleSelectRegion = useCallback((level: RegionLevel, code: string) => {
    setSelectedPlaceId(null);
    setSelectedPlaceFallback(null);
    setViewState((prev) => ({
      ...prev,
      regionLevel: level,
      regionCode: code,
    }));
  }, []);

  const handleSelectPlaceFromMap = useCallback(
    (placeId: string) => {
      setSelectedPlaceId(placeId);
      const found =
        places.find((p) => p.id === placeId) ?? dossierPlaces.find((p) => p.id === placeId) ?? null;
      setSelectedPlaceFallback(found);
    },
    [places, dossierPlaces],
  );

  const handleToggleSingleVisit = (place: AtlasPlaceView, nextStatus: VisitStatus) => {
    updateVisitMutation.mutate(
      { placeId: place.id, status: nextStatus },
      {
        onSuccess: (result) => {
          setSelectedPlaceFallback((prev) =>
            prev && prev.id === result.placeId
              ? {
                  ...prev,
                  visitStatus: result.status,
                  visitSource: result.source,
                  visitCount: result.visitCount,
                  firstVisitedAt: result.firstVisitedAt,
                  lastVisitedAt: result.lastVisitedAt,
                }
              : prev,
          );
          setUndoToast({
            message: `Marked ${place.canonicalName} as ${nextStatus === "VISITED" ? "visited" : "unvisited"}`,
            snapshots: [result.previousState],
          });
        },
        onError: () => {
          setUndoToast({
            message: `Could not update ${place.canonicalName}`,
            snapshots: [],
          });
        },
      },
    );
  };

  const handleConfirmBulkVisit = (selectedIds: string[]) => {
    bulkVisitMutation.mutate(
      { placeIds: selectedIds, targetStatus: "VISITED" },
      {
        onSuccess: (result) => {
          setBulkDialog(null);
          setUndoToast({
            message: `Marked ${result.updatedCount} place${result.updatedCount === 1 ? "" : "s"} as visited`,
            snapshots: result.previousStates,
          });
        },
        onError: () => {
          setUndoToast({
            message: "Could not update selected places",
            snapshots: [],
          });
        },
      },
    );
  };

  const handleUndo = () => {
    if (!undoToast || undoToast.snapshots.length === 0) {
      return;
    }
    restoreVisitMutation.mutate(undoToast.snapshots, {
      onSuccess: () => {
        setUndoToast(null);
      },
      onError: () => {
        setUndoToast({
          message: "Could not undo visit changes",
          snapshots: [],
        });
      },
    });
  };

  const handleSyncLocalFolder = () => {
    localImportMutation.mutate(undefined, {
      onSuccess: (result) => {
        if (result.accounts.length === 0) {
          setUndoToast({ message: "No local import accounts found", snapshots: [] });
          return;
        }
        if (result.accounts.every((account) => account.skipped)) {
          setUndoToast({ message: "Local folder already up to date", snapshots: [] });
          return;
        }
        setUndoToast({
          message: `Synced ${result.totalPlacesCreated} new, ${result.totalPlacesMerged} merged · ${result.totalQueuedResolutionJobs} awaiting coordinates`,
          snapshots: [],
        });
      },
      onError: () => {
        setUndoToast({ message: "Local folder sync failed", snapshots: [] });
      },
    });
  };

  const isMutating =
    updateVisitMutation.isPending || bulkVisitMutation.isPending || restoreVisitMutation.isPending;

  return (
    <main className="relative h-full w-full">
      <AtlasMapView
        projection={viewState.projection}
        places={places}
        countrySummaries={countrySummariesQuery.data ?? []}
        admin1Summaries={admin1SummariesQuery.data ?? []}
        selectedCountryCode={selectedCountryCode}
        selectedRegionLevel={viewState.regionLevel}
        selectedRegionCode={viewState.regionCode}
        selectedPlaceId={selectedPlaceId}
        fitBoundsTarget={fitBoundsTarget}
        onSelectPlace={handleSelectPlaceFromMap}
        onSelectRegion={handleSelectRegion}
        onViewportChange={setViewport}
      />

      <header className="pointer-events-none fixed inset-x-0 top-3 z-10 flex flex-col items-center gap-2 px-4">
        <AtlasHealthPill
          health={healthQuery.data}
          isLoading={healthQuery.isLoading}
          isError={healthQuery.isError}
          projection={viewState.projection}
          onProjectionChange={handleProjectionChange}
          statsOpen={viewState.statsOpen}
          onToggleStats={() => setViewState((prev) => ({ ...prev, statsOpen: !prev.statsOpen }))}
          extraActions={
            <SyncImportsButton
              isPending={localImportMutation.isPending}
              onClick={handleSyncLocalFolder}
            />
          }
        />

        <AtlasFilterBar
          viewState={viewState}
          accountLists={statsQuery.data?.accountLists ?? []}
          totalCount={placesQuery.data?.totalCount}
          onChange={setViewState}
          onClear={() => setViewState((prev) => clearAtlasFilters(prev))}
        />
      </header>

      {viewState.statsOpen && (
        <TravelStatsPanel
          stats={statsQuery.data}
          isLoading={statsQuery.isLoading}
          isError={statsQuery.isError}
          onClose={() => setViewState((prev) => ({ ...prev, statsOpen: false }))}
          onFilterCategory={(category) => setViewState((prev) => ({ ...prev, category }))}
          importSlot={
            <ImportStatusBadge
              mappedCount={statsQuery.data?.totalMappedPlaces ?? 0}
              status={importStatusQuery.data}
            />
          }
        />
      )}

      {viewState.regionLevel !== "" && viewState.regionCode.length > 0 && (
        <RegionDossierDrawer
          dossier={dossierQuery.data}
          isLoading={dossierQuery.isLoading}
          isError={dossierQuery.isError}
          isMutating={isMutating}
          onClose={() => setViewState((prev) => ({ ...prev, regionLevel: "", regionCode: "" }))}
          onSelectRegion={handleSelectRegion}
          onSelectPlace={(place) => {
            setSelectedPlaceId(place.id);
            setSelectedPlaceFallback(place);
          }}
          onToggleVisitStatus={handleToggleSingleVisit}
          onOpenBulkVisit={(targetPlaces, title) => setBulkDialog({ title, places: targetPlaces })}
        />
      )}

      {selectedPlace && (
        <PlaceDetailCard
          place={selectedPlace}
          isMutating={isMutating}
          offsetForStats={viewState.statsOpen}
          onClose={() => {
            setSelectedPlaceId(null);
            setSelectedPlaceFallback(null);
          }}
          onUpdateVisitStatus={handleToggleSingleVisit}
        />
      )}

      {visibleUnvisitedPlaces.length > 0 &&
        visibleUnvisitedPlaces.length <= VIEWPORT_BULK_MAX_PLACES && (
          <div className="pointer-events-none fixed inset-x-0 bottom-5 z-10 flex justify-center px-4">
            <button
              type="button"
              data-testid="bulk-viewport-button"
              disabled={isMutating}
              onClick={() =>
                setBulkDialog({
                  title: "Mark visible places as visited",
                  places: visibleUnvisitedPlaces,
                })
              }
              className="pointer-events-auto inline-flex cursor-pointer items-center gap-1.5 rounded-full border px-4 py-2 text-[13px] font-semibold text-white shadow-lg transition-opacity hover:opacity-95 disabled:opacity-50"
              style={{
                backgroundColor: "var(--atlas-accent)",
                borderColor: "rgba(255, 255, 255, 0.25)",
              }}
            >
              <CheckCircle2 className="h-4 w-4" aria-hidden="true" />
              Mark {visibleUnvisitedPlaces.length} visible as visited
            </button>
          </div>
        )}

      {bulkDialog && (
        <BulkVisitDialog
          title={bulkDialog.title}
          places={bulkDialog.places}
          isSubmitting={bulkVisitMutation.isPending}
          onClose={() => setBulkDialog(null)}
          onConfirm={handleConfirmBulkVisit}
        />
      )}

      {undoToast && (
        <UndoToast
          message={undoToast.message}
          canUndo={undoToast.snapshots.length > 0}
          isRestoring={restoreVisitMutation.isPending}
          onUndo={handleUndo}
          onDismiss={() => setUndoToast(null)}
        />
      )}
    </main>
  );
}

export function App() {
  const [queryClient] = useState(() => new QueryClient());

  return (
    <QueryClientProvider client={queryClient}>
      <AtlasShell />
    </QueryClientProvider>
  );
}
