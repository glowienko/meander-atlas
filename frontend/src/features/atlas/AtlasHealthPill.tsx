import { BarChart3, Globe, Home, Map as MapIcon } from "lucide-react";
import type { ReactNode } from "react";
import type { AtlasHealthView } from "../../api/client";
import type { AtlasBasemap, AtlasProjection } from "./viewState";

interface AtlasHealthPillProps {
  health: AtlasHealthView | undefined;
  isLoading: boolean;
  isError: boolean;
  projection: AtlasProjection;
  onProjectionChange: (projection: AtlasProjection) => void;
  basemap?: AtlasBasemap;
  onBasemapChange?: (basemap: AtlasBasemap) => void;
  statsOpen?: boolean;
  mappedCount?: number;
  onToggleStats?: () => void;
  onResetView?: () => void;
  extraActions?: ReactNode;
}

export function AtlasHealthPill({
  health,
  isLoading,
  isError,
  projection,
  onProjectionChange,
  basemap = "atlas",
  onBasemapChange,
  statsOpen = false,
  mappedCount,
  onToggleStats,
  onResetView,
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
        {onResetView ? (
          <button
            type="button"
            data-testid="brand-reset-view-button"
            title="Reset to world view"
            onClick={onResetView}
            className="font-editorial cursor-pointer text-base font-semibold tracking-tight transition-opacity hover:opacity-75"
          >
            MeanderAtlas
          </button>
        ) : (
          <span className="font-editorial text-base font-semibold tracking-tight">
            MeanderAtlas
          </span>
        )}
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

      {onBasemapChange && (
        <fieldset
          className="m-0 flex items-center rounded-full border p-0.5 text-[13px]"
          style={{ borderColor: "var(--atlas-border)" }}
          aria-label="Map basemap"
        >
          <button
            type="button"
            onClick={() => onBasemapChange("atlas")}
            aria-pressed={basemap === "atlas"}
            className="inline-flex cursor-pointer items-center rounded-full px-2.5 py-1 font-medium transition-colors"
            style={{
              backgroundColor: basemap === "atlas" ? "var(--atlas-accent-soft)" : "transparent",
              color: basemap === "atlas" ? "var(--atlas-accent)" : "var(--atlas-muted)",
            }}
          >
            Atlas
          </button>
          <button
            type="button"
            onClick={() => onBasemapChange("street")}
            aria-pressed={basemap === "street"}
            className="inline-flex cursor-pointer items-center rounded-full px-2.5 py-1 font-medium transition-colors"
            style={{
              backgroundColor: basemap === "street" ? "var(--atlas-accent-soft)" : "transparent",
              color: basemap === "street" ? "var(--atlas-accent)" : "var(--atlas-muted)",
            }}
          >
            Street
          </button>
        </fieldset>
      )}

      {onResetView && (
        <button
          type="button"
          data-testid="reset-view-button"
          onClick={onResetView}
          className="inline-flex cursor-pointer items-center gap-1.5 rounded-full border px-3 py-1 text-[13px] font-medium transition-colors hover:bg-black/5"
          style={{
            borderColor: "var(--atlas-border)",
            color: "var(--atlas-ink)",
          }}
        >
          <Home className="h-3.5 w-3.5" aria-hidden="true" />
          Reset view
        </button>
      )}

      {onToggleStats && (
        <button
          type="button"
          data-testid="toggle-stats-button"
          title="Open travel statistics and sync status"
          onClick={onToggleStats}
          aria-pressed={statsOpen}
          className="inline-flex cursor-pointer items-center gap-1.5 rounded-full border px-3 py-1 text-[13px] font-semibold shadow-xs transition-colors"
          style={{
            borderColor: statsOpen ? "var(--atlas-accent)" : "rgba(29, 91, 76, 0.35)",
            backgroundColor: statsOpen ? "var(--atlas-accent)" : "var(--atlas-accent-soft)",
            color: statsOpen ? "#ffffff" : "var(--atlas-accent)",
          }}
        >
          <BarChart3 className="h-3.5 w-3.5" aria-hidden="true" />
          <span>Stats</span>
          {mappedCount !== undefined && (
            <span
              className="rounded-full px-1.5 py-0.2 text-[11px] tabular-nums"
              style={{
                backgroundColor: statsOpen ? "rgba(255, 255, 255, 0.22)" : "rgba(29, 91, 76, 0.14)",
              }}
            >
              {mappedCount.toLocaleString()}
            </span>
          )}
        </button>
      )}

      {extraActions}
    </div>
  );
}
