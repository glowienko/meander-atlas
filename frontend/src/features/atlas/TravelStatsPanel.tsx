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
      className="pointer-events-auto fixed top-28 bottom-4 left-4 z-20 flex w-80 max-w-[calc(100vw-2rem)] flex-col overflow-hidden rounded-2xl border shadow-lg backdrop-blur-md"
      style={{
        backgroundColor: "rgba(250, 249, 246, 0.94)",
        borderColor: "var(--atlas-border)",
      }}
    >
      <div
        className="flex items-center justify-between border-b px-4 py-3"
        style={{ borderColor: "var(--atlas-border)" }}
      >
        <h2 className="font-editorial text-lg font-semibold">Travel Atlas Stats</h2>
        <button
          type="button"
          aria-label="Close travel stats"
          onClick={onClose}
          className="cursor-pointer rounded-full p-1 hover:bg-black/5"
          style={{ color: "var(--atlas-muted)" }}
        >
          <X className="h-4 w-4" aria-hidden="true" />
        </button>
      </div>

      <div className="flex-1 space-y-4 overflow-y-auto px-4 py-3.5 text-xs">
        {importSlot}

        {isLoading && <p style={{ color: "var(--atlas-muted)" }}>Loading atlas summary...</p>}
        {isError && (
          <p style={{ color: "var(--atlas-danger)" }}>Could not load atlas statistics.</p>
        )}

        {stats && (
          <>
            <div className="grid grid-cols-2 gap-2">
              <StatCard label="Mapped places" value={stats.totalMappedPlaces} />
              <StatCard label="Visited" value={stats.visitedPlaces} accent />
              <StatCard label="Bucket list" value={stats.unvisitedPlaces} />
              <StatCard label="Needs triage" value={stats.needsTriagePlaces} />
            </div>

            <div
              className="space-y-2 rounded-xl border bg-white/70 p-3"
              style={{ borderColor: "var(--atlas-border)" }}
            >
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

            <div>
              <h3
                className="mb-2 text-[11px] font-semibold uppercase tracking-wider"
                style={{ color: "var(--atlas-muted)" }}
              >
                Categories
              </h3>
              {stats.categories.length === 0 ? (
                <p style={{ color: "var(--atlas-muted)" }}>No categorized places yet.</p>
              ) : (
                <ul className="space-y-1.5">
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
                          className="w-full cursor-pointer rounded-lg border bg-white/60 px-2.5 py-1.5 text-left transition-colors hover:border-[var(--atlas-accent)]"
                          style={{ borderColor: "var(--atlas-border)" }}
                        >
                          <div className="flex items-center justify-between font-medium">
                            <span>{categoryLabel(cat.category)}</span>
                            <span className="tabular-nums" style={{ color: "var(--atlas-muted)" }}>
                              {cat.visitedCount}/{cat.totalCount}
                            </span>
                          </div>
                          <div className="mt-1 h-1.5 w-full overflow-hidden rounded-full bg-black/5">
                            <div
                              className="h-full rounded-full"
                              style={{
                                width: `${pct}%`,
                                backgroundColor: "var(--atlas-accent)",
                              }}
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
              <div>
                <h3
                  className="mb-1.5 text-[11px] font-semibold uppercase tracking-wider"
                  style={{ color: "var(--atlas-muted)" }}
                >
                  Top Subcategories
                </h3>
                <div className="flex flex-wrap gap-1.5">
                  {stats.topSubcategories.map((sub) => (
                    <span
                      key={`${sub.category}:${sub.subcategory}`}
                      className="rounded-full border bg-white/70 px-2.5 py-0.5 text-[11px]"
                      style={{ borderColor: "var(--atlas-border)" }}
                    >
                      {sub.subcategory} · {sub.totalCount}
                    </span>
                  ))}
                </div>
              </div>
            )}
          </>
        )}
      </div>
    </aside>
  );
}

function StatCard({
  label,
  value,
  accent = false,
}: {
  label: string;
  value: number;
  accent?: boolean;
}) {
  return (
    <div
      className="rounded-xl border bg-white/70 px-3 py-2"
      style={{ borderColor: "var(--atlas-border)" }}
    >
      <div className="text-[11px]" style={{ color: "var(--atlas-muted)" }}>
        {label}
      </div>
      <div
        className="font-editorial mt-0.5 text-xl font-semibold tabular-nums"
        style={{ color: accent ? "var(--atlas-accent)" : "var(--atlas-ink)" }}
      >
        {value}
      </div>
    </div>
  );
}
