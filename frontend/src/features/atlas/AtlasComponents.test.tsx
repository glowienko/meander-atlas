import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { AtlasPlaceView, AtlasStatsView, RegionDossierView } from "../../api/client";
import { AtlasFilterBar } from "./AtlasFilterBar";
import { ListAssignActionBar } from "./ListAssignActionBar";
import { MapLegend } from "./MapLegend";
import { formatVisitDates, PlaceDetailCard } from "./PlaceDetailCard";
import { PlaceSelectionDialog } from "./PlaceSelectionDialog";
import { RegionDossierDrawer } from "./RegionDossierDrawer";
import { TravelStatsPanel } from "./TravelStatsPanel";
import { UndoToast } from "./UndoToast";
import { withCategoryState, withListsState, withVisitState } from "./useAtlasQueries";
import { defaultAtlasViewState } from "./viewState";

const SAMPLE_PLACE: AtlasPlaceView = {
  id: "11111111-1111-1111-1111-111111111111",
  canonicalName: "Wawel Royal Castle",
  lat: 50.054,
  lng: 19.935,
  category: "LANDMARK",
  subcategory: "castle",
  categorySource: "HEURISTIC",
  coordSource: "TAKEOUT",
  derivedRole: "MUST_SEE",
  visitStatus: "UNVISITED",
  visitCount: 0,
  countryCode: "PL",
  admin1Code: "PL-12",
  admin1Name: "Małopolskie",
  cityName: "Krakow",
  memberships: [
    {
      accountLabel: "personal",
      listName: "Starred",
      listKind: "STARRED",
      note: "Must visit hill",
      sourceUrl: "https://maps.google.com/?cid=123",
    },
  ],
};

const SECOND_PLACE: AtlasPlaceView = {
  ...SAMPLE_PLACE,
  id: "22222222-2222-2222-2222-222222222222",
  canonicalName: "Hala Targowa",
  category: "RESTAURANT",
  subcategory: "market",
  derivedRole: "FAVORITE",
};

function detailCardElement(
  place: AtlasPlaceView,
  overrides: Partial<Parameters<typeof PlaceDetailCard>[0]> = {},
) {
  return (
    <PlaceDetailCard
      place={place}
      accountLists={[]}
      isMutating={false}
      onClose={vi.fn()}
      onUpdateVisitStatus={vi.fn()}
      onUpdateCategory={vi.fn()}
      onUpdateLists={vi.fn()}
      {...overrides}
    />
  );
}

describe("Living Atlas components", () => {
  it("renders RegionDossierDrawer completion ring and triggers bulk visit", () => {
    const onOpenBulkVisit = vi.fn();
    const onClearFilters = vi.fn();
    const dossier: RegionDossierView = {
      region: {
        id: "33333333-3333-3333-3333-333333333333",
        level: "COUNTRY",
        code: "PL",
        countryCode: "PL",
        name: "Poland",
        totalPlaces: 2,
        visitedPlaces: 1,
        unvisitedPlaces: 1,
        needsTriagePlaces: 0,
        completionRatio: 0.5,
        minLng: 14,
        minLat: 49,
        maxLng: 24,
        maxLat: 55,
      },
      childRegions: [],
      places: [SAMPLE_PLACE],
    };

    render(
      <RegionDossierDrawer
        dossier={dossier}
        isLoading={false}
        isError={false}
        isMutating={false}
        activeFilterCount={1}
        onClearFilters={onClearFilters}
        onClose={vi.fn()}
        onSelectRegion={vi.fn()}
        onSelectPlace={vi.fn()}
        onToggleVisitStatus={vi.fn()}
        onOpenBulkVisit={onOpenBulkVisit}
        onResetView={vi.fn()}
      />,
    );

    expect(screen.getByTestId("region-completion-ring")).toHaveTextContent("50%");
    expect(screen.getByText("1 of 2 visited (50%)")).toBeInTheDocument();
    expect(screen.getByTestId("dossier-filter-notice")).toHaveTextContent(
      "Map filter active (1 pin shown)",
    );
    fireEvent.click(screen.getByRole("button", { name: "Show all" }));
    expect(onClearFilters).toHaveBeenCalledTimes(1);
    fireEvent.click(screen.getByTestId("dossier-bulk-visit-button"));
    expect(onOpenBulkVisit).toHaveBeenCalledWith([SAMPLE_PLACE], "Mark Poland places visited");
  });

  it("sanitizes external URLs in PlaceDetailCard and triggers status, category, and list updates", () => {
    const onUpdateVisitStatus = vi.fn();
    const onUpdateCategory = vi.fn();
    const onUpdateLists = vi.fn();
    const accountLists = [
      {
        accountLabel: "personal",
        listName: "Starred",
        listKind: "STARRED" as const,
        placeCount: 1,
      },
      { accountLabel: "personal", listName: "Food", listKind: "CUSTOM" as const, placeCount: 2 },
    ];
    const { rerender } = render(
      <PlaceDetailCard
        place={SAMPLE_PLACE}
        accountLists={accountLists}
        isMutating={false}
        onClose={vi.fn()}
        onUpdateVisitStatus={onUpdateVisitStatus}
        onUpdateCategory={onUpdateCategory}
        onUpdateLists={onUpdateLists}
      />,
    );

    const card = screen.getByTestId("place-detail-card");
    expect(card).toHaveTextContent("Wawel Royal Castle");
    expect(card).toHaveTextContent("Bucket list");
    expect(card).toHaveTextContent("castle");
    const categorySelect = screen.getByTestId("place-category-select");
    expect(categorySelect).toHaveValue("LANDMARK");
    fireEvent.change(categorySelect, { target: { value: "GROCERIES" } });
    expect(onUpdateCategory).toHaveBeenCalledWith(SAMPLE_PLACE, "GROCERIES");

    fireEvent.change(screen.getByTestId("place-list-new-name"), {
      target: { value: "Weekend Krakow" },
    });
    fireEvent.click(screen.getByTestId("place-list-add-button"));
    expect(onUpdateLists).toHaveBeenCalledWith(SAMPLE_PLACE, [
      { listName: "Starred", listKind: "STARRED", accountLabel: "personal" },
      { listName: "Weekend Krakow", listKind: "CUSTOM" },
    ]);

    fireEvent.click(screen.getByTestId("remove-place-list-button"));
    expect(onUpdateLists).toHaveBeenCalledWith(SAMPLE_PLACE, []);

    const link = screen.getByRole("link", { name: /Google Maps/i });
    expect(link).toHaveAttribute("href", "https://maps.google.com/?cid=123");

    fireEvent.click(screen.getByRole("button", { name: "Mark Visited" }));
    expect(onUpdateVisitStatus).toHaveBeenCalledWith(SAMPLE_PLACE, "VISITED");

    const unsafePlace: AtlasPlaceView = {
      ...SAMPLE_PLACE,
      visitStatus: "VISITED",
      visitCount: 2,
      category: "GROCERIES",
      categorySource: "MANUAL",
      memberships: [
        {
          accountLabel: "personal",
          listName: "Starred",
          listKind: "STARRED",
          sourceUrl: "javascript:alert(1)",
        },
      ],
    };
    rerender(
      <PlaceDetailCard
        place={unsafePlace}
        accountLists={accountLists}
        isMutating={false}
        onClose={vi.fn()}
        onUpdateVisitStatus={onUpdateVisitStatus}
        onUpdateCategory={onUpdateCategory}
        onUpdateLists={onUpdateLists}
      />,
    );

    expect(screen.queryByRole("link", { name: /Google Maps/i })).not.toBeInTheDocument();
    expect(screen.getByTestId("place-detail-card")).toHaveTextContent("2 visits");
    expect(screen.getByTestId("place-category-manual-badge")).toHaveTextContent("Manual");
  });

  it("wires ListAssignActionBar in PlaceSelectionDialog secondaryActions for create and move", () => {
    const onAssign = vi.fn();
    const accountLists = [
      {
        accountLabel: "personal",
        listName: "Want to go",
        listKind: "WANT_TO_GO" as const,
        placeCount: 2,
      },
    ];
    render(
      <PlaceSelectionDialog
        title="Selected area (2 places)"
        places={[SAMPLE_PLACE, SECOND_PLACE]}
        isSubmitting={false}
        onClose={vi.fn()}
        onMarkVisited={vi.fn()}
        onMarkUnvisited={vi.fn()}
        secondaryActions={(selectedPlaceIds) => (
          <ListAssignActionBar
            selectedPlaceIds={selectedPlaceIds}
            accountLists={accountLists}
            defaultSourceList="Want to go"
            isSubmitting={false}
            onAssign={onAssign}
          />
        )}
      />,
    );

    fireEvent.click(screen.getByTestId("bulk-list-mode-move"));
    fireEvent.change(screen.getByTestId("bulk-list-new-name"), {
      target: { value: "Summer Trip" },
    });
    fireEvent.click(screen.getByTestId("bulk-list-apply-button"));

    expect(onAssign).toHaveBeenCalledWith({
      placeIds: [SAMPLE_PLACE.id, SECOND_PLACE.id],
      targetListName: "Summer Trip",
      listKind: "CUSTOM",
      mode: "MOVE",
      sourceListName: "Want to go",
      accountLabel: undefined,
    });
  });

  it("allows unchecking single places in PlaceSelectionDialog and submits remaining IDs", () => {
    const onConfirm = vi.fn();
    render(
      <PlaceSelectionDialog
        title="Mark Krakow places visited"
        places={[SAMPLE_PLACE, SECOND_PLACE]}
        isSubmitting={false}
        onClose={vi.fn()}
        onMarkVisited={onConfirm}
        onMarkUnvisited={vi.fn()}
      />,
    );

    const checkboxes = screen.getAllByRole("checkbox");
    expect(checkboxes).toHaveLength(2);
    const secondCheckbox = checkboxes[1];
    expect(secondCheckbox).toBeDefined();
    if (secondCheckbox) {
      fireEvent.click(secondCheckbox);
    }

    fireEvent.click(screen.getByTestId("confirm-bulk-visit-button"));
    expect(onConfirm).toHaveBeenCalledWith([SAMPLE_PLACE.id]);
  });

  it("renders TravelStatsPanel, AtlasFilterBar, and UndoToast callbacks", () => {
    const onFilterCategory = vi.fn();
    const onUndo = vi.fn();
    const onFilterChange = vi.fn();
    const stats: AtlasStatsView = {
      totalMappedPlaces: 12,
      visitedPlaces: 5,
      unvisitedPlaces: 6,
      needsTriagePlaces: 1,
      visitedCountries: 2,
      totalSavedCountries: 4,
      visitedAdmin1Regions: 3,
      totalSavedAdmin1Regions: 7,
      categories: [{ category: "MOUNTAIN", totalCount: 4, visitedCount: 2 }],
      topSubcategories: [
        { subcategory: "peak", category: "MOUNTAIN", totalCount: 4, visitedCount: 2 },
      ],
      accountLists: [
        { accountLabel: "personal", listName: "Starred", listKind: "STARRED", placeCount: 12 },
      ],
    };

    render(
      <>
        <AtlasFilterBar
          viewState={defaultAtlasViewState()}
          accountLists={stats.accountLists}
          totalCount={12}
          onChange={onFilterChange}
          onClear={vi.fn()}
        />
        <TravelStatsPanel
          stats={stats}
          isLoading={false}
          isError={false}
          onClose={vi.fn()}
          onFilterCategory={onFilterCategory}
        />
        <UndoToast
          message="Marked 1 place as visited"
          canUndo={true}
          isRestoring={false}
          onUndo={onUndo}
          onDismiss={vi.fn()}
        />
      </>,
    );

    expect(screen.getByTestId("places-count-badge")).toHaveTextContent("12 pins");
    fireEvent.change(screen.getByTestId("filter-status"), { target: { value: "VISITED" } });
    expect(onFilterChange).toHaveBeenCalledWith(
      expect.objectContaining({ visitStatus: "VISITED" }),
    );

    fireEvent.click(screen.getByRole("button", { name: /Mountains & Peaks/i }));
    expect(onFilterCategory).toHaveBeenCalledWith("MOUNTAIN");

    fireEvent.click(screen.getByTestId("atlas-undo-button"));
    expect(onUndo).toHaveBeenCalledTimes(1);
  });

  it("formats and renders visit dates in PlaceDetailCard when valid", () => {
    expect(formatVisitDates(undefined, undefined)).toBeNull();
    expect(formatVisitDates("not-a-date", "")).toBeNull();
    expect(formatVisitDates("2025-10-12T10:00:00Z", undefined)).toBe("Visited Oct 2025");
    expect(formatVisitDates("2025-10-01T00:00:00Z", "2025-10-20T00:00:00Z")).toBe(
      "Visited Oct 2025",
    );
    expect(formatVisitDates("2024-10-05T00:00:00Z", "2026-03-14T00:00:00Z")).toBe(
      "First Oct 2024 · Last Mar 2026",
    );

    const visitedPlace: AtlasPlaceView = {
      ...SAMPLE_PLACE,
      visitStatus: "VISITED",
      visitCount: 2,
      firstVisitedAt: "2024-10-05T00:00:00Z",
      lastVisitedAt: "2026-03-14T00:00:00Z",
    };

    const { rerender } = render(detailCardElement(visitedPlace));
    expect(screen.getByTestId("place-visit-dates")).toHaveTextContent(
      "First Oct 2024 · Last Mar 2026",
    );

    rerender(detailCardElement(SAMPLE_PLACE));
    expect(screen.queryByTestId("place-visit-dates")).not.toBeInTheDocument();
  });

  it("renders photoUrl banner, Wikimedia attribution, image fallback, and approximate location badge in PlaceDetailCard", () => {
    const approxWithPhoto: AtlasPlaceView = {
      ...SAMPLE_PLACE,
      coordSource: "TAKEOUT_S2_APPROX",
      photoUrl: "https://upload.wikimedia.org/wikipedia/commons/thumb/wawel.jpg",
    };

    const { rerender } = render(detailCardElement(approxWithPhoto));

    const banner = screen.getByTestId("place-photo-banner");
    expect(banner).toHaveTextContent("Wikimedia");
    const img = screen.getByRole("img", { name: "Wawel Royal Castle" });
    expect(img).toHaveAttribute(
      "src",
      "https://upload.wikimedia.org/wikipedia/commons/thumb/wawel.jpg",
    );
    expect(screen.getByTestId("place-approx-location-badge")).toHaveTextContent(
      "Approximate location",
    );

    fireEvent.error(img);
    expect(screen.queryByTestId("place-photo-banner")).not.toBeInTheDocument();

    const upgradedOpenCatalog: AtlasPlaceView = {
      ...SAMPLE_PLACE,
      coordSource: "OPEN_CATALOG",
      photoUrl: "javascript:alert(1)",
    };
    rerender(detailCardElement(upgradedOpenCatalog));

    expect(screen.queryByTestId("place-approx-location-badge")).not.toBeInTheDocument();
    expect(screen.queryByTestId("place-photo-banner")).not.toBeInTheDocument();
  });

  it("toggles MapLegend popover and shifts left when dossierOpen is true", () => {
    const { container, rerender } = render(<MapLegend dossierOpen={false} />);

    expect(screen.queryByTestId("map-legend-popover")).not.toBeInTheDocument();
    const toggle = screen.getByTestId("map-legend-toggle");
    expect(toggle).toHaveAttribute("aria-expanded", "false");
    expect(container.firstElementChild).toHaveClass("right-4");
    expect(container.firstElementChild).not.toHaveClass("md:right-(--atlas-dossier-offset)");

    fireEvent.click(toggle);
    expect(toggle).toHaveAttribute("aria-expanded", "true");
    const popover = screen.getByTestId("map-legend-popover");
    expect(popover).toHaveTextContent("Visited (darker = higher %)");
    expect(popover).toHaveTextContent("Must-See / Preferred Hotel");

    rerender(<MapLegend dossierOpen={true} />);
    expect(container.firstElementChild).toHaveClass("md:right-(--atlas-dossier-offset)");

    fireEvent.click(screen.getByRole("button", { name: "Close map legend" }));
    expect(screen.queryByTestId("map-legend-popover")).not.toBeInTheDocument();
  });

  it("supports visited and unvisited actions, filter toggling, and disable guards in PlaceSelectionDialog", () => {
    const visitedPlace: AtlasPlaceView = {
      ...SECOND_PLACE,
      visitStatus: "VISITED",
      visitCount: 1,
    };
    const triagePlace: AtlasPlaceView = {
      ...SAMPLE_PLACE,
      id: "44444444-4444-4444-4444-444444444444",
      canonicalName: "Kazimierz Cafe",
      visitStatus: "NEEDS_TRIAGE",
    };

    const onMarkVisited = vi.fn();
    const onMarkUnvisited = vi.fn();

    render(
      <PlaceSelectionDialog
        title="Selected area (3 places)"
        places={[SAMPLE_PLACE, visitedPlace, triagePlace]}
        isSubmitting={false}
        onClose={vi.fn()}
        onMarkVisited={onMarkVisited}
        onMarkUnvisited={onMarkUnvisited}
      />,
    );

    const visitBtn = screen.getByTestId("confirm-bulk-visit-button");
    const unvisitBtn = screen.getByTestId("confirm-bulk-unvisited-button");
    expect(visitBtn).toHaveTextContent("Mark 2 as visited");
    expect(unvisitBtn).toHaveTextContent("Mark 1 unvisited");

    fireEvent.click(visitBtn);
    expect(onMarkVisited).toHaveBeenCalledWith([SAMPLE_PLACE.id, triagePlace.id]);

    fireEvent.click(unvisitBtn);
    expect(onMarkUnvisited).toHaveBeenCalledWith([visitedPlace.id]);

    fireEvent.change(screen.getByPlaceholderText("Filter checklist..."), {
      target: { value: "Hala" },
    });
    expect(screen.getAllByRole("checkbox")).toHaveLength(1);
    fireEvent.click(screen.getByRole("button", { name: "Uncheck all" }));
    expect(unvisitBtn).toBeDisabled();
    expect(visitBtn).toBeEnabled();

    fireEvent.change(screen.getByPlaceholderText("Filter checklist..."), {
      target: { value: "" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Check all" }));
    fireEvent.click(screen.getByRole("button", { name: "Uncheck all" }));
    expect(visitBtn).toBeDisabled();
    expect(unvisitBtn).toBeDisabled();
  });

  it("displays batch note in PlaceSelectionDialog when more than 500 places are selected", () => {
    const manyPlaces: AtlasPlaceView[] = Array.from({ length: 501 }, (_, index) => ({
      ...SAMPLE_PLACE,
      id: `00000000-0000-0000-0000-${String(index).padStart(12, "0")}`,
      canonicalName: `Place ${index}`,
    }));

    render(
      <PlaceSelectionDialog
        title="Large selection"
        places={manyPlaces}
        isSubmitting={false}
        onClose={vi.fn()}
        onMarkVisited={vi.fn()}
        onMarkUnvisited={vi.fn()}
      />,
    );

    expect(screen.getByTestId("bulk-visit-dialog")).toHaveTextContent("Sent in batches of 500");
  });
});

describe("withVisitState", () => {
  it("applies a visit result to the matching selected place only", () => {
    const result = {
      placeId: SAMPLE_PLACE.id,
      status: "VISITED" as const,
      source: "MANUAL_SINGLE" as const,
      visitCount: 1,
      firstVisitedAt: "2026-11-27T10:00:00Z",
      lastVisitedAt: "2026-11-27T10:00:00Z",
      previousState: { placeId: SAMPLE_PLACE.id, status: "UNVISITED" as const, visitCount: 0 },
    };

    expect(withVisitState(SAMPLE_PLACE, result)).toMatchObject({
      id: SAMPLE_PLACE.id,
      visitStatus: "VISITED",
      visitCount: 1,
      lastVisitedAt: "2026-11-27T10:00:00Z",
    });
    expect(withVisitState(SECOND_PLACE, result)).toBe(SECOND_PLACE);
  });

  it("applies a category result to the matching selected place only", () => {
    const result = {
      placeId: SAMPLE_PLACE.id,
      category: "GROCERIES" as const,
      subcategory: "supermarket",
      categorySource: "MANUAL" as const,
    };

    expect(withCategoryState(SAMPLE_PLACE, result)).toMatchObject({
      id: SAMPLE_PLACE.id,
      category: "GROCERIES",
      subcategory: "supermarket",
      categorySource: "MANUAL",
    });
    expect(withCategoryState(SECOND_PLACE, result)).toBe(SECOND_PLACE);
  });

  it("applies a lists result to the matching selected place only", () => {
    const result = {
      placeId: SAMPLE_PLACE.id,
      derivedRole: "FAVORITE" as const,
      memberships: [
        { accountLabel: "personal", listName: "Summer", listKind: "FAVORITE" as const },
      ],
    };

    expect(withListsState(SAMPLE_PLACE, result)).toMatchObject({
      id: SAMPLE_PLACE.id,
      derivedRole: "FAVORITE",
      memberships: result.memberships,
    });
    expect(withListsState(SECOND_PLACE, result)).toBe(SECOND_PLACE);
  });
});
