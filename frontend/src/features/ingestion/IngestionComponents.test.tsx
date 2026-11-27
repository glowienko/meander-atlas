import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ImportStatusBadge } from "./ImportStatusBadge";
import { SyncImportsButton } from "./SyncImportsButton";

describe("Ingestion components", () => {
  it("renders SyncImportsButton in idle and pending states", () => {
    const onClick = vi.fn();
    const { rerender } = render(<SyncImportsButton isPending={false} onClick={onClick} />);

    const idleButton = screen.getByTestId("sync-local-folder-button");
    expect(idleButton).toHaveTextContent("Sync local folder");
    expect(idleButton).not.toBeDisabled();
    fireEvent.click(idleButton);
    expect(onClick).toHaveBeenCalledTimes(1);

    rerender(<SyncImportsButton isPending={true} onClick={onClick} />);
    const pendingButton = screen.getByTestId("sync-local-folder-button");
    expect(pendingButton).toHaveTextContent("Syncing...");
    expect(pendingButton).toBeDisabled();
  });

  it("renders calm read-only enrichment status and hides single account label", () => {
    const { rerender } = render(
      <ImportStatusBadge
        mappedCount={1284}
        status={{
          pendingResolutionCount: 15,
          pendingCoordinateLookupCount: 15,
          pendingEnrichmentCount: 720,
          enrichedPlaceCount: 85,
          approximatePlaceCount: 540,
          failedOrUnresolvableCount: 3,
          enrichmentRunning: true,
          completedBatches: 2,
          accounts: ["personal", "work"],
          lastImportedAt: "2026-11-24T12:00:00Z",
        }}
      />,
    );

    const badge = screen.getByTestId("import-status-badge");
    expect(badge).toHaveTextContent("1284 mapped");
    expect(badge).toHaveTextContent("15 awaiting coordinates");
    expect(badge).toHaveTextContent("85 enriched · 540 approximate · 3 skipped");
    expect(badge).toHaveTextContent("2 batches");
    expect(badge).toHaveTextContent("Accounts: personal · work");

    const indicator = screen.getByTestId("enrichment-status-indicator");
    expect(indicator).toHaveTextContent("Enrichment in progress · 735 left");
    expect(indicator).toHaveAttribute(
      "title",
      expect.stringContaining("OpenStreetMap and Wikimedia"),
    );

    expect(screen.queryByTestId("materialize-offline-button")).not.toBeInTheDocument();
    expect(screen.queryByTestId("enrich-batch-button")).not.toBeInTheDocument();
    expect(screen.queryByTestId("enrich-all-button")).not.toBeInTheDocument();
    expect(screen.queryByRole("button")).not.toBeInTheDocument();

    rerender(
      <ImportStatusBadge
        mappedCount={1284}
        status={{
          pendingResolutionCount: 0,
          pendingCoordinateLookupCount: 0,
          pendingEnrichmentCount: 12,
          enrichedPlaceCount: 95,
          approximatePlaceCount: 0,
          failedOrUnresolvableCount: 0,
          enrichmentRunning: false,
          completedBatches: 1,
          accounts: ["personal"],
        }}
      />,
    );

    expect(badge).toHaveTextContent("1 batch");
    expect(badge).not.toHaveTextContent("Accounts:");
    expect(screen.getByTestId("enrichment-status-indicator")).toHaveTextContent(
      "Enrichment queued · 12 left",
    );

    rerender(
      <ImportStatusBadge
        mappedCount={7174}
        status={{
          pendingResolutionCount: 0,
          pendingCoordinateLookupCount: 0,
          pendingEnrichmentCount: 0,
          enrichedPlaceCount: 120,
          approximatePlaceCount: 4,
          failedOrUnresolvableCount: 0,
          enrichmentRunning: false,
          completedBatches: 1,
          accounts: ["personal"],
        }}
      />,
    );

    expect(screen.getByTestId("enrichment-status-indicator")).toHaveTextContent(
      "All 7,174 places up to date",
    );
    expect(screen.getByTestId("enrichment-progress-summary")).toHaveTextContent(
      "120 enriched · 4 approximate",
    );
  });
});
