import { Compass, FolderSync, Loader2, Sparkles } from "lucide-react";
import type { ImportStatusView } from "../../api/client";

interface ImportStatusBadgeProps {
  mappedCount: number;
  status: ImportStatusView | undefined;
  isMaterializing?: boolean;
  isEnriching?: boolean;
  onMaterializeOffline?: () => void;
  onRunEnrichment?: (options: { limit: number; background: boolean }) => void;
}

const DEFAULT_ENRICH_BATCH_SIZE = 10;
const BACKGROUND_ENRICH_BATCH_SIZE = 25;

export function ImportStatusBadge({
  mappedCount,
  status,
  isMaterializing = false,
  isEnriching = false,
  onMaterializeOffline,
  onRunEnrichment,
}: ImportStatusBadgeProps) {
  const pendingCoordCount =
    status?.pendingCoordinateLookupCount ?? status?.pendingResolutionCount ?? 0;
  const pendingEnrichmentCount = status?.pendingEnrichmentCount ?? 0;
  const enrichedPlaceCount = status?.enrichedPlaceCount ?? 0;
  const approximatePlaceCount = status?.approximatePlaceCount ?? 0;
  const enrichmentRunning = status?.enrichmentRunning ?? false;
  const accounts = status?.accounts ?? [];

  const showEnrichmentSummary =
    pendingEnrichmentCount > 0 ||
    enrichedPlaceCount > 0 ||
    approximatePlaceCount > 0 ||
    enrichmentRunning;
  const showMaterializeAction = Boolean(onMaterializeOffline) && pendingCoordCount > 0;
  const showEnrichActions =
    Boolean(onRunEnrichment) && (pendingEnrichmentCount > 0 || pendingCoordCount > 0);
  const enrichDisabled = isEnriching || enrichmentRunning || isMaterializing;

  return (
    <div
      data-testid="import-status-badge"
      className="space-y-2 rounded-xl bg-white/60 p-3 text-[13px]"
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
        <strong style={{ color: "var(--atlas-ink)" }}>{pendingCoordCount}</strong> awaiting
        coordinates
      </p>

      {showEnrichmentSummary && (
        <div
          data-testid="enrichment-progress-summary"
          className="space-y-1 text-[12px] tabular-nums"
          style={{ color: "var(--atlas-muted)" }}
        >
          <p>
            <strong style={{ color: "var(--atlas-ink)" }}>{enrichedPlaceCount}</strong> enriched ·{" "}
            <strong style={{ color: "var(--atlas-ink)" }}>{pendingEnrichmentCount}</strong> pending
            enrichment
            {approximatePlaceCount > 0 && (
              <>
                {" · "}
                <strong style={{ color: "var(--atlas-ink)" }}>{approximatePlaceCount}</strong>{" "}
                approximate
              </>
            )}
          </p>
          {enrichmentRunning && (
            <p
              data-testid="enrichment-running-indicator"
              className="inline-flex items-center gap-1.5 font-medium"
              style={{ color: "var(--atlas-accent)" }}
            >
              <Loader2 className="h-3 w-3 animate-spin" aria-hidden="true" />
              Enriching in background...
            </p>
          )}
        </div>
      )}

      {(showMaterializeAction || showEnrichActions) && (
        <div className="flex flex-wrap gap-1.5 pt-0.5">
          {showMaterializeAction && onMaterializeOffline && (
            <button
              type="button"
              data-testid="materialize-offline-button"
              disabled={isMaterializing || isEnriching}
              onClick={onMaterializeOffline}
              className="inline-flex cursor-pointer items-center gap-1 rounded-full border bg-white/90 px-2.5 py-1 text-[12px] font-medium transition-colors hover:bg-black/5 disabled:cursor-not-allowed disabled:opacity-50"
              style={{
                borderColor: "var(--atlas-border)",
                color: "var(--atlas-ink)",
              }}
            >
              <Compass className="h-3 w-3" aria-hidden="true" />
              {isMaterializing ? "Materializing..." : "Materialize now"}
            </button>
          )}

          {showEnrichActions && onRunEnrichment && (
            <>
              <button
                type="button"
                data-testid="enrich-batch-button"
                disabled={enrichDisabled}
                onClick={() =>
                  onRunEnrichment({ limit: DEFAULT_ENRICH_BATCH_SIZE, background: false })
                }
                className="inline-flex cursor-pointer items-center gap-1 rounded-full px-2.5 py-1 text-[12px] font-medium text-white transition-opacity hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
                style={{ backgroundColor: "var(--atlas-accent)" }}
              >
                <Sparkles className="h-3 w-3" aria-hidden="true" />
                {isEnriching ? "Enriching..." : "Enrich"}
              </button>
              <button
                type="button"
                data-testid="enrich-all-button"
                disabled={enrichDisabled}
                onClick={() =>
                  onRunEnrichment({ limit: BACKGROUND_ENRICH_BATCH_SIZE, background: true })
                }
                className="inline-flex cursor-pointer items-center gap-1 rounded-full border bg-white/90 px-2.5 py-1 text-[12px] font-medium transition-colors hover:bg-black/5 disabled:cursor-not-allowed disabled:opacity-50"
                style={{
                  borderColor: "var(--atlas-border)",
                  color: "var(--atlas-ink)",
                }}
              >
                Enrich all
              </button>
            </>
          )}
        </div>
      )}

      {accounts.length > 0 && (
        <p className="text-[13px]" style={{ color: "var(--atlas-muted)" }}>
          Accounts: {accounts.join(" · ")}
        </p>
      )}
    </div>
  );
}
