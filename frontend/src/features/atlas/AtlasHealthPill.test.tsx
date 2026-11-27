import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { AtlasHealthPill } from "./AtlasHealthPill";
import {
  defaultAtlasViewState,
  isPlaceInViewport,
  readAtlasViewState,
  writeAtlasViewState,
} from "./viewState";

describe("AtlasHealthPill", () => {
  it("renders live PostGIS health status and toggles projection controls", () => {
    const onProjectionChange = vi.fn();

    render(
      <AtlasHealthPill
        health={{ status: "UP", database: "postgresql", postgisVersion: "3.5.3" }}
        isLoading={false}
        isError={false}
        projection="globe"
        onProjectionChange={onProjectionChange}
      />,
    );

    expect(screen.getByTestId("atlas-health-status")).toHaveTextContent("UP · PostGIS 3.5.3");
    expect(screen.getByTestId("atlas-health-dot")).toHaveClass("bg-[var(--atlas-accent)]");

    fireEvent.click(screen.getByRole("button", { name: "2D Map" }));
    expect(onProjectionChange).toHaveBeenCalledWith("mercator");
  });

  it("renders offline state when health query fails", () => {
    render(
      <AtlasHealthPill
        health={undefined}
        isLoading={false}
        isError={true}
        projection="mercator"
        onProjectionChange={vi.fn()}
      />,
    );

    expect(screen.getByTestId("atlas-health-status")).toHaveTextContent("Backend Offline");
    expect(screen.getByTestId("atlas-health-dot")).toHaveClass("bg-[var(--atlas-danger)]");
  });

  it("renders danger indicator when backend reports non-UP status", () => {
    render(
      <AtlasHealthPill
        health={{ status: "DEGRADED", database: "postgresql", postgisVersion: "3.5.3" }}
        isLoading={false}
        isError={false}
        projection="globe"
        onProjectionChange={vi.fn()}
      />,
    );

    expect(screen.getByTestId("atlas-health-status")).toHaveTextContent("DEGRADED · PostGIS 3.5.3");
    expect(screen.getByTestId("atlas-health-dot")).toHaveClass("bg-[var(--atlas-danger)]");
  });

  it("renders basemap toggle when onBasemapChange is provided and emits selections", () => {
    const onBasemapChange = vi.fn();

    render(
      <AtlasHealthPill
        health={{ status: "UP", database: "postgresql", postgisVersion: "3.5.3" }}
        isLoading={false}
        isError={false}
        projection="globe"
        onProjectionChange={vi.fn()}
        basemap="atlas"
        onBasemapChange={onBasemapChange}
      />,
    );

    const atlasBtn = screen.getByRole("button", { name: "Atlas" });
    const streetBtn = screen.getByRole("button", { name: "Street" });
    expect(atlasBtn).toHaveAttribute("aria-pressed", "true");
    expect(streetBtn).toHaveAttribute("aria-pressed", "false");

    fireEvent.click(streetBtn);
    expect(onBasemapChange).toHaveBeenCalledWith("street");
  });

  it("renders Reset view button, clickable brand title, and prominent Stats count", () => {
    const onResetView = vi.fn();
    const onToggleStats = vi.fn();

    render(
      <AtlasHealthPill
        health={{ status: "UP", database: "postgresql", postgisVersion: "3.5.3" }}
        isLoading={false}
        isError={false}
        projection="globe"
        onProjectionChange={vi.fn()}
        mappedCount={7174}
        onToggleStats={onToggleStats}
        onResetView={onResetView}
      />,
    );

    fireEvent.click(screen.getByTestId("reset-view-button"));
    fireEvent.click(screen.getByTestId("brand-reset-view-button"));
    expect(onResetView).toHaveBeenCalledTimes(2);
    expect(screen.getByTestId("toggle-stats-button")).toHaveTextContent("Stats");
    expect(screen.getByTestId("toggle-stats-button")).toHaveTextContent("7,174");
    fireEvent.click(screen.getByTestId("toggle-stats-button"));
    expect(onToggleStats).toHaveBeenCalledTimes(1);
  });
});

describe("viewState URL serialization", () => {
  it("defaults to clean URL for globe and preserves unknown query params across filters and region", () => {
    const defaults = defaultAtlasViewState();
    expect(readAtlasViewState("")).toEqual(defaults);
    expect(writeAtlasViewState(defaults, "")).toBe("");

    const filteredState = {
      ...defaults,
      projection: "mercator" as const,
      q: "wawel",
      visitStatus: "VISITED" as const,
      derivedRole: "MUST_SEE" as const,
      category: "LANDMARK" as const,
      account: "personal",
      list: "Starred",
      regionLevel: "COUNTRY" as const,
      regionCode: "PL",
      statsOpen: true,
    };

    const serialized = writeAtlasViewState(filteredState, "?basemap=offline");
    expect(serialized).toContain("basemap=offline");
    expect(serialized).toContain("view=map");
    expect(serialized).toContain("q=wawel");
    expect(serialized).toContain("status=VISITED");
    expect(serialized).toContain("role=MUST_SEE");
    expect(serialized).toContain("category=LANDMARK");
    expect(serialized).toContain("account=personal");
    expect(serialized).toContain("list=Starred");
    expect(serialized).toContain("regionLevel=COUNTRY");
    expect(serialized).toContain("region=PL");
    expect(serialized).toContain("stats=1");
    expect(readAtlasViewState(serialized)).toEqual(filteredState);

    expect(writeAtlasViewState(defaults, serialized)).toBe("?basemap=offline");

    const fallbackCountry = readAtlasViewState("?region=PL");
    expect(fallbackCountry.regionLevel).toBe("COUNTRY");
    expect(fallbackCountry.regionCode).toBe("PL");
  });

  it("serializes basemap=street and preserves basemap=offline override", () => {
    const defaults = defaultAtlasViewState();
    const streetState = { ...defaults, basemap: "street" as const };

    const streetUrl = writeAtlasViewState(streetState, "");
    expect(streetUrl).toBe("?basemap=street");
    expect(readAtlasViewState(streetUrl)).toEqual(streetState);
    expect(writeAtlasViewState(defaults, streetUrl)).toBe("");

    const offlinePreserved = writeAtlasViewState(streetState, "?basemap=offline");
    expect(offlinePreserved).toBe("?basemap=offline");
    expect(readAtlasViewState("?basemap=offline").basemap).toBe("atlas");
  });

  it("handles standard, antimeridian, unwrapped, and full-world viewports", () => {
    expect(
      isPlaceInViewport(
        { lat: 50.0, lng: 19.9 },
        { minLng: 19.0, minLat: 49.0, maxLng: 21.0, maxLat: 51.0 },
      ),
    ).toBe(true);
    expect(
      isPlaceInViewport(
        { lat: 52.0, lng: 19.9 },
        { minLng: 19.0, minLat: 49.0, maxLng: 21.0, maxLat: 51.0 },
      ),
    ).toBe(false);
    expect(
      isPlaceInViewport(
        { lat: -18.0, lng: 179.5 },
        { minLng: 170.0, minLat: -25.0, maxLng: -170.0, maxLat: 0.0 },
      ),
    ).toBe(true);
    expect(
      isPlaceInViewport(
        { lat: -14.0, lng: -175.0 },
        { minLng: 170.0, minLat: -25.0, maxLng: -170.0, maxLat: 0.0 },
      ),
    ).toBe(true);
    expect(
      isPlaceInViewport(
        { lat: -14.0, lng: 10.0 },
        { minLng: 170.0, minLat: -25.0, maxLng: -170.0, maxLat: 0.0 },
      ),
    ).toBe(false);
    expect(
      isPlaceInViewport(
        { lat: -14.0, lng: -175.0 },
        { minLng: 170.0, minLat: -25.0, maxLng: 190.0, maxLat: 0.0 },
      ),
    ).toBe(true);
    expect(
      isPlaceInViewport(
        { lat: 10.0, lng: 0.0 },
        { minLng: -240.0, minLat: -85.0, maxLng: 240.0, maxLat: 85.0 },
      ),
    ).toBe(true);
  });
});
