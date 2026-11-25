import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { AtlasPlaceView, AtlasStatsView, RegionDossierView } from "../../api/client";
import { AtlasFilterBar } from "./AtlasFilterBar";
import { BulkVisitDialog } from "./BulkVisitDialog";
import { RegionDossierDrawer } from "./RegionDossierDrawer";
import { TravelStatsPanel } from "./TravelStatsPanel";
import { UndoToast } from "./UndoToast";
import { defaultAtlasViewState } from "./viewState";

const SAMPLE_PLACE: AtlasPlaceView = {
  id: "11111111-1111-1111-1111-111111111111",
  canonicalName: "Wawel Royal Castle",
  lat: 50.054,
  lng: 19.935,
  category: "LANDMARK",
  subcategory: "castle",
  categorySource: "HEURISTIC",
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

describe("Living Atlas components", () => {
  it("renders RegionDossierDrawer completion ring and triggers bulk visit", () => {
    const onOpenBulkVisit = vi.fn();
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
        onClose={vi.fn()}
        onSelectRegion={vi.fn()}
        onSelectPlace={vi.fn()}
        onToggleVisitStatus={vi.fn()}
        onOpenBulkVisit={onOpenBulkVisit}
      />,
    );

    expect(screen.getByTestId("region-completion-ring")).toHaveTextContent("50%");
    expect(screen.getByText("1 of 2 visited — 50%")).toBeInTheDocument();
    fireEvent.click(screen.getByTestId("dossier-bulk-visit-button"));
    expect(onOpenBulkVisit).toHaveBeenCalledWith([SAMPLE_PLACE], "Mark Poland places visited");
  });

  it("allows unchecking places in BulkVisitDialog and submits remaining IDs", () => {
    const onConfirm = vi.fn();
    render(
      <BulkVisitDialog
        title="Mark Krakow places visited"
        places={[SAMPLE_PLACE, SECOND_PLACE]}
        isSubmitting={false}
        onClose={vi.fn()}
        onConfirm={onConfirm}
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
});
