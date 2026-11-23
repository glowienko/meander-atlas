import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { AtlasHealthPill } from "./AtlasHealthPill";
import { readAtlasViewState, writeAtlasViewState } from "./viewState";

describe("AtlasHealthPill", () => {
  it("renders live PostGIS health status and toggles controls", () => {
    const onProjectionChange = vi.fn();
    const onThemeChange = vi.fn();

    render(
      <AtlasHealthPill
        health={{ status: "UP", database: "postgresql", postgisVersion: "3.5.3" }}
        isLoading={false}
        isError={false}
        projection="globe"
        theme="light"
        onProjectionChange={onProjectionChange}
        onThemeChange={onThemeChange}
      />,
    );

    expect(screen.getByTestId("atlas-health-status")).toHaveTextContent("UP · PostGIS 3.5.3");

    fireEvent.click(screen.getByRole("button", { name: "2D Map" }));
    expect(onProjectionChange).toHaveBeenCalledWith("mercator");

    fireEvent.click(screen.getByRole("button", { name: "Switch to dark theme" }));
    expect(onThemeChange).toHaveBeenCalledWith("dark");
  });

  it("renders offline state when health query fails", () => {
    render(
      <AtlasHealthPill
        health={undefined}
        isLoading={false}
        isError={true}
        projection="mercator"
        theme="dark"
        onProjectionChange={vi.fn()}
        onThemeChange={vi.fn()}
      />,
    );

    expect(screen.getByTestId("atlas-health-status")).toHaveTextContent("Backend Offline");
  });
});

describe("viewState URL serialization", () => {
  it("defaults to globe and light theme and round-trips dark 2D map", () => {
    expect(readAtlasViewState("")).toEqual({ projection: "globe", theme: "light" });
    const serialized = writeAtlasViewState({ projection: "mercator", theme: "dark" }, "?foo=bar");
    expect(serialized).toContain("foo=bar");
    expect(readAtlasViewState(serialized)).toEqual({ projection: "mercator", theme: "dark" });
  });
});
