import { Check, RotateCcw, X } from "lucide-react";
import { useLayoutEffect, useMemo, useRef, useState } from "react";
import { type AtlasPlaceView, BULK_BATCH_SIZE } from "../../api/client";
import { categoryLabel, visitStatusLabel } from "./atlasLabels";
import { STATUS_TEXT_COLORS, withAlpha } from "./atlasPalette";

export interface PlaceSelectionDialogProps {
  title: string;
  subtitle?: string;
  places: AtlasPlaceView[];
  isSubmitting: boolean;
  onClose: () => void;
  onMarkVisited: (selectedPlaceIds: string[]) => void;
  onMarkUnvisited: (selectedPlaceIds: string[]) => void;
}

function idsWhere(
  places: readonly AtlasPlaceView[],
  predicate: (place: AtlasPlaceView) => boolean,
) {
  return places.filter(predicate).map((place) => place.id);
}

export function PlaceSelectionDialog({
  title,
  subtitle = "Choose places to mark visited or unvisited.",
  places,
  isSubmitting,
  onClose,
  onMarkVisited,
  onMarkUnvisited,
}: PlaceSelectionDialogProps) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const [selectedIds, setSelectedIds] = useState(() => new Set(places.map((place) => place.id)));
  const [filterText, setFilterText] = useState("");

  useLayoutEffect(() => {
    const dialog = dialogRef.current;
    dialog?.showModal();
    return () => dialog?.close();
  }, []);

  const filteredPlaces = useMemo(() => {
    const query = filterText.trim().toLowerCase();
    return places.filter(
      (place) =>
        place.canonicalName.toLowerCase().includes(query) ||
        (place.cityName?.toLowerCase().includes(query) ?? false),
    );
  }, [filterText, places]);

  const checkedUnvisitedIds = idsWhere(
    places,
    (place) => selectedIds.has(place.id) && place.visitStatus !== "VISITED",
  );
  const checkedVisitedIds = idsWhere(
    places,
    (place) => selectedIds.has(place.id) && place.visitStatus === "VISITED",
  );
  const allFilteredSelected =
    filteredPlaces.length > 0 && filteredPlaces.every((place) => selectedIds.has(place.id));

  const toggleId = (id: string) =>
    setSelectedIds((previous) =>
      previous.has(id)
        ? new Set([...previous].filter((selectedId) => selectedId !== id))
        : new Set([...previous, id]),
    );

  const toggleAll = () => {
    const filteredIds = new Set(filteredPlaces.map((place) => place.id));
    setSelectedIds((previous) =>
      allFilteredSelected
        ? new Set([...previous].filter((id) => !filteredIds.has(id)))
        : new Set([...previous, ...filteredIds]),
    );
  };

  const batched = Math.max(checkedUnvisitedIds.length, checkedVisitedIds.length) > BULK_BATCH_SIZE;

  return (
    <dialog
      ref={dialogRef}
      aria-label={title}
      data-testid="bulk-visit-dialog"
      onCancel={(event) => {
        event.preventDefault();
        onClose();
      }}
      className="m-auto max-h-[80vh] w-full max-w-lg flex-col overflow-hidden rounded-2xl border border-atlas-border bg-atlas-bg text-atlas-ink shadow-xl backdrop:bg-black/25 backdrop:backdrop-blur-[2px] open:flex"
    >
      <div className="flex items-center justify-between border-b border-atlas-border px-5 py-3.5">
        <div>
          <h2 className="font-editorial text-lg font-semibold">{title}</h2>
          <p className="text-[13px] text-atlas-muted">{subtitle}</p>
        </div>
        <button
          type="button"
          aria-label="Close place selection checklist"
          onClick={onClose}
          className="atlas-icon-button"
        >
          <X className="h-4 w-4" aria-hidden="true" />
        </button>
      </div>

      <div className="flex items-center justify-between gap-2 border-b border-atlas-border px-5 py-2.5 text-[13px]">
        <input
          type="search"
          aria-label="Filter places in checklist"
          placeholder="Filter checklist..."
          value={filterText}
          onChange={(event) => setFilterText(event.target.value)}
          className="h-8 flex-1 rounded-full border border-atlas-border bg-white/80 px-3 text-[13px] outline-none focus:border-atlas-accent"
        />
        <button type="button" onClick={toggleAll} className="atlas-pill">
          {allFilteredSelected ? "Uncheck all" : "Check all"}
        </button>
      </div>

      <div className="border-b border-atlas-border px-5 py-1.5 text-[12px] text-atlas-muted tabular-nums">
        {selectedIds.size} selected · {checkedUnvisitedIds.length} to mark visited ·{" "}
        {checkedVisitedIds.length} visited
        {batched ? ` · Sent in batches of ${BULK_BATCH_SIZE}` : ""}
      </div>

      <ul className="flex-1 divide-y divide-atlas-border overflow-y-auto px-5 py-2">
        {filteredPlaces.map((place) => {
          const statusColor = STATUS_TEXT_COLORS[place.visitStatus];
          return (
            <li key={place.id}>
              <label className="flex cursor-pointer items-center justify-between gap-3 rounded-lg px-2 py-2.5 text-sm transition-colors hover:bg-white/75">
                <div className="flex min-w-0 items-center gap-2.5">
                  <input
                    type="checkbox"
                    checked={selectedIds.has(place.id)}
                    onChange={() => toggleId(place.id)}
                    className="h-4 w-4 accent-atlas-accent"
                  />
                  <div className="min-w-0">
                    <div className="truncate font-semibold">{place.canonicalName}</div>
                    <div className="truncate text-[13px] text-atlas-muted">
                      {categoryLabel(place.category)}
                      {place.cityName ? ` · ${place.cityName}` : ""}
                    </div>
                  </div>
                </div>
                <span
                  className="shrink-0 rounded-full px-2 py-0.5 text-[11px] font-medium"
                  style={{ backgroundColor: withAlpha(statusColor, 0.12), color: statusColor }}
                >
                  {visitStatusLabel(place.visitStatus)}
                </span>
              </label>
            </li>
          );
        })}
      </ul>

      <div className="flex flex-wrap items-center justify-end gap-2 border-t border-atlas-border px-5 py-3">
        <button type="button" onClick={onClose} className="atlas-pill px-4 py-1.5">
          Cancel
        </button>
        <button
          type="button"
          data-testid="confirm-bulk-unvisited-button"
          disabled={isSubmitting || checkedVisitedIds.length === 0}
          onClick={() => onMarkUnvisited(checkedVisitedIds)}
          className="atlas-pill bg-white/80 px-4 py-1.5"
        >
          <RotateCcw className="h-3.5 w-3.5" aria-hidden="true" />
          Mark {checkedVisitedIds.length} unvisited
        </button>
        <button
          type="button"
          data-testid="confirm-bulk-visit-button"
          disabled={isSubmitting || checkedUnvisitedIds.length === 0}
          onClick={() => onMarkVisited(checkedUnvisitedIds)}
          className="atlas-pill-solid px-4 py-1.5"
        >
          <Check className="h-3.5 w-3.5" aria-hidden="true" />
          Mark {checkedUnvisitedIds.length} as visited
        </button>
      </div>
    </dialog>
  );
}
