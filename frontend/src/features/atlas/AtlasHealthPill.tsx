import { BarChart3, Globe, Home, type LucideIcon, Map as MapIcon } from "lucide-react";
import type { ReactNode } from "react";
import type { AtlasHealthView } from "../../api/client";
import type { AtlasBasemap, AtlasProjection } from "./viewState";

interface AtlasHealthPillProps {
  health: AtlasHealthView | undefined;
  isLoading: boolean;
  isError: boolean;
  projection: AtlasProjection;
  onProjectionChange: (projection: AtlasProjection) => void;
  basemap: AtlasBasemap;
  onBasemapChange: (basemap: AtlasBasemap) => void;
  statsOpen: boolean;
  mappedCount?: number;
  onToggleStats: () => void;
  onResetView: () => void;
  extraActions?: ReactNode;
}

interface ToggleOption<T extends string> {
  value: T;
  label: string;
  Icon?: LucideIcon;
}

const PROJECTION_OPTIONS: readonly ToggleOption<AtlasProjection>[] = [
  { value: "globe", label: "Globe", Icon: Globe },
  { value: "mercator", label: "2D Map", Icon: MapIcon },
];

const BASEMAP_OPTIONS: readonly ToggleOption<AtlasBasemap>[] = [
  { value: "atlas", label: "Atlas" },
  { value: "street", label: "Street" },
];

function SegmentedToggle<T extends string>({
  label,
  value,
  options,
  onChange,
}: {
  label: string;
  value: T;
  options: readonly ToggleOption<T>[];
  onChange: (value: T) => void;
}) {
  return (
    <fieldset
      className="m-0 flex items-center rounded-full border border-atlas-border p-0.5 text-[13px]"
      aria-label={label}
    >
      {options.map(({ value: optionValue, label: optionLabel, Icon }) => (
        <button
          key={optionValue}
          type="button"
          onClick={() => onChange(optionValue)}
          aria-pressed={value === optionValue}
          className="inline-flex cursor-pointer items-center gap-1 rounded-full px-2.5 py-1 font-medium text-atlas-muted transition-colors aria-pressed:bg-atlas-accent-soft aria-pressed:text-atlas-accent"
        >
          {Icon && <Icon className="h-3.5 w-3.5" aria-hidden="true" />}
          {optionLabel}
        </button>
      ))}
    </fieldset>
  );
}

export function AtlasHealthPill({
  health,
  isLoading,
  isError,
  projection,
  onProjectionChange,
  basemap,
  onBasemapChange,
  statsOpen,
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
      ? "bg-atlas-accent"
      : "bg-atlas-danger";

  return (
    <div className="pointer-events-auto flex flex-wrap items-center justify-center gap-2.5 rounded-full border border-atlas-border bg-atlas-surface px-4 py-1.5 shadow-sm backdrop-blur-md">
      <div className="flex items-center gap-2.5 pr-1">
        <button
          type="button"
          data-testid="brand-reset-view-button"
          title="Reset to world view"
          onClick={onResetView}
          className="font-editorial cursor-pointer text-base font-semibold tracking-tight transition-opacity hover:opacity-75"
        >
          MeanderAtlas
        </button>
        <span className="h-3.5 w-px bg-atlas-border" aria-hidden="true" />
        <span
          data-testid="atlas-health-status"
          className="inline-flex items-center gap-1.5 text-[13px] font-medium text-atlas-muted"
        >
          <span
            data-testid="atlas-health-dot"
            className={`h-2 w-2 rounded-full ${statusDotColor}`}
            aria-hidden="true"
          />
          {statusLabel}
        </span>
      </div>

      <SegmentedToggle
        label="Map projection"
        value={projection}
        options={PROJECTION_OPTIONS}
        onChange={onProjectionChange}
      />
      <SegmentedToggle
        label="Map basemap"
        value={basemap}
        options={BASEMAP_OPTIONS}
        onChange={onBasemapChange}
      />

      <button
        type="button"
        data-testid="reset-view-button"
        onClick={onResetView}
        className="atlas-pill"
      >
        <Home className="h-3.5 w-3.5" aria-hidden="true" />
        Reset view
      </button>

      <button
        type="button"
        data-testid="toggle-stats-button"
        title="Open travel statistics and sync status"
        onClick={onToggleStats}
        aria-pressed={statsOpen}
        className="atlas-pill border-atlas-accent/35 bg-atlas-accent-soft font-semibold text-atlas-accent aria-pressed:border-atlas-accent aria-pressed:bg-atlas-accent aria-pressed:text-white"
      >
        <BarChart3 className="h-3.5 w-3.5" aria-hidden="true" />
        <span>Stats</span>
        {mappedCount !== undefined && (
          <span className="rounded-full bg-atlas-accent/15 px-1.5 text-[11px] tabular-nums">
            {mappedCount.toLocaleString()}
          </span>
        )}
      </button>

      {extraActions}
    </div>
  );
}
