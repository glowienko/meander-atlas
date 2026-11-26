import { FolderSync } from "lucide-react";
import type { ImportStatusView } from "../../api/client";

interface ImportStatusBadgeProps {
  mappedCount: number;
  status: ImportStatusView | undefined;
}

export function ImportStatusBadge({ mappedCount, status }: ImportStatusBadgeProps) {
  const pendingCount = status?.pendingResolutionCount ?? 0;
  const accounts = status?.accounts ?? [];

  return (
    <div
      data-testid="import-status-badge"
      className="space-y-1.5 rounded-xl bg-white/60 p-3 text-[13px]"
    >
      <div className="flex items-center justify-between gap-2">
        <span
          className="inline-flex items-center gap-1.5 text-sm font-semibold"
          style={{ color: "var(--atlas-ink)" }}
        >
          <FolderSync className="h-3.5 w-3.5" aria-hidden="true" />
          Local Takeout sync
        </span>
        <span className="tabular-nums" style={{ color: "var(--atlas-muted)" }}>
          {status?.completedBatches ?? 0} batch{status?.completedBatches === 1 ? "" : "es"}
        </span>
      </div>

      <p className="tabular-nums" style={{ color: "var(--atlas-muted)" }}>
        <strong style={{ color: "var(--atlas-ink)" }}>{mappedCount}</strong> mapped ·{" "}
        <strong style={{ color: "var(--atlas-ink)" }}>{pendingCount}</strong> awaiting coordinates
      </p>

      {accounts.length > 0 && (
        <p className="text-[13px]" style={{ color: "var(--atlas-muted)" }}>
          Accounts: {accounts.join(" · ")}
        </p>
      )}
    </div>
  );
}
