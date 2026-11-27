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

  it("renders ImportStatusBadge with progress counters and materialize/enrich triggers", () => {
    const onMaterializeOffline = vi.fn();
    const onRunEnrichment = vi.fn();
    const { rerender } = render(
      <ImportStatusBadge
        mappedCount={1284}
        status={{
          pendingResolutionCount: 6150,
          pendingCoordinateLookupCount: 6150,
          pendingEnrichmentCount: 720,
          enrichedPlaceCount: 85,
          approximatePlaceCount: 540,
          enrichmentRunning: false,
          completedBatches: 2,
          accounts: ["personal", "work"],
          lastImportedAt: "2026-11-24T12:00:00Z",
        }}
        onMaterializeOffline={onMaterializeOffline}
        onRunEnrichment={onRunEnrichment}
        isMaterializing={false}
        isEnriching={false}
      />,
    );

    const badge = screen.getByTestId("import-status-badge");
    expect(badge).toHaveTextContent("1284 mapped");
    expect(badge).toHaveTextContent("6150 awaiting coordinates");
    expect(badge).toHaveTextContent("85 enriched · 720 pending enrichment · 540 approximate");
    expect(badge).toHaveTextContent("2 batches");
    expect(badge).toHaveTextContent("personal");
    expect(badge).toHaveTextContent("work");

    fireEvent.click(screen.getByTestId("materialize-offline-button"));
    expect(onMaterializeOffline).toHaveBeenCalledTimes(1);

    fireEvent.click(screen.getByTestId("enrich-batch-button"));
    expect(onRunEnrichment).toHaveBeenCalledWith({ limit: 10, background: false });

    fireEvent.click(screen.getByTestId("enrich-all-button"));
    expect(onRunEnrichment).toHaveBeenCalledWith({ limit: 25, background: true });

    rerender(
      <ImportStatusBadge
        mappedCount={1284}
        status={{
          pendingResolutionCount: 0,
          pendingCoordinateLookupCount: 0,
          pendingEnrichmentCount: 720,
          enrichedPlaceCount: 95,
          approximatePlaceCount: 530,
          enrichmentRunning: true,
          completedBatches: 2,
          accounts: ["personal"],
        }}
        onMaterializeOffline={onMaterializeOffline}
        onRunEnrichment={onRunEnrichment}
        isMaterializing={false}
        isEnriching={false}
      />,
    );

    expect(screen.queryByTestId("materialize-offline-button")).not.toBeInTheDocument();
    expect(screen.getByTestId("enrichment-running-indicator")).toHaveTextContent(
      "Enriching in background...",
    );
    expect(screen.getByTestId("enrich-batch-button")).toBeDisabled();
    expect(screen.getByTestId("enrich-all-button")).toBeDisabled();
  });
});
