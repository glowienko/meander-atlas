import { Compass, FolderSync, Loader2, Sparkles } from "lucide-react";
import type { ImportStatusView } from "../../api/client";
import type { EnrichmentOptions } from "./useLocalImport";

interface ImportStatusBadgeProps {
  mappedCount: number;
  status: ImportStatusView | undefined;
  isMaterializing: boolean;
  isEnriching: boolean;
  onMaterializeOffline: () => void;
  onRunEnrichment: (options: EnrichmentOptions) => void;
}

const DEFAULT_ENRICH_BATCH_SIZE = 10;
const BACKGROUND_ENRICH_BATCH_SIZE = 25;
const SMALL_PILL = "atlas-pill gap-1 bg-white/90 px-2.5 text-[12px]";

function Count({ value }: { value: number }) {
  return <strong className="text-atlas-ink">{value}</strong>;
}

export function ImportStatusBadge({
  mappedCount,
  status,
  isMaterializing,
  isEnriching,
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
  const showMaterializeAction = pendingCoordCount > 0;
  const showEnrichActions = pendingEnrichmentCount > 0 || pendingCoordCount > 0;
  const enrichDisabled = isEnriching || enrichmentRunning || isMaterializing;

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
        <Count value={mappedCount} /> mapped · <Count value={pendingCoordCount} /> awaiting
        coordinates
      </p>

      {showEnrichmentSummary && (
        <div
          data-testid="enrichment-progress-summary"
          className="space-y-1 text-[12px] tabular-nums"
        >
          <p>
            <Count value={enrichedPlaceCount} /> enriched · <Count value={pendingEnrichmentCount} />{" "}
            pending enrichment
            {approximatePlaceCount > 0 && (
              <>
                {" · "}
                <Count value={approximatePlaceCount} /> approximate
              </>
            )}
          </p>
          {enrichmentRunning && (
            <p
              data-testid="enrichment-running-indicator"
              className="inline-flex items-center gap-1.5 font-medium text-atlas-accent"
            >
              <Loader2 className="h-3 w-3 animate-spin" aria-hidden="true" />
              Enriching in background...
            </p>
          )}
        </div>
      )}

      {(showMaterializeAction || showEnrichActions) && (
        <div className="flex flex-wrap gap-1.5 pt-0.5">
          {showMaterializeAction && (
            <button
              type="button"
              data-testid="materialize-offline-button"
              title="Decodes offline Google S2 Cell IDs and DMS coordinate titles into map pins with zero network calls. Remaining rows without S2/DMS hints require Enrich."
              disabled={isMaterializing || isEnriching}
              onClick={onMaterializeOffline}
              className={SMALL_PILL}
            >
              <Compass className="h-3 w-3" aria-hidden="true" />
              {isMaterializing ? "Materializing..." : "Materialize now"}
            </button>
          )}

          {showEnrichActions && (
            <>
              <button
                type="button"
                data-testid="enrich-batch-button"
                title={`Enriches up to ${DEFAULT_ENRICH_BATCH_SIZE} places using OpenStreetMap (Overpass/Photon) and Wikimedia to refine approximate pins, categories, cities, photos, and coordinate-only names.`}
                disabled={enrichDisabled}
                onClick={() =>
                  onRunEnrichment({ limit: DEFAULT_ENRICH_BATCH_SIZE, background: false })
                }
                className="atlas-pill-solid gap-1 px-2.5 text-[12px]"
              >
                <Sparkles className="h-3 w-3" aria-hidden="true" />
                {isEnriching ? "Enriching..." : "Enrich"}
              </button>
              <button
                type="button"
                data-testid="enrich-all-button"
                title="Runs resumable background enrichment across all pending places with polite rate limiting."
                disabled={enrichDisabled}
                onClick={() =>
                  onRunEnrichment({ limit: BACKGROUND_ENRICH_BATCH_SIZE, background: true })
                }
                className={SMALL_PILL}
              >
                Enrich all
              </button>
            </>
          )}
        </div>
      )}

      {accounts.length > 0 && <p>Accounts: {accounts.join(" · ")}</p>}
    </div>
  );
}
