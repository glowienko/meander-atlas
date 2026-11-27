import { Info, X } from "lucide-react";
import { useState } from "react";

interface MapLegendProps {
  dossierOpen: boolean;
}

export function MapLegend({ dossierOpen }: MapLegendProps) {
  const [open, setOpen] = useState(false);

  return (
    <div
      className={`pointer-events-auto fixed bottom-4 z-10 flex flex-col items-end gap-2 transition-[right] motion-reduce:transition-none ${
        dossierOpen ? "right-4 md:right-[25rem]" : "right-4"
      }`}
    >
      {open && (
        <section
          data-testid="map-legend-popover"
          aria-label="Map legend"
          className="w-64 rounded-2xl border p-3.5 text-[12px] shadow-lg backdrop-blur-md"
          style={{
            backgroundColor: "rgba(250, 249, 246, 0.95)",
            borderColor: "var(--atlas-border)",
          }}
        >
          <div className="flex items-center justify-between gap-2 pb-2">
            <h3 className="font-editorial text-sm font-semibold">Map Legend</h3>
            <button
              type="button"
              aria-label="Close map legend"
              onClick={() => setOpen(false)}
              className="cursor-pointer rounded-full p-0.5 hover:bg-black/5"
              style={{ color: "var(--atlas-muted)" }}
            >
              <X className="h-3.5 w-3.5" aria-hidden="true" />
            </button>
          </div>

          <div
            className="space-y-2.5 border-t pt-2.5"
            style={{ borderColor: "var(--atlas-border)" }}
          >
            <div>
              <div
                className="mb-1 text-[11px] font-semibold uppercase tracking-wide"
                style={{ color: "var(--atlas-muted)" }}
              >
                Regions
              </div>
              <ul className="space-y-1.5">
                <li className="flex items-center gap-2">
                  <span
                    className="h-3 w-5 shrink-0 rounded border"
                    style={{
                      background:
                        "linear-gradient(90deg, rgba(31, 111, 84, 0.18), rgba(31, 111, 84, 0.46))",
                      borderColor: "rgba(29, 91, 76, 0.5)",
                    }}
                    aria-hidden="true"
                  />
                  <span>Visited (darker = higher %)</span>
                </li>
                <li className="flex items-center gap-2">
                  <span
                    className="h-3 w-5 shrink-0 rounded border"
                    style={{
                      backgroundColor: "rgba(201, 106, 56, 0.14)",
                      borderColor: "rgba(201, 106, 56, 0.55)",
                    }}
                    aria-hidden="true"
                  />
                  <span>Bucket list only</span>
                </li>
                <li className="flex items-center gap-2">
                  <span
                    className="h-3 w-5 shrink-0 rounded border"
                    style={{
                      backgroundColor: "#faf9f6",
                      borderColor: "rgba(22, 24, 29, 0.16)",
                    }}
                    aria-hidden="true"
                  />
                  <span>No saved places</span>
                </li>
              </ul>
            </div>

            <div className="border-t pt-2" style={{ borderColor: "var(--atlas-border)" }}>
              <div
                className="mb-1 text-[11px] font-semibold uppercase tracking-wide"
                style={{ color: "var(--atlas-muted)" }}
              >
                Pins & Rings
              </div>
              <ul className="space-y-1.5">
                <li className="flex items-center gap-2">
                  <span
                    className="h-3 w-3 shrink-0 rounded-full border"
                    style={{ backgroundColor: "#1f6f54", borderColor: "#faf9f6" }}
                    aria-hidden="true"
                  />
                  <span>Visited</span>
                </li>
                <li className="flex items-center gap-2">
                  <span
                    className="h-3 w-3 shrink-0 rounded-full border"
                    style={{ backgroundColor: "#c96a38", borderColor: "#faf9f6" }}
                    aria-hidden="true"
                  />
                  <span>Bucket list</span>
                </li>
                <li className="flex items-center gap-2">
                  <span
                    className="h-3 w-3 shrink-0 rounded-full border"
                    style={{ backgroundColor: "#6d597a", borderColor: "#faf9f6" }}
                    aria-hidden="true"
                  />
                  <span>Needs triage</span>
                </li>
                <li className="flex items-center gap-2">
                  <span
                    className="h-3.5 w-3.5 shrink-0 rounded-full border-2"
                    style={{ backgroundColor: "#faf9f6", borderColor: "#d4a017" }}
                    aria-hidden="true"
                  />
                  <span>Must-See / Preferred Hotel</span>
                </li>
                <li className="flex items-center gap-2">
                  <span
                    className="h-3 w-3 shrink-0 rounded-full border-2"
                    style={{ backgroundColor: "#faf9f6", borderColor: "#b84a62" }}
                    aria-hidden="true"
                  />
                  <span>Favorite</span>
                </li>
              </ul>
            </div>
          </div>
        </section>
      )}

      <button
        type="button"
        data-testid="map-legend-toggle"
        aria-expanded={open}
        onClick={() => setOpen((prev) => !prev)}
        className="inline-flex cursor-pointer items-center gap-1.5 rounded-full border px-3 py-1.5 text-[13px] font-medium shadow-sm backdrop-blur-md transition-colors hover:bg-white/90"
        style={{
          backgroundColor: "var(--atlas-surface)",
          borderColor: "var(--atlas-border)",
          color: "var(--atlas-ink)",
        }}
      >
        <Info className="h-3.5 w-3.5" aria-hidden="true" />
        Legend
      </button>
    </div>
  );
}
