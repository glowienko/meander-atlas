import { QueryClient, QueryClientProvider, useQueryClient } from "@tanstack/react-query";
import { CheckCircle2, SquareDashed } from "lucide-react";
import { useEffect, useEffectEvent, useMemo, useRef, useState } from "react";
import type {
  AccountListFacetView,
  AtlasPlaceView,
  RegionLevel,
  RegionSummaryView,
  VisitStateSnapshotView,
} from "./api/client";
import { AtlasFilterBar } from "./features/atlas/AtlasFilterBar";
import { AtlasHealthPill } from "./features/atlas/AtlasHealthPill";
import { type AtlasMapHandle, AtlasMapView, type MapViewport } from "./features/atlas/AtlasMapView";
import { MapLegend } from "./features/atlas/MapLegend";
import { OVERLAY_OFFSET_STYLE } from "./features/atlas/mapGeometry";
import { STREET_ZOOM } from "./features/atlas/mapLayers";
import { PlaceDetailCard } from "./features/atlas/PlaceDetailCard";
import { PlaceSelectionDialog } from "./features/atlas/PlaceSelectionDialog";
import { RegionDossierDrawer } from "./features/atlas/RegionDossierDrawer";
import { TravelStatsPanel } from "./features/atlas/TravelStatsPanel";
import { UndoToast } from "./features/atlas/UndoToast";
import { useAtlasHealth } from "./features/atlas/useAtlasHealth";
import {
  regionDossierQuery,
  toFilterParams,
  useAtlasPlaces,
  useAtlasStats,
  useRegionDossier,
  useRegionSummaries,
  useVisitActions,
  withCategoryState,
  withVisitState,
} from "./features/atlas/useAtlasQueries";
import {
  clearAtlasFilters,
  hasActiveFilters,
  isPlaceInViewport,
  useAtlasViewState,
} from "./features/atlas/viewState";
import { ImportStatusBadge } from "./features/ingestion/ImportStatusBadge";
import { SyncImportsButton } from "./features/ingestion/SyncImportsButton";
import { useImportActions, useImportStatus } from "./features/ingestion/useLocalImport";

interface ToastState {
  message: string;
  snapshots: readonly VisitStateSnapshotView[];
}

interface BulkDialogState {
  id: number;
  title: string;
  subtitle?: string;
  places: AtlasPlaceView[];
}

const NO_PLACES: readonly AtlasPlaceView[] = Object.freeze([]);
const NO_REGIONS: readonly RegionSummaryView[] = Object.freeze([]);
const NO_IDS: readonly string[] = Object.freeze([]);
const NO_ACCOUNT_LISTS: readonly AccountListFacetView[] = Object.freeze([]);
const NO_SNAPSHOTS: readonly VisitStateSnapshotView[] = Object.freeze([]);
const VIEWPORT_BULK_MAX_PLACES = 200;

function AtlasShell() {
  const queryClient = useQueryClient();
  const mapRef = useRef<AtlasMapHandle>(null);
  const [viewState, setViewState, offlineBasemap] = useAtlasViewState();
  const [selection, setSelection] = useState<AtlasPlaceView | null>(null);
  const [viewport, setViewport] = useState<MapViewport | null>(null);
  const [areaSelectMode, setAreaSelectMode] = useState(false);
  const [bulkDialog, setBulkDialog] = useState<BulkDialogState | null>(null);
  const [toast, setToast] = useState<ToastState | null>(null);

  const notify = (message: string, snapshots = NO_SNAPSHOTS) => setToast({ message, snapshots });
  const filterParams = useMemo(() => toFilterParams(viewState), [viewState]);
  const healthQuery = useAtlasHealth();
  const placesQuery = useAtlasPlaces(filterParams);
  const statsQuery = useAtlasStats();
  const countrySummariesQuery = useRegionSummaries("COUNTRY", undefined, filterParams);
  const dossierQuery = useRegionDossier(viewState.regionLevel, viewState.regionCode);
  const selectedCountryCode =
    viewState.regionLevel === "ADMIN1"
      ? (dossierQuery.data?.region.countryCode ?? "")
      : viewState.regionCode;
  const admin1SummariesQuery = useRegionSummaries(
    "ADMIN1",
    selectedCountryCode,
    filterParams,
    selectedCountryCode.length > 0,
  );
  const importStatusQuery = useImportStatus();
  const imports = useImportActions(notify);
  const visits = useVisitActions(notify);

  const places = placesQuery.data?.places ?? NO_PLACES;
  const dossierPlaces = dossierQuery.data?.places ?? NO_PLACES;
  const selectedPlace = useMemo(() => {
    const isSelected = (place: AtlasPlaceView) => place.id === selection?.id;
    return places.find(isSelected) ?? dossierPlaces.find(isSelected) ?? selection;
  }, [places, dossierPlaces, selection]);
  const mapPlaces = useMemo(
    () =>
      !selectedPlace || places.some((place) => place.id === selectedPlace.id)
        ? places
        : [...places, selectedPlace],
    [places, selectedPlace],
  );
  const visibleUnvisitedPlaces = useMemo(
    () =>
      viewport && viewport.zoom >= STREET_ZOOM
        ? places.filter(
            (place) => place.visitStatus !== "VISITED" && isPlaceInViewport(place, viewport),
          )
        : [],
    [places, viewport],
  );
  const highlightedPlaceIds = useMemo(
    () => bulkDialog?.places.map((place) => place.id) ?? NO_IDS,
    [bulkDialog],
  );
  const dossierOpen = viewState.regionLevel !== "" && viewState.regionCode.length > 0;
  const filtersActive = hasActiveFilters(viewState);

  const openBulkDialog = (dialog: Omit<BulkDialogState, "id">) =>
    setBulkDialog((previous) => ({ ...dialog, id: (previous?.id ?? 0) + 1 }));

  const focusPlace = (place: AtlasPlaceView) => {
    setSelection(place);
    mapRef.current?.flyTo(place, {
      statsOpen: viewState.statsOpen,
      dossierOpen,
      placeDetailOpen: true,
    });
  };

  const fitRegion = (level: RegionLevel, code: string) => {
    queryClient.fetchQuery(regionDossierQuery(level, code)).then(
      ({ region }) =>
        mapRef.current?.fitBounds(region, {
          statsOpen: viewState.statsOpen,
          dossierOpen: true,
          placeDetailOpen: false,
        }),
      () => undefined,
    );
  };

  const fitInitialRegion = useEffectEvent(() => {
    if (viewState.regionLevel !== "") {
      fitRegion(viewState.regionLevel, viewState.regionCode);
    }
  });
  useEffect(() => fitInitialRegion(), []);

  const closeRegion = () =>
    setViewState((previous) => ({ ...previous, regionLevel: "", regionCode: "" }));

  const resetView = () => {
    setSelection(null);
    setAreaSelectMode(false);
    closeRegion();
    mapRef.current?.resetView();
  };

  const selectRegion = (level: RegionLevel, code: string) => {
    setSelection(null);
    setViewState((previous) => ({ ...previous, regionLevel: level, regionCode: code }));
    fitRegion(level, code);
  };

  const selectPlaceById = (placeId: string) => {
    const place = mapPlaces.find((candidate) => candidate.id === placeId);
    if (place) {
      focusPlace(place);
    }
  };

  const clearFilters = () => setViewState((previous) => clearAtlasFilters(previous));
  const closeBulkDialog = () => setBulkDialog(null);

  return (
    <main className="relative h-full w-full" style={OVERLAY_OFFSET_STYLE}>
      <AtlasMapView
        ref={mapRef}
        projection={viewState.projection}
        basemap={offlineBasemap ? "offline" : viewState.basemap}
        places={mapPlaces}
        countrySummaries={countrySummariesQuery.data ?? NO_REGIONS}
        admin1Summaries={admin1SummariesQuery.data ?? NO_REGIONS}
        selectedCountryCode={selectedCountryCode}
        selectedRegionLevel={viewState.regionLevel}
        selectedRegionCode={viewState.regionCode}
        selectedPlaceId={selection?.id ?? null}
        highlightedPlaceIds={highlightedPlaceIds}
        areaSelectMode={areaSelectMode}
        onSelectPlace={selectPlaceById}
        onSelectRegion={selectRegion}
        onViewportChange={setViewport}
        onSelectAreaPlaces={(matched) => {
          setAreaSelectMode(false);
          openBulkDialog({
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
          onProjectionChange={(projection) =>
            setViewState((previous) => ({ ...previous, projection }))
          }
          basemap={viewState.basemap}
          onBasemapChange={(basemap) => setViewState((previous) => ({ ...previous, basemap }))}
          statsOpen={viewState.statsOpen}
          mappedCount={statsQuery.data?.totalMappedPlaces}
          onToggleStats={() =>
            setViewState((previous) => ({ ...previous, statsOpen: !previous.statsOpen }))
          }
          onResetView={resetView}
          extraActions={
            <>
              <button
                type="button"
                data-testid="select-area-button"
                aria-pressed={areaSelectMode}
                onClick={() => setAreaSelectMode((previous) => !previous)}
                className="atlas-pill aria-pressed:bg-atlas-accent-soft aria-pressed:text-atlas-accent"
              >
                <SquareDashed className="h-3.5 w-3.5" aria-hidden="true" />
                Select area
              </button>
              <SyncImportsButton
                isPending={imports.localImport.isPending}
                onClick={() => imports.localImport.mutate()}
              />
            </>
          }
        />

        <AtlasFilterBar
          viewState={viewState}
          accountLists={statsQuery.data?.accountLists ?? NO_ACCOUNT_LISTS}
          totalCount={placesQuery.data?.totalCount}
          onChange={setViewState}
          onClear={clearFilters}
        />
      </header>

      {viewState.statsOpen && (
        <TravelStatsPanel
          stats={statsQuery.data}
          isLoading={statsQuery.isLoading}
          isError={statsQuery.isError}
          onClose={() => setViewState((previous) => ({ ...previous, statsOpen: false }))}
          onFilterCategory={(category) => setViewState((previous) => ({ ...previous, category }))}
          importSlot={
            <ImportStatusBadge
              mappedCount={statsQuery.data?.totalMappedPlaces ?? 0}
              status={importStatusQuery.data}
            />
          }
        />
      )}

      {dossierOpen && (
        <RegionDossierDrawer
          dossier={dossierQuery.data}
          isLoading={dossierQuery.isLoading}
          isError={dossierQuery.isError}
          isMutating={visits.isMutating}
          activeFilterCount={filtersActive ? places.length : undefined}
          onClearFilters={clearFilters}
          onResetView={resetView}
          onClose={closeRegion}
          onSelectRegion={selectRegion}
          onSelectPlace={focusPlace}
          onToggleVisitStatus={visits.updatePlace}
          onOpenBulkVisit={(targetPlaces, title) => openBulkDialog({ title, places: targetPlaces })}
        />
      )}

      {selectedPlace && (
        <PlaceDetailCard
          place={selectedPlace}
          isMutating={visits.isMutating}
          offsetForStats={viewState.statsOpen}
          onClose={() => setSelection(null)}
          onUpdateVisitStatus={(place, status) =>
            visits.updatePlace(place, status, (result) =>
              setSelection((previous) => (previous ? withVisitState(previous, result) : previous)),
            )
          }
          onUpdateCategory={(place, nextCategory) =>
            visits.updateCategory(place, nextCategory, (result) =>
              setSelection((previous) =>
                previous ? withCategoryState(previous, result) : previous,
              ),
            )
          }
        />
      )}

      <MapLegend dossierOpen={dossierOpen} />

      {visibleUnvisitedPlaces.length > 0 &&
        visibleUnvisitedPlaces.length <= VIEWPORT_BULK_MAX_PLACES && (
          <div className="pointer-events-none fixed inset-x-0 bottom-5 z-10 flex justify-center px-4">
            <button
              type="button"
              data-testid="bulk-viewport-button"
              disabled={visits.isMutating}
              onClick={() =>
                openBulkDialog({
                  title: "Mark visible places as visited",
                  places: visibleUnvisitedPlaces,
                })
              }
              className="atlas-pill-solid pointer-events-auto border border-white/25 px-4 py-2 font-semibold shadow-lg"
            >
              <CheckCircle2 className="h-4 w-4" aria-hidden="true" />
              Mark {visibleUnvisitedPlaces.length} visible as visited
            </button>
          </div>
        )}

      {bulkDialog && (
        <PlaceSelectionDialog
          key={bulkDialog.id}
          title={bulkDialog.title}
          subtitle={bulkDialog.subtitle}
          places={bulkDialog.places}
          isSubmitting={visits.isBulkUpdating}
          onClose={closeBulkDialog}
          onMarkVisited={(ids) => visits.updatePlaces(ids, "VISITED", closeBulkDialog)}
          onMarkUnvisited={(ids) => visits.updatePlaces(ids, "UNVISITED", closeBulkDialog)}
        />
      )}

      {toast && (
        <UndoToast
          message={toast.message}
          canUndo={toast.snapshots.length > 0}
          isRestoring={visits.isRestoring}
          onUndo={() => visits.undo(toast.snapshots, () => setToast(null))}
          onDismiss={() => setToast(null)}
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
