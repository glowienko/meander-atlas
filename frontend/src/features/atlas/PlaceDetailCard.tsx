import { CheckCircle2, Compass, ExternalLink, RotateCcw, X } from "lucide-react";
import { useState } from "react";
import type {
  AccountListFacetView,
  AtlasPlaceView,
  PlaceCategory,
  PlaceListEntryRequest,
  VisitStatus,
} from "../../api/client";
import { CATEGORY_OPTIONS, derivedRoleLabel, visitStatusLabel } from "./atlasLabels";
import { STATUS_COLORS, withAlpha } from "./atlasPalette";
import { PlaceListEditor } from "./PlaceListEditor";

interface PlaceDetailCardProps {
  place: AtlasPlaceView;
  accountLists: readonly AccountListFacetView[];
  isMutating: boolean;
  offsetForStats?: boolean;
  onClose: () => void;
  onUpdateVisitStatus: (place: AtlasPlaceView, nextStatus: VisitStatus) => void;
  onUpdateCategory: (place: AtlasPlaceView, nextCategory: PlaceCategory) => void;
  onUpdateLists: (place: AtlasPlaceView, lists: PlaceListEntryRequest[]) => void;
}

const VISIT_DATE_FORMATTER = new Intl.DateTimeFormat("en", {
  month: "short",
  year: "numeric",
  timeZone: "UTC",
});

function formatIsoMonthYear(raw: string | undefined): string | null {
  if (!raw || raw.trim().length === 0) {
    return null;
  }
  const parsed = new Date(raw);
  if (Number.isNaN(parsed.getTime())) {
    return null;
  }
  return VISIT_DATE_FORMATTER.format(parsed);
}

export function formatVisitDates(
  firstVisitedAt: string | undefined,
  lastVisitedAt: string | undefined,
): string | null {
  const firstLabel = formatIsoMonthYear(firstVisitedAt);
  const lastLabel = formatIsoMonthYear(lastVisitedAt);
  if (!firstLabel && !lastLabel) {
    return null;
  }
  if (firstLabel && lastLabel && firstLabel !== lastLabel) {
    return `First ${firstLabel} · Last ${lastLabel}`;
  }
  return `Visited ${firstLabel ?? lastLabel}`;
}

export function PlaceDetailCard({
  place,
  accountLists,
  isMutating,
  offsetForStats = false,
  onClose,
  onUpdateVisitStatus,
  onUpdateCategory,
  onUpdateLists,
}: PlaceDetailCardProps) {
  const [failedPhotoUrl, setFailedPhotoUrl] = useState<string | null>(null);
  const safeExternalUrl = place.memberships.find(
    (m) => m.sourceUrl && /^https?:\/\//i.test(m.sourceUrl),
  )?.sourceUrl;
  const safePhotoUrl =
    place.photoUrl && /^https:\/\//i.test(place.photoUrl) && failedPhotoUrl !== place.photoUrl
      ? place.photoUrl
      : null;
  const isWikimediaPhoto =
    safePhotoUrl !== null && /^https:\/\/upload\.wikimedia\.org\//i.test(safePhotoUrl);
  const isApproxLocation = place.coordSource === "TAKEOUT_S2_APPROX";

  const locationSummary =
    [place.cityName, place.admin1Name, place.countryCode].filter(Boolean).join(" · ") ||
    `${place.lat.toFixed(4)}, ${place.lng.toFixed(4)}`;
  const visitDates = formatVisitDates(place.firstVisitedAt, place.lastVisitedAt);

  return (
    <article
      data-testid="place-detail-card"
      aria-label={place.canonicalName}
      className={`atlas-panel pointer-events-auto fixed bottom-4 left-4 z-20 w-88 max-w-[calc(100vw-2rem)] overflow-hidden transition-[left] motion-reduce:transition-none ${
        offsetForStats ? "md:left-(--atlas-stats-offset)" : ""
      }`}
    >
      {safePhotoUrl && (
        <div
          data-testid="place-photo-banner"
          className="relative h-36 w-full overflow-hidden bg-black/5"
        >
          <img
            src={safePhotoUrl}
            alt={place.canonicalName}
            loading="lazy"
            decoding="async"
            onError={() => setFailedPhotoUrl(safePhotoUrl)}
            className="h-full w-full object-cover"
          />
          {isWikimediaPhoto && (
            <span className="absolute right-2 bottom-2 rounded-full bg-black/55 px-2 py-0.5 text-[10px] font-medium text-white backdrop-blur-xs">
              Wikimedia
            </span>
          )}
        </div>
      )}

      <div className="p-4">
        <div className="flex items-start justify-between gap-2">
          <div className="min-w-0">
            <h3 className="font-editorial text-lg font-semibold leading-snug">
              {place.canonicalName}
            </h3>
            <p className="mt-0.5 text-[13px] text-atlas-muted">{locationSummary}</p>
            {isApproxLocation && (
              <span
                data-testid="place-approx-location-badge"
                className="mt-1.5 inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-[11px] font-medium"
                style={{
                  borderColor: withAlpha(STATUS_COLORS.UNVISITED, 0.35),
                  backgroundColor: withAlpha(STATUS_COLORS.UNVISITED, 0.08),
                  color: "#9a4a20",
                }}
              >
                <Compass className="h-3 w-3" aria-hidden="true" />
                Approximate location
              </span>
            )}
          </div>

          <button
            type="button"
            aria-label="Close place details"
            onClick={onClose}
            className="atlas-icon-button"
          >
            <X className="h-4 w-4" aria-hidden="true" />
          </button>
        </div>

        <p className="mt-2 text-[13px] font-medium tabular-nums text-atlas-ink">
          <span className="text-atlas-accent">{visitStatusLabel(place.visitStatus)}</span>
          {" · "}
          <span className="text-atlas-muted">{derivedRoleLabel(place.derivedRole)}</span>
          {place.visitCount > 0 && (
            <span className="text-atlas-muted">
              {" · "}
              {place.visitCount} visit{place.visitCount === 1 ? "" : "s"}
            </span>
          )}
        </p>

        <div className="mt-2 flex items-center gap-2 text-[12px] text-atlas-muted">
          <label htmlFor="place-category-select" className="shrink-0 font-medium">
            Category
          </label>
          <select
            id="place-category-select"
            data-testid="place-category-select"
            aria-label="Place category"
            disabled={isMutating}
            value={place.category}
            onChange={(event) => onUpdateCategory(place, event.target.value as PlaceCategory)}
            className="h-7 min-w-0 flex-1 cursor-pointer rounded-full border border-atlas-border bg-white/80 px-2.5 text-[12px] text-atlas-ink outline-none focus:border-atlas-accent disabled:opacity-50"
          >
            {CATEGORY_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
          {place.subcategory && <span className="truncate">{place.subcategory}</span>}
          {place.categorySource === "MANUAL" && (
            <span
              data-testid="place-category-manual-badge"
              className="shrink-0 rounded-full bg-atlas-accent-soft px-2 py-0.5 text-[10px] font-medium text-atlas-accent"
            >
              Manual
            </span>
          )}
        </div>

        {visitDates && (
          <p
            data-testid="place-visit-dates"
            className="mt-1 text-[12px] tabular-nums text-atlas-muted"
          >
            {visitDates}
          </p>
        )}

        <PlaceListEditor
          place={place}
          accountLists={accountLists}
          isMutating={isMutating}
          onUpdateLists={onUpdateLists}
        />

        <div className="mt-3 flex items-center justify-between gap-2">
          {place.visitStatus === "VISITED" ? (
            <button
              type="button"
              disabled={isMutating}
              onClick={() => onUpdateVisitStatus(place, "UNVISITED")}
              className="atlas-pill bg-white/80 py-1.5"
            >
              <RotateCcw className="h-3.5 w-3.5" aria-hidden="true" />
              Mark Unvisited
            </button>
          ) : (
            <button
              type="button"
              disabled={isMutating}
              onClick={() => onUpdateVisitStatus(place, "VISITED")}
              className="atlas-pill-solid py-1.5"
            >
              <CheckCircle2 className="h-3.5 w-3.5" aria-hidden="true" />
              {place.visitStatus === "NEEDS_TRIAGE" ? "Confirm Visited" : "Mark Visited"}
            </button>
          )}

          {safeExternalUrl && (
            <a
              href={safeExternalUrl}
              target="_blank"
              rel="noreferrer"
              className="inline-flex items-center gap-1 text-[13px] font-medium hover:underline text-atlas-accent"
            >
              Google Maps
              <ExternalLink className="h-3.5 w-3.5" aria-hidden="true" />
            </a>
          )}
        </div>
      </div>
    </article>
  );
}
