import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { AtlasHealthPill } from "./AtlasHealthPill";
import { readAtlasViewState, writeAtlasViewState } from "./viewState";

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
  it("defaults to clean URL for globe and preserves other query params on round-trip", () => {
    expect(readAtlasViewState("")).toEqual({ projection: "globe" });
    expect(writeAtlasViewState({ projection: "globe" }, "")).toBe("");

    const serialized = writeAtlasViewState({ projection: "mercator" }, "?foo=bar");
    expect(serialized).toContain("foo=bar");
    expect(serialized).toContain("view=map");
    expect(readAtlasViewState(serialized)).toEqual({ projection: "mercator" });

    expect(writeAtlasViewState({ projection: "globe" }, serialized)).toBe("?foo=bar");
  });
});
