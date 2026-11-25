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

  it("renders ImportStatusBadge with mapped and pending coordinate counts", () => {
    render(
      <ImportStatusBadge
        mappedCount={1284}
        status={{
          pendingResolutionCount: 6150,
          completedBatches: 2,
          accounts: ["personal", "work"],
          lastImportedAt: "2026-11-24T12:00:00Z",
        }}
      />,
    );

    const badge = screen.getByTestId("import-status-badge");
    expect(badge).toHaveTextContent("1284 mapped");
    expect(badge).toHaveTextContent("6150 awaiting coordinates");
    expect(badge).toHaveTextContent("2 batches");
    expect(badge).toHaveTextContent("personal");
    expect(badge).toHaveTextContent("work");
  });
});
