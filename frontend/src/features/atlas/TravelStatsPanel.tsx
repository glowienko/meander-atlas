import { X } from "lucide-react";
import type { ReactNode } from "react";
import type { AtlasStatsView } from "../../api/client";
import { categoryLabel } from "./atlasLabels";

interface TravelStatsPanelProps {
  stats: AtlasStatsView | undefined;
  isLoading: boolean;
  isError: boolean;
  onClose: () => void;
  onFilterCategory: (category: AtlasStatsView["categories"][number]["category"]) => void;
  importSlot?: ReactNode;
}

export function TravelStatsPanel({
  stats,
  isLoading,
  isError,
  onClose,
  onFilterCategory,
  importSlot,
}: TravelStatsPanelProps) {
  return (
    <aside
      data-testid="travel-stats-panel"
      aria-label="Travel statistics"
      className="atlas-panel pointer-events-auto fixed top-28 bottom-4 left-4 z-20 flex w-80 max-w-[calc(100vw-2rem)] flex-col overflow-hidden"
    >
      <div className="flex items-center justify-between border-b px-4 py-3 border-atlas-border">
        <h2 className="font-editorial text-lg font-semibold">Travel atlas stats</h2>
        <button
          type="button"
          aria-label="Close travel stats"
          onClick={onClose}
          className="atlas-icon-button"
        >
          <X className="h-4 w-4" aria-hidden="true" />
        </button>
      </div>

      <div className="flex-1 space-y-4 overflow-y-auto px-4 py-3.5 text-[13px]">
        {importSlot}

        {isLoading && <p className="text-atlas-muted">Loading atlas summary...</p>}
        {isError && <p className="text-atlas-danger">Could not load atlas statistics.</p>}

        {stats && (
          <div className="divide-y divide-atlas-border">
            <div className="grid grid-cols-2 gap-2 pb-3.5">
              <StatMetric label="Mapped places" value={stats.totalMappedPlaces} />
              <StatMetric label="Visited" value={stats.visitedPlaces} accent />
              <StatMetric label="Bucket list" value={stats.unvisitedPlaces} />
              <StatMetric label="Needs triage" value={stats.needsTriagePlaces} />
            </div>

            <div className="space-y-1.5 py-3.5 text-sm">
              <div className="flex items-center justify-between">
                <span className="font-medium">Countries visited</span>
                <span className="font-semibold tabular-nums">
                  {stats.visitedCountries} / {stats.totalSavedCountries}
                </span>
              </div>
              <div className="flex items-center justify-between">
                <span className="font-medium">Admin-1 regions visited</span>
                <span className="font-semibold tabular-nums">
                  {stats.visitedAdmin1Regions} / {stats.totalSavedAdmin1Regions}
                </span>
              </div>
            </div>

            <div className="py-3.5">
              <h3 className="mb-2 text-[13px] font-semibold text-atlas-muted">Categories</h3>
              {stats.categories.length === 0 ? (
                <p className="text-atlas-muted">No categorized places yet.</p>
              ) : (
                <ul className="divide-y divide-atlas-border">
                  {stats.categories.map((cat) => {
                    const pct =
                      cat.totalCount > 0
                        ? Math.round((cat.visitedCount / cat.totalCount) * 100)
                        : 0;
                    return (
                      <li key={cat.category}>
                        <button
                          type="button"
                          onClick={() => onFilterCategory(cat.category)}
                          className="w-full cursor-pointer rounded-lg px-2 py-2 text-left transition-colors hover:bg-white/75"
                        >
                          <div className="flex items-center justify-between text-sm font-medium">
                            <span>{categoryLabel(cat.category)}</span>
                            <span className="text-[13px] tabular-nums text-atlas-muted">
                              {cat.visitedCount}/{cat.totalCount}
                            </span>
                          </div>
                          <div className="mt-1.5 h-1.5 w-full overflow-hidden rounded-full bg-black/5">
                            <div
                              className="h-full rounded-full bg-atlas-accent"
                              style={{ width: `${pct}%` }}
                            />
                          </div>
                        </button>
                      </li>
                    );
                  })}
                </ul>
              )}
            </div>

            {stats.topSubcategories.length > 0 && (
              <div className="pt-3.5">
                <h3 className="mb-1.5 text-[13px] font-semibold text-atlas-muted">
                  Top subcategories
                </h3>
                <p className="text-[13px] leading-relaxed text-atlas-ink">
                  {stats.topSubcategories.map((sub, index) => (
                    <span key={`${sub.category}:${sub.subcategory}`} className="tabular-nums">
                      {index > 0 ? " · " : ""}
                      {sub.subcategory} ({sub.totalCount})
                    </span>
                  ))}
                </p>
              </div>
            )}
          </div>
        )}
      </div>
    </aside>
  );
}

function StatMetric({
  label,
  value,
  accent = false,
}: {
  label: string;
  value: number;
  accent?: boolean;
}) {
  return (
    <div className="rounded-xl bg-white/60 px-3 py-2">
      <div className="text-[13px] text-atlas-muted">{label}</div>
      <div
        className={`font-editorial mt-0.5 text-lg font-semibold tabular-nums ${accent ? "text-atlas-accent" : "text-atlas-ink"}`}
      >
        {value}
      </div>
    </div>
  );
}
