import { Globe, Map as MapIcon } from "lucide-react";
import type { AtlasHealthView } from "../../api/client";
import type { AtlasProjection } from "./viewState";

interface AtlasHealthPillProps {
  health: AtlasHealthView | undefined;
  isLoading: boolean;
  isError: boolean;
  projection: AtlasProjection;
  onProjectionChange: (projection: AtlasProjection) => void;
}

export function AtlasHealthPill({
  health,
  isLoading,
  isError,
  projection,
  onProjectionChange,
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
    <header className="pointer-events-none fixed inset-x-0 top-5 z-10 flex justify-center px-4">
      <div
        className="pointer-events-auto flex flex-wrap items-center gap-3 rounded-full border px-4 py-2 shadow-sm backdrop-blur-md"
        style={{
          backgroundColor: "var(--atlas-surface)",
          borderColor: "var(--atlas-border)",
        }}
      >
        <div className="flex items-center gap-2.5 pr-1">
          <span className="font-editorial text-base font-semibold tracking-tight">
            MeanderAtlas
          </span>
          <span
            className="h-3.5 w-px"
            style={{ backgroundColor: "var(--atlas-border)" }}
            aria-hidden="true"
          />
          <span
            data-testid="atlas-health-status"
            className="inline-flex items-center gap-1.5 text-xs font-medium"
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
          className="m-0 flex items-center rounded-full border p-0.5 text-xs"
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
              backgroundColor:
                projection === "mercator" ? "var(--atlas-accent-soft)" : "transparent",
              color: projection === "mercator" ? "var(--atlas-accent)" : "var(--atlas-muted)",
            }}
          >
            <MapIcon className="h-3.5 w-3.5" aria-hidden="true" />
            2D Map
          </button>
        </fieldset>
      </div>
    </header>
  );
}
