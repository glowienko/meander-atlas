import { CheckCircle2, FolderSync, Loader2, Sparkles } from "lucide-react";
import type { ImportStatusView } from "../../api/client";

interface ImportStatusBadgeProps {
  mappedCount: number;
  status: ImportStatusView | undefined;
}

const ENRICHMENT_TOOLTIP =
  "Background enrichment automatically refines approximate pins, categories, cities, photos, and coordinate-only names using OpenStreetMap and Wikimedia.";

function Count({ value }: { value: number }) {
  return <strong className="text-atlas-ink">{value}</strong>;
}

function EnrichmentStatusLine({
  mappedCount,
  enrichmentRunning,
  remainingCount,
}: {
  mappedCount: number;
  enrichmentRunning: boolean;
  remainingCount: number;
}) {
  if (enrichmentRunning) {
    return (
      <p
        data-testid="enrichment-status-indicator"
        title={ENRICHMENT_TOOLTIP}
        className="inline-flex items-center gap-1.5 text-[12px] font-medium text-atlas-accent tabular-nums"
      >
        <Loader2 className="h-3 w-3 animate-spin" aria-hidden="true" />
        Enrichment in progress · {remainingCount} left
      </p>
    );
  }
  if (remainingCount > 0) {
    return (
      <p
        data-testid="enrichment-status-indicator"
        title={ENRICHMENT_TOOLTIP}
        className="inline-flex items-center gap-1.5 text-[12px] text-atlas-muted tabular-nums"
      >
        <Sparkles className="h-3 w-3" aria-hidden="true" />
        Enrichment queued · {remainingCount} left
      </p>
    );
  }
  if (mappedCount > 0) {
    return (
      <p
        data-testid="enrichment-status-indicator"
        title={ENRICHMENT_TOOLTIP}
        className="inline-flex items-center gap-1.5 text-[12px] text-atlas-muted tabular-nums"
      >
        <CheckCircle2 className="h-3 w-3 text-atlas-accent" aria-hidden="true" />
        All {mappedCount.toLocaleString("en-US")} places up to date
      </p>
    );
  }
  return null;
}

export function ImportStatusBadge({ mappedCount, status }: ImportStatusBadgeProps) {
  const pendingCoordCount =
    status?.pendingCoordinateLookupCount ?? status?.pendingResolutionCount ?? 0;
  const pendingEnrichmentCount = status?.pendingEnrichmentCount ?? 0;
  const remainingCount = pendingEnrichmentCount + pendingCoordCount;
  const enrichedPlaceCount = status?.enrichedPlaceCount ?? 0;
  const approximatePlaceCount = status?.approximatePlaceCount ?? 0;
  const failedOrUnresolvableCount = status?.failedOrUnresolvableCount ?? 0;
  const enrichmentRunning = status?.enrichmentRunning ?? false;
  const accounts = status?.accounts ?? [];
  const showDetailsSubline =
    enrichedPlaceCount > 0 || approximatePlaceCount > 0 || failedOrUnresolvableCount > 0;

  return (
    <div
      data-testid="import-status-badge"
      className="space-y-2 rounded-xl bg-white/60 p-3 text-[13px] text-atlas-muted"
    >
      <div className="flex items-center justify-between gap-2">
        <span className="inline-flex items-center gap-1.5 text-sm font-semibold text-atlas-ink">
          <FolderSync className="h-3.5 w-3.5" aria-hidden="true" />
          Local Takeout sync
        </span>
        <span className="tabular-nums">
          {status?.completedBatches ?? 0} batch{status?.completedBatches === 1 ? "" : "es"}
        </span>
      </div>

      <p className="tabular-nums">
        <Count value={mappedCount} /> mapped
        {pendingCoordCount > 0 && (
          <>
            {" · "}
            <Count value={pendingCoordCount} /> awaiting coordinates
          </>
        )}
      </p>

      <EnrichmentStatusLine
        mappedCount={mappedCount}
        enrichmentRunning={enrichmentRunning}
        remainingCount={remainingCount}
      />

      {showDetailsSubline && (
        <p data-testid="enrichment-progress-summary" className="text-[12px] tabular-nums">
          <Count value={enrichedPlaceCount} /> enriched
          {approximatePlaceCount > 0 && (
            <>
              {" · "}
              <Count value={approximatePlaceCount} /> approximate
            </>
          )}
          {failedOrUnresolvableCount > 0 && (
            <>
              {" · "}
              <Count value={failedOrUnresolvableCount} /> skipped
            </>
          )}
        </p>
      )}

      {accounts.length >= 2 && <p>Accounts: {accounts.join(" · ")}</p>}
    </div>
  );
}
