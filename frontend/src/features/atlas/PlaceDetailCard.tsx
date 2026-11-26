import { CheckCircle2, ExternalLink, RotateCcw, X } from "lucide-react";
import type { AtlasPlaceView, VisitStatus } from "../../api/client";
import { categoryLabel, derivedRoleLabel, visitStatusLabel } from "./atlasLabels";

interface PlaceDetailCardProps {
  place: AtlasPlaceView;
  isMutating: boolean;
  offsetForStats?: boolean;
  onClose: () => void;
  onUpdateVisitStatus: (place: AtlasPlaceView, nextStatus: VisitStatus) => void;
}

export function PlaceDetailCard({
  place,
  isMutating,
  offsetForStats = false,
  onClose,
  onUpdateVisitStatus,
}: PlaceDetailCardProps) {
  const safeExternalUrl = place.memberships.find(
    (m) => m.sourceUrl && /^https?:\/\//i.test(m.sourceUrl),
  )?.sourceUrl;

  const locationSummary =
    [place.cityName, place.admin1Name, place.countryCode].filter(Boolean).join(" · ") ||
    `${place.lat.toFixed(4)}, ${place.lng.toFixed(4)}`;
  const categorySummary = `${categoryLabel(place.category)}${place.subcategory ? ` · ${place.subcategory}` : ""}`;

  return (
    <article
      data-testid="place-detail-card"
      aria-label={place.canonicalName}
      className={`pointer-events-auto fixed bottom-4 left-4 z-20 w-88 max-w-[calc(100vw-2rem)] rounded-2xl border p-4 shadow-lg backdrop-blur-md transition-[left] motion-reduce:transition-none ${
        offsetForStats ? "md:left-[22rem]" : ""
      }`}
      style={{
        backgroundColor: "rgba(250, 249, 246, 0.95)",
        borderColor: "var(--atlas-border)",
      }}
    >
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <h3 className="font-editorial text-lg font-semibold leading-snug">
            {place.canonicalName}
          </h3>
          <p className="mt-0.5 text-[13px]" style={{ color: "var(--atlas-muted)" }}>
            {locationSummary}
          </p>
        </div>

        <button
          type="button"
          aria-label="Close place details"
          onClick={onClose}
          className="cursor-pointer rounded-full p-1 hover:bg-black/5"
          style={{ color: "var(--atlas-muted)" }}
        >
          <X className="h-4 w-4" aria-hidden="true" />
        </button>
      </div>

      <p
        className="mt-2 text-[13px] font-medium tabular-nums"
        style={{ color: "var(--atlas-ink)" }}
      >
        <span style={{ color: "var(--atlas-accent)" }}>{visitStatusLabel(place.visitStatus)}</span>
        {" · "}
        <span style={{ color: "var(--atlas-muted)" }}>{derivedRoleLabel(place.derivedRole)}</span>
        {" · "}
        <span>{categorySummary}</span>
        {place.visitCount > 0 && (
          <span style={{ color: "var(--atlas-muted)" }}>
            {" · "}
            {place.visitCount} visit{place.visitCount === 1 ? "" : "s"}
          </span>
        )}
      </p>

      {place.memberships.length > 0 && (
        <ul className="mt-3 divide-y border-t" style={{ borderColor: "var(--atlas-border)" }}>
          {place.memberships.map((m) => (
            <li
              key={`${m.accountLabel}:${m.listName}`}
              className="py-2 text-sm"
              style={{ borderColor: "var(--atlas-border)" }}
            >
              <div className="font-medium">
                {m.accountLabel} · {m.listName}
              </div>
              {m.note && (
                <div className="mt-0.5 text-[13px] italic" style={{ color: "var(--atlas-muted)" }}>
                  {m.note}
                </div>
              )}
            </li>
          ))}
        </ul>
      )}

      <div className="mt-3 flex items-center justify-between gap-2">
        {place.visitStatus === "VISITED" ? (
          <button
            type="button"
            disabled={isMutating}
            onClick={() => onUpdateVisitStatus(place, "UNVISITED")}
            className="inline-flex cursor-pointer items-center gap-1.5 rounded-full border bg-white/80 px-3 py-1.5 text-[13px] font-medium transition-colors hover:bg-black/5 disabled:opacity-50"
            style={{ borderColor: "var(--atlas-border)" }}
          >
            <RotateCcw className="h-3.5 w-3.5" aria-hidden="true" />
            Mark Unvisited
          </button>
        ) : (
          <button
            type="button"
            disabled={isMutating}
            onClick={() => onUpdateVisitStatus(place, "VISITED")}
            className="inline-flex cursor-pointer items-center gap-1.5 rounded-full px-3 py-1.5 text-[13px] font-medium text-white transition-opacity hover:opacity-90 disabled:opacity-50"
            style={{ backgroundColor: "var(--atlas-accent)" }}
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
            className="inline-flex items-center gap-1 text-[13px] font-medium hover:underline"
            style={{ color: "var(--atlas-accent)" }}
          >
            Google Maps
            <ExternalLink className="h-3.5 w-3.5" aria-hidden="true" />
          </a>
        )}
      </div>
    </article>
  );
}
