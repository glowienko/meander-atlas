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
      className="space-y-1.5 rounded-xl border bg-white/70 p-3"
      style={{ borderColor: "var(--atlas-border)" }}
    >
      <div className="flex items-center justify-between gap-2">
        <span
          className="inline-flex items-center gap-1.5 font-semibold"
          style={{ color: "var(--atlas-ink)" }}
        >
          <FolderSync className="h-3.5 w-3.5" aria-hidden="true" />
          Local Takeout Sync
        </span>
        <span style={{ color: "var(--atlas-muted)" }}>
          {status?.completedBatches ?? 0} batch{status?.completedBatches === 1 ? "" : "es"}
        </span>
      </div>

      <p style={{ color: "var(--atlas-muted)" }}>
        <strong style={{ color: "var(--atlas-ink)" }}>{mappedCount}</strong> mapped ·{" "}
        <strong style={{ color: "var(--atlas-ink)" }}>{pendingCount}</strong> awaiting coordinates
      </p>

      {accounts.length > 0 && (
        <div className="flex flex-wrap gap-1 pt-0.5">
          {accounts.map((account) => (
            <span
              key={account}
              className="rounded-full border px-2 py-0.5 text-[11px]"
              style={{
                borderColor: "var(--atlas-border)",
                color: "var(--atlas-muted)",
              }}
            >
              {account}
            </span>
          ))}
        </div>
      )}
    </div>
  );
}
