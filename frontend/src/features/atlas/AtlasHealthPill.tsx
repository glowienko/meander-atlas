import { BarChart3, Globe, Map as MapIcon } from "lucide-react";
import type { ReactNode } from "react";
import type { AtlasHealthView } from "../../api/client";
import type { AtlasProjection } from "./viewState";

interface AtlasHealthPillProps {
  health: AtlasHealthView | undefined;
  isLoading: boolean;
  isError: boolean;
  projection: AtlasProjection;
  onProjectionChange: (projection: AtlasProjection) => void;
  statsOpen?: boolean;
  onToggleStats?: () => void;
  extraActions?: ReactNode;
}

export function AtlasHealthPill({
  health,
  isLoading,
  isError,
  projection,
  onProjectionChange,
  statsOpen = false,
  onToggleStats,
  extraActions,
}: AtlasHealthPillProps) {
  const isHealthy = !isLoading && !isError && health?.status === "UP";
  const statusLabel = isLoading
    ? "Checking PostGIS..."
    : isError || !health
      ? "Backend Offline"
      : `${health.status} · PostGIS ${health.postgisVersion}`;

  const statusDotColor = isLoading
    ? "bg-amber-500"
    : isHealthy
      ? "bg-[var(--atlas-accent)]"
      : "bg-[var(--atlas-danger)]";

  return (
    <div
      className="pointer-events-auto flex flex-wrap items-center justify-center gap-2.5 rounded-full border px-4 py-1.5 shadow-sm backdrop-blur-md"
      style={{
        backgroundColor: "var(--atlas-surface)",
        borderColor: "var(--atlas-border)",
      }}
    >
      <div className="flex items-center gap-2.5 pr-1">
        <span className="font-editorial text-base font-semibold tracking-tight">MeanderAtlas</span>
        <span
          className="h-3.5 w-px"
          style={{ backgroundColor: "var(--atlas-border)" }}
          aria-hidden="true"
        />
        <span
          data-testid="atlas-health-status"
          className="inline-flex items-center gap-1.5 text-[13px] font-medium"
          style={{ color: "var(--atlas-muted)" }}
        >
          <span
            data-testid="atlas-health-dot"
            className={`h-2 w-2 rounded-full ${statusDotColor}`}
            aria-hidden="true"
          />
          {statusLabel}
        </span>
      </div>

      <fieldset
        className="m-0 flex items-center rounded-full border p-0.5 text-[13px]"
        style={{ borderColor: "var(--atlas-border)" }}
        aria-label="Map projection"
      >
        <button
          type="button"
          onClick={() => onProjectionChange("globe")}
          aria-pressed={projection === "globe"}
          className="inline-flex cursor-pointer items-center gap-1 rounded-full px-2.5 py-1 font-medium transition-colors"
          style={{
            backgroundColor: projection === "globe" ? "var(--atlas-accent-soft)" : "transparent",
            color: projection === "globe" ? "var(--atlas-accent)" : "var(--atlas-muted)",
          }}
        >
          <Globe className="h-3.5 w-3.5" aria-hidden="true" />
          Globe
        </button>
        <button
          type="button"
          onClick={() => onProjectionChange("mercator")}
          aria-pressed={projection === "mercator"}
          className="inline-flex cursor-pointer items-center gap-1 rounded-full px-2.5 py-1 font-medium transition-colors"
          style={{
            backgroundColor: projection === "mercator" ? "var(--atlas-accent-soft)" : "transparent",
            color: projection === "mercator" ? "var(--atlas-accent)" : "var(--atlas-muted)",
          }}
        >
          <MapIcon className="h-3.5 w-3.5" aria-hidden="true" />
          2D Map
        </button>
      </fieldset>

      {onToggleStats && (
        <button
          type="button"
          data-testid="toggle-stats-button"
          onClick={onToggleStats}
          aria-pressed={statsOpen}
          className="inline-flex cursor-pointer items-center gap-1.5 rounded-full border px-3 py-1 text-[13px] font-medium transition-colors"
          style={{
            borderColor: "var(--atlas-border)",
            backgroundColor: statsOpen ? "var(--atlas-accent-soft)" : "transparent",
            color: statsOpen ? "var(--atlas-accent)" : "var(--atlas-ink)",
          }}
        >
          <BarChart3 className="h-3.5 w-3.5" aria-hidden="true" />
          Stats
        </button>
      )}

      {extraActions}
    </div>
  );
}
