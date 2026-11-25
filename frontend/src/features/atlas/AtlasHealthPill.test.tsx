import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { AtlasHealthPill } from "./AtlasHealthPill";
import { defaultAtlasViewState, readAtlasViewState, writeAtlasViewState } from "./viewState";

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
  });
});
