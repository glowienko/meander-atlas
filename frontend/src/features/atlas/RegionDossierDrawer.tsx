import { CheckCircle2, ChevronRight, MapPin, X } from "lucide-react";
import type { AtlasPlaceView, RegionDossierView, RegionLevel, VisitStatus } from "../../api/client";
import { categoryLabel, derivedRoleLabel, visitStatusLabel } from "./atlasLabels";

interface RegionDossierDrawerProps {
  dossier: RegionDossierView | undefined;
  isLoading: boolean;
  isError: boolean;
  isMutating: boolean;
  activeFilterCount?: number;
  onClearFilters?: () => void;
  onClose: () => void;
  onSelectRegion: (level: RegionLevel, code: string) => void;
  onSelectPlace: (place: AtlasPlaceView) => void;
  onToggleVisitStatus: (place: AtlasPlaceView, nextStatus: VisitStatus) => void;
  onOpenBulkVisit: (places: AtlasPlaceView[], title: string) => void;
}

const RING_RADIUS = 24;
const RING_CIRCUMFERENCE = 2 * Math.PI * RING_RADIUS;

export function RegionDossierDrawer({
  dossier,
  isLoading,
  isError,
  isMutating,
  activeFilterCount,
  onClearFilters,
  onClose,
  onSelectRegion,
  onSelectPlace,
  onToggleVisitStatus,
  onOpenBulkVisit,
}: RegionDossierDrawerProps) {
  const region = dossier?.region;
  const pct = region ? Math.round(region.completionRatio * 100) : 0;
  const dashOffset = RING_CIRCUMFERENCE * (1 - (region?.completionRatio ?? 0));
  const unvisitedCandidates =
    dossier?.places.filter((place) => place.visitStatus !== "VISITED") ?? [];

  return (
    <aside
      data-testid="region-dossier-drawer"
      aria-label="Region dossier"
      className="pointer-events-auto fixed top-28 right-4 bottom-4 z-20 flex w-[23rem] max-w-[calc(100vw-2rem)] flex-col overflow-hidden rounded-2xl border shadow-lg backdrop-blur-md"
      style={{
        backgroundColor: "rgba(250, 249, 246, 0.94)",
        borderColor: "var(--atlas-border)",
      }}
    >
      <div
        className="flex items-start justify-between gap-2 border-b px-4 py-3.5"
        style={{ borderColor: "var(--atlas-border)" }}
      >
        <div className="min-w-0">
          <div
            className="flex items-center gap-1 text-[13px] font-medium"
            style={{ color: "var(--atlas-muted)" }}
          >
            <button type="button" onClick={onClose} className="cursor-pointer hover:underline">
              World
            </button>
            {region && region.level === "ADMIN1" && (
              <>
                <ChevronRight className="h-3.5 w-3.5" aria-hidden="true" />
                <button
                  type="button"
                  onClick={() => onSelectRegion("COUNTRY", region.countryCode)}
                  className="cursor-pointer hover:underline"
                >
                  {region.countryCode}
                </button>
              </>
            )}
            {region && (
              <>
                <ChevronRight className="h-3.5 w-3.5" aria-hidden="true" />
                <span>{region.code}</span>
              </>
            )}
          </div>
          <h2 className="font-editorial mt-0.5 truncate text-lg font-semibold">
            {isLoading ? "Loading region..." : (region?.name ?? "Region dossier")}
          </h2>
        </div>

        <button
          type="button"
          aria-label="Close region dossier"
          onClick={onClose}
          className="cursor-pointer rounded-full p-1 hover:bg-black/5"
          style={{ color: "var(--atlas-muted)" }}
        >
          <X className="h-4 w-4" aria-hidden="true" />
        </button>
      </div>

      <div className="flex-1 overflow-y-auto px-4 py-3.5">
        {isError && (
          <p className="text-[13px]" style={{ color: "var(--atlas-danger)" }}>
            Could not load region dossier.
          </p>
        )}

        {activeFilterCount !== undefined && onClearFilters && (
          <div
            data-testid="dossier-filter-notice"
            className="mb-3 flex items-center justify-between gap-2 rounded-xl border px-3 py-2 text-[12px]"
            style={{
              borderColor: "rgba(201, 106, 56, 0.35)",
              backgroundColor: "rgba(201, 106, 56, 0.08)",
              color: "#7c3b19",
            }}
          >
            <span>
              Map filter active ({activeFilterCount} pin{activeFilterCount === 1 ? "" : "s"} shown)
            </span>
            <button
              type="button"
              onClick={onClearFilters}
              className="shrink-0 cursor-pointer font-semibold underline"
            >
              Show all
            </button>
          </div>
        )}

        {dossier && region && (
          <div className="divide-y" style={{ borderColor: "var(--atlas-border)" }}>
            <div className="flex items-center gap-3.5 rounded-xl bg-white/55 p-3">
              <svg
                data-testid="region-completion-ring"
                width="60"
                height="60"
                viewBox="0 0 60 60"
                className="shrink-0"
                aria-hidden="true"
              >
                <circle
                  cx="30"
                  cy="30"
                  r={RING_RADIUS}
                  fill="none"
                  stroke="rgba(22, 24, 29, 0.1)"
                  strokeWidth="5"
                />
                <circle
                  cx="30"
                  cy="30"
                  r={RING_RADIUS}
                  fill="none"
                  stroke="#1f6f54"
                  strokeWidth="5"
                  strokeLinecap="round"
                  strokeDasharray={RING_CIRCUMFERENCE}
                  strokeDashoffset={dashOffset}
                  transform="rotate(-90 30 30)"
                />
                <text
                  x="30"
                  y="34"
                  textAnchor="middle"
                  fontSize="12"
                  fontWeight="600"
                  fill="#16181d"
                >
                  {pct}%
                </text>
              </svg>

              <div className="min-w-0 flex-1">
                <div className="text-sm font-semibold tabular-nums">
                  {region.visitedPlaces} of {region.totalPlaces} visited ({pct}%)
                </div>
                <div className="mt-0.5 text-[13px]" style={{ color: "var(--atlas-muted)" }}>
                  {region.unvisitedPlaces} bucket list · {region.needsTriagePlaces} triage
                </div>
                {unvisitedCandidates.length > 0 && (
                  <button
                    type="button"
                    data-testid="dossier-bulk-visit-button"
                    disabled={isMutating}
                    onClick={() =>
                      onOpenBulkVisit(unvisitedCandidates, `Mark ${region.name} places visited`)
                    }
                    className="mt-2 inline-flex cursor-pointer items-center gap-1 rounded-full px-3 py-1 text-[13px] font-medium text-white transition-opacity hover:opacity-90 disabled:opacity-50"
                    style={{ backgroundColor: "var(--atlas-accent)" }}
                  >
                    <CheckCircle2 className="h-3.5 w-3.5" aria-hidden="true" />
                    Mark {unvisitedCandidates.length} unvisited as visited
                  </button>
                )}
              </div>
            </div>

            {dossier.childRegions.length > 0 && (
              <div className="py-3.5" style={{ borderColor: "var(--atlas-border)" }}>
                <h3
                  className="mb-2 text-[13px] font-semibold"
                  style={{ color: "var(--atlas-muted)" }}
                >
                  Admin-1 regions ({dossier.childRegions.length})
                </h3>
                <div className="flex flex-wrap gap-1.5">
                  {dossier.childRegions.map((child) => (
                    <button
                      key={child.id}
                      type="button"
                      onClick={() => onSelectRegion("ADMIN1", child.code)}
                      className="cursor-pointer rounded-full border bg-white/75 px-2.5 py-1 text-[13px] tabular-nums transition-colors hover:border-[var(--atlas-accent)]"
                      style={{ borderColor: "var(--atlas-border)" }}
                    >
                      {child.name} ({child.visitedPlaces}/{child.totalPlaces})
                    </button>
                  ))}
                </div>
              </div>
            )}

            <div className="pt-3.5" style={{ borderColor: "var(--atlas-border)" }}>
              <h3
                className="mb-1.5 text-[13px] font-semibold"
                style={{ color: "var(--atlas-muted)" }}
              >
                Saved places ({dossier.places.length})
              </h3>
              {dossier.places.length === 0 ? (
                <p className="text-[13px]" style={{ color: "var(--atlas-muted)" }}>
                  No mapped places saved in this region yet.
                </p>
              ) : (
                <ul className="divide-y" style={{ borderColor: "var(--atlas-border)" }}>
                  {dossier.places.map((place) => {
                    const nextStatus: VisitStatus =
                      place.visitStatus === "VISITED" ? "UNVISITED" : "VISITED";
                    const firstNote = place.memberships.find((m) => m.note)?.note;
                    return (
                      <li
                        key={place.id}
                        className="py-2.5"
                        style={{ borderColor: "var(--atlas-border)" }}
                      >
                        <div className="flex items-start justify-between gap-2">
                          <button
                            type="button"
                            onClick={() => onSelectPlace(place)}
                            className="min-w-0 flex-1 cursor-pointer text-left text-sm font-semibold hover:underline"
                          >
                            {place.canonicalName}
                          </button>
                          <button
                            type="button"
                            disabled={isMutating}
                            onClick={() => onToggleVisitStatus(place, nextStatus)}
                            className="shrink-0 cursor-pointer rounded-full border px-2.5 py-0.5 text-[13px] font-medium transition-colors hover:bg-black/5 disabled:opacity-50"
                            style={{
                              borderColor: "var(--atlas-border)",
                              color:
                                place.visitStatus === "VISITED"
                                  ? "var(--atlas-accent)"
                                  : "var(--atlas-ink)",
                            }}
                          >
                            {place.visitStatus === "VISITED" ? "Visited ✓" : "Mark visited"}
                          </button>
                        </div>

                        <div
                          className="mt-1 flex flex-wrap items-center gap-1.5 text-[13px]"
                          style={{ color: "var(--atlas-muted)" }}
                        >
                          <span>{categoryLabel(place.category)}</span>
                          {place.subcategory && <span>· {place.subcategory}</span>}
                          <span>· {derivedRoleLabel(place.derivedRole)}</span>
                          <span>· {visitStatusLabel(place.visitStatus)}</span>
                        </div>

                        {place.cityName && (
                          <div
                            className="mt-1 flex items-center gap-1 text-[13px]"
                            style={{ color: "var(--atlas-muted)" }}
                          >
                            <MapPin className="h-3.5 w-3.5" aria-hidden="true" />
                            <span>{place.cityName}</span>
                          </div>
                        )}

                        {firstNote && (
                          <p
                            className="mt-1 line-clamp-2 text-[13px] italic"
                            style={{ color: "var(--atlas-muted)" }}
                          >
                            {firstNote}
                          </p>
                        )}
                      </li>
                    );
                  })}
                </ul>
              )}
            </div>
          </div>
        )}
      </div>
    </aside>
  );
}
