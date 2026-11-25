import { Check, X } from "lucide-react";
import { useMemo, useState } from "react";
import type { AtlasPlaceView } from "../../api/client";
import { categoryLabel } from "./atlasLabels";

interface BulkVisitDialogProps {
  title: string;
  places: AtlasPlaceView[];
  isSubmitting: boolean;
  onClose: () => void;
  onConfirm: (selectedPlaceIds: string[]) => void;
}

export function BulkVisitDialog({
  title,
  places,
  isSubmitting,
  onClose,
  onConfirm,
}: BulkVisitDialogProps) {
  const [selectedIds, setSelectedIds] = useState<Set<string>>(
    () => new Set(places.map((p) => p.id)),
  );
  const [filterText, setFilterText] = useState("");

  const filteredPlaces = useMemo(() => {
    const query = filterText.trim().toLowerCase();
    if (query.length === 0) {
      return places;
    }
    return places.filter(
      (p) =>
        p.canonicalName.toLowerCase().includes(query) ||
        (p.cityName?.toLowerCase().includes(query) ?? false),
    );
  }, [filterText, places]);

  const toggleId = (id: string) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  };

  const toggleAll = () => {
    if (selectedIds.size === places.length) {
      setSelectedIds(new Set());
    } else {
      setSelectedIds(new Set(places.map((p) => p.id)));
    }
  };

  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center bg-black/25 p-4 backdrop-blur-[2px]">
      <div
        role="dialog"
        aria-modal="true"
        aria-label={title}
        data-testid="bulk-visit-dialog"
        className="flex max-h-[80vh] w-full max-w-lg flex-col overflow-hidden rounded-2xl border shadow-xl"
        style={{
          backgroundColor: "var(--atlas-bg)",
          borderColor: "var(--atlas-border)",
        }}
      >
        <div
          className="flex items-center justify-between border-b px-5 py-3.5"
          style={{ borderColor: "var(--atlas-border)" }}
        >
          <div>
            <h2 className="font-editorial text-lg font-semibold">{title}</h2>
            <p className="text-[13px]" style={{ color: "var(--atlas-muted)" }}>
              Uncheck any places you have not visited yet.
            </p>
          </div>
          <button
            type="button"
            aria-label="Close bulk visit checklist"
            onClick={onClose}
            className="cursor-pointer rounded-full p-1 hover:bg-black/5"
            style={{ color: "var(--atlas-muted)" }}
          >
            <X className="h-4 w-4" aria-hidden="true" />
          </button>
        </div>

        <div
          className="flex items-center justify-between gap-2 border-b px-5 py-2.5 text-[13px]"
          style={{ borderColor: "var(--atlas-border)" }}
        >
          <input
            type="search"
            placeholder="Filter checklist..."
            value={filterText}
            onChange={(e) => setFilterText(e.target.value)}
            className="h-8 flex-1 rounded-full border bg-white/80 px-3 text-[13px] outline-none focus:border-[var(--atlas-accent)]"
            style={{ borderColor: "var(--atlas-border)" }}
          />
          <button
            type="button"
            onClick={toggleAll}
            className="cursor-pointer rounded-full border px-3 py-1 font-medium hover:bg-black/5"
            style={{ borderColor: "var(--atlas-border)" }}
          >
            {selectedIds.size === places.length ? "Uncheck all" : "Check all"}
          </button>
        </div>

        <ul
          className="flex-1 divide-y overflow-y-auto px-5 py-2"
          style={{ borderColor: "var(--atlas-border)" }}
        >
          {filteredPlaces.map((place) => {
            const checked = selectedIds.has(place.id);
            return (
              <li key={place.id} style={{ borderColor: "var(--atlas-border)" }}>
                <label className="flex cursor-pointer items-center justify-between gap-3 rounded-lg px-2 py-2.5 text-sm transition-colors hover:bg-white/75">
                  <div className="flex min-w-0 items-center gap-2.5">
                    <input
                      type="checkbox"
                      checked={checked}
                      onChange={() => toggleId(place.id)}
                      className="h-4 w-4 accent-[var(--atlas-accent)]"
                    />
                    <div className="min-w-0">
                      <div className="truncate font-semibold">{place.canonicalName}</div>
                      <div className="truncate text-[13px]" style={{ color: "var(--atlas-muted)" }}>
                        {categoryLabel(place.category)}
                        {place.cityName ? ` · ${place.cityName}` : ""}
                      </div>
                    </div>
                  </div>
                </label>
              </li>
            );
          })}
        </ul>

        <div
          className="flex items-center justify-end gap-2 border-t px-5 py-3"
          style={{ borderColor: "var(--atlas-border)" }}
        >
          <button
            type="button"
            onClick={onClose}
            className="cursor-pointer rounded-full border px-4 py-1.5 text-[13px] font-medium hover:bg-black/5"
            style={{ borderColor: "var(--atlas-border)" }}
          >
            Cancel
          </button>
          <button
            type="button"
            data-testid="confirm-bulk-visit-button"
            disabled={isSubmitting || selectedIds.size === 0}
            onClick={() => onConfirm(Array.from(selectedIds))}
            className="inline-flex cursor-pointer items-center gap-1.5 rounded-full px-4 py-1.5 text-[13px] font-medium text-white transition-opacity hover:opacity-90 disabled:opacity-50"
            style={{ backgroundColor: "var(--atlas-accent)" }}
          >
            <Check className="h-3.5 w-3.5" aria-hidden="true" />
            Mark {selectedIds.size} as visited
          </button>
        </div>
      </div>
    </div>
  );
}
