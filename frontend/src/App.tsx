import { QueryClient, QueryClientProvider, useQueryClient } from "@tanstack/react-query";
import { CheckCircle2, SquareDashed } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
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
import { MapLegend } from "./features/atlas/MapLegend";
import { computeMapPadding, type FocusPlaceTarget } from "./features/atlas/mapGeometry";
import { PlaceDetailCard } from "./features/atlas/PlaceDetailCard";
import { PlaceSelectionDialog } from "./features/atlas/PlaceSelectionDialog";
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
  type AtlasBasemap,
  type AtlasProjection,
  clearAtlasFilters,
  isPlaceInViewport,
  readAtlasViewState,
  writeAtlasViewState,
} from "./features/atlas/viewState";
import { ImportStatusBadge } from "./features/ingestion/ImportStatusBadge";
import { SyncImportsButton } from "./features/ingestion/SyncImportsButton";
import {
  useImportStatus,
  useMaterializeOffline,
  useRunEnrichment,
  useTriggerLocalImport,
} from "./features/ingestion/useLocalImport";

interface UndoToastState {
  message: string;
  snapshots: VisitStateSnapshotView[];
}

interface BulkDialogState {
  title: string;
  subtitle?: string;
  places: AtlasPlaceView[];
}

const VIEWPORT_BULK_MIN_ZOOM = 9;
const VIEWPORT_BULK_MAX_PLACES = 200;

function AtlasShell() {
  const [viewState, setViewState] = useState(() => readAtlasViewState(window.location.search));
  const [selectedPlaceId, setSelectedPlaceId] = useState<string | null>(null);
  const [selectedPlaceFallback, setSelectedPlaceFallback] = useState<AtlasPlaceView | null>(null);
  const [focusPlaceTarget, setFocusPlaceTarget] = useState<FocusPlaceTarget | null>(null);
  const [resetViewSeq, setResetViewSeq] = useState(0);
  const [viewport, setViewport] = useState<MapViewport | null>(null);
  const [areaSelectMode, setAreaSelectMode] = useState(false);
  const [bulkDialog, setBulkDialog] = useState<BulkDialogState | null>(null);
  const [undoToast, setUndoToast] = useState<UndoToastState | null>(null);
  const [windowSize, setWindowSize] = useState(() => ({
    width: window.innerWidth,
    height: window.innerHeight,
  }));
  const focusSeqRef = useRef(0);

  useEffect(() => {
    const handleResize = () => {
      setWindowSize({ width: window.innerWidth, height: window.innerHeight });
    };
    window.addEventListener("resize", handleResize);
    return () => {
      window.removeEventListener("resize", handleResize);
    };
  }, []);

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

  const queryClient = useQueryClient();
  const updateVisitMutation = useUpdatePlaceVisitStatus();
  const bulkVisitMutation = useBulkUpdateVisitStatus();
  const restoreVisitMutation = useRestoreVisitStates();
  const importStatusQuery = useImportStatus();
  const localImportMutation = useTriggerLocalImport();
  const materializeOfflineMutation = useMaterializeOffline();
  const runEnrichmentMutation = useRunEnrichment();
  const prevEnrichmentRef = useRef<{ running: boolean; enriched: number }>({
    running: false,
    enriched: 0,
  });

  useEffect(() => {
    const status = importStatusQuery.data;
    if (!status) {
      return;
    }
    const prev = prevEnrichmentRef.current;
    const finishedRun = prev.running && !status.enrichmentRunning;
    const progressedDuringRun =
      status.enrichmentRunning && status.enrichedPlaceCount > prev.enriched;
    prevEnrichmentRef.current = {
      running: status.enrichmentRunning,
      enriched: status.enrichedPlaceCount,
    };
    if (finishedRun || progressedDuringRun) {
      void queryClient.invalidateQueries({ queryKey: ["atlas"] });
    }
  }, [importStatusQuery.data, queryClient]);

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

  const mapPlaces = useMemo(() => {
    if (!selectedPlace || places.some((p) => p.id === selectedPlace.id)) {
      return places;
    }
    return [...places, selectedPlace];
  }, [places, selectedPlace]);

  const hasActiveFilters = Boolean(
    viewState.q ||
      viewState.visitStatus ||
      viewState.derivedRole ||
      viewState.category ||
      viewState.account ||
      viewState.list,
  );

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

  const dossierOpen = viewState.regionLevel !== "" && viewState.regionCode.length > 0;

  const mapPadding = useMemo(
    () =>
      computeMapPadding({
        statsOpen: viewState.statsOpen,
        dossierOpen,
        placeDetailOpen: selectedPlace !== null,
        viewportWidth: windowSize.width,
        viewportHeight: windowSize.height,
      }),
    [viewState.statsOpen, dossierOpen, selectedPlace, windowSize.width, windowSize.height],
  );

  const highlightedPlaceIds = useMemo(
    () => bulkDialog?.places.map((p) => p.id) ?? [],
    [bulkDialog],
  );

  const focusPlace = useCallback((place: { id: string; lng: number; lat: number }) => {
    focusSeqRef.current += 1;
    setFocusPlaceTarget({
      key: `${place.id}:${focusSeqRef.current}`,
      lng: place.lng,
      lat: place.lat,
    });
  }, []);

  const handleProjectionChange = (projection: AtlasProjection) => {
    setViewState((prev) => ({ ...prev, projection }));
  };

  const handleBasemapChange = (basemap: AtlasBasemap) => {
    setViewState((prev) => ({ ...prev, basemap }));
  };

  const handleResetView = useCallback(() => {
    setSelectedPlaceId(null);
    setSelectedPlaceFallback(null);
    setFocusPlaceTarget(null);
    setAreaSelectMode(false);
    setViewState((prev) => ({
      ...prev,
      regionLevel: "",
      regionCode: "",
    }));
    setResetViewSeq((seq) => seq + 1);
  }, []);

  const handleSelectRegion = useCallback((level: RegionLevel, code: string) => {
    setSelectedPlaceId(null);
    setSelectedPlaceFallback(null);
    setFocusPlaceTarget(null);
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
      if (found) {
        focusPlace(found);
      }
    },
    [places, dossierPlaces, focusPlace],
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

  const handleBulkStatusUpdate = (selectedIds: string[], targetStatus: "VISITED" | "UNVISITED") => {
    bulkVisitMutation.mutate(
      { placeIds: selectedIds, targetStatus },
      {
        onSuccess: (result) => {
          setBulkDialog(null);
          setUndoToast({
            message: `Marked ${result.updatedCount} place${result.updatedCount === 1 ? "" : "s"} as ${targetStatus === "VISITED" ? "visited" : "unvisited"}`,
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

  const handleMaterializeOffline = () => {
    materializeOfflineMutation.mutate(undefined, {
      onSuccess: (result) => {
        setUndoToast({
          message: `Materialized ${result.materializedCount} places (${result.exactFromTitleCount} exact, ${result.approxFromS2Count} approx, ${result.mergedByCidCount} merged)`,
          snapshots: [],
        });
      },
      onError: () => {
        setUndoToast({ message: "Offline materialization failed", snapshots: [] });
      },
    });
  };

  const handleRunEnrichment = (options: { limit: number; background: boolean }) => {
    runEnrichmentMutation.mutate(options, {
      onSuccess: (result) => {
        if (!result.enabled) {
          setUndoToast({
            message: "Open-catalog enrichment is disabled on the server",
            snapshots: [],
          });
          return;
        }
        if (result.alreadyRunning) {
          setUndoToast({
            message: "Enrichment is already running in the background",
            snapshots: [],
          });
          return;
        }
        if (result.startedInBackground) {
          setUndoToast({
            message: `Background enrichment started (${result.remainingPendingCount} remaining)`,
            snapshots: [],
          });
          return;
        }
        setUndoToast({
          message: `Enriched ${result.matchedCount}/${result.claimedCount} (${result.upgradedCoordsCount} coords, ${result.upgradedCategoryCount} categories, ${result.photosAddedCount} photos)`,
          snapshots: [],
        });
      },
      onError: () => {
        setUndoToast({ message: "Enrichment request failed", snapshots: [] });
      },
    });
  };

  const isMutating =
    updateVisitMutation.isPending || bulkVisitMutation.isPending || restoreVisitMutation.isPending;

  return (
    <main className="relative h-full w-full">
      <AtlasMapView
        projection={viewState.projection}
        basemap={viewState.basemap}
        places={mapPlaces}
        countrySummaries={countrySummariesQuery.data ?? []}
        admin1Summaries={admin1SummariesQuery.data ?? []}
        selectedCountryCode={selectedCountryCode}
        selectedRegionLevel={viewState.regionLevel}
        selectedRegionCode={viewState.regionCode}
        selectedPlaceId={selectedPlaceId}
        highlightedPlaceIds={highlightedPlaceIds}
        fitBoundsTarget={fitBoundsTarget}
        focusPlaceTarget={focusPlaceTarget}
        resetViewSeq={resetViewSeq}
        mapPadding={mapPadding}
        areaSelectMode={areaSelectMode}
        onSelectPlace={handleSelectPlaceFromMap}
        onSelectRegion={handleSelectRegion}
        onViewportChange={setViewport}
        onSelectAreaPlaces={(matched) => {
          setAreaSelectMode(false);
          setBulkDialog({
            title: `Selected area (${matched.length} place${matched.length === 1 ? "" : "s"})`,
            subtitle: "Review places inside the drawn area.",
            places: matched,
          });
        }}
      />

      <header className="pointer-events-none fixed inset-x-0 top-3 z-10 flex flex-col items-center gap-2 px-4">
        <AtlasHealthPill
          health={healthQuery.data}
          isLoading={healthQuery.isLoading}
          isError={healthQuery.isError}
          projection={viewState.projection}
          onProjectionChange={handleProjectionChange}
          basemap={viewState.basemap}
          onBasemapChange={handleBasemapChange}
          statsOpen={viewState.statsOpen}
          onToggleStats={() => setViewState((prev) => ({ ...prev, statsOpen: !prev.statsOpen }))}
          onResetView={handleResetView}
          extraActions={
            <>
              <button
                type="button"
                data-testid="select-area-button"
                aria-pressed={areaSelectMode}
                onClick={() => setAreaSelectMode((prev) => !prev)}
                className="inline-flex cursor-pointer items-center gap-1.5 rounded-full border px-3 py-1 text-[13px] font-medium transition-colors"
                style={{
                  borderColor: "var(--atlas-border)",
                  backgroundColor: areaSelectMode ? "var(--atlas-accent-soft)" : "transparent",
                  color: areaSelectMode ? "var(--atlas-accent)" : "var(--atlas-ink)",
                }}
              >
                <SquareDashed className="h-3.5 w-3.5" aria-hidden="true" />
                Select area
              </button>
              <SyncImportsButton
                isPending={localImportMutation.isPending}
                onClick={handleSyncLocalFolder}
              />
            </>
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
              isMaterializing={materializeOfflineMutation.isPending}
              isEnriching={runEnrichmentMutation.isPending}
              onMaterializeOffline={handleMaterializeOffline}
              onRunEnrichment={handleRunEnrichment}
            />
          }
        />
      )}

      {dossierOpen && (
        <RegionDossierDrawer
          dossier={dossierQuery.data}
          isLoading={dossierQuery.isLoading}
          isError={dossierQuery.isError}
          isMutating={isMutating}
          activeFilterCount={hasActiveFilters ? places.length : undefined}
          onClearFilters={
            hasActiveFilters ? () => setViewState((prev) => clearAtlasFilters(prev)) : undefined
          }
          onResetView={handleResetView}
          onClose={() => setViewState((prev) => ({ ...prev, regionLevel: "", regionCode: "" }))}
          onSelectRegion={handleSelectRegion}
          onSelectPlace={(place) => {
            setSelectedPlaceId(place.id);
            setSelectedPlaceFallback(place);
            focusPlace(place);
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

      <MapLegend dossierOpen={dossierOpen} />

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
        <PlaceSelectionDialog
          key={`${bulkDialog.title}:${bulkDialog.places.length}`}
          title={bulkDialog.title}
          subtitle={bulkDialog.subtitle}
          places={bulkDialog.places}
          isSubmitting={bulkVisitMutation.isPending}
          onClose={() => setBulkDialog(null)}
          onMarkVisited={(ids) => handleBulkStatusUpdate(ids, "VISITED")}
          onMarkUnvisited={(ids) => handleBulkStatusUpdate(ids, "UNVISITED")}
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
