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
