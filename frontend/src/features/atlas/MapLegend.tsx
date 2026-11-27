import { Info, X } from "lucide-react";
import { type CSSProperties, useState } from "react";
import {
  ACCENT,
  FAVORITE_RING,
  INK,
  MUST_SEE_RING,
  PAPER,
  STATUS_COLORS,
  withAlpha,
} from "./atlasPalette";

interface MapLegendProps {
  dossierOpen: boolean;
}

interface LegendSwatch {
  label: string;
  className: string;
  style: CSSProperties;
}

const REGION_SWATCH = "h-3 w-5 rounded border";
const PIN_SWATCH = "h-3 w-3 rounded-full border";
const RING_SWATCH = "rounded-full border-2";

const LEGEND_SECTIONS: readonly { title: string; swatches: readonly LegendSwatch[] }[] = [
  {
    title: "Regions",
    swatches: [
      {
        label: "Visited (darker = higher %)",
        className: REGION_SWATCH,
        style: {
          background: `linear-gradient(90deg, ${withAlpha(STATUS_COLORS.VISITED, 0.18)}, ${withAlpha(STATUS_COLORS.VISITED, 0.46)})`,
          borderColor: withAlpha(ACCENT, 0.5),
        },
      },
      {
        label: "Bucket list only",
        className: REGION_SWATCH,
        style: {
          backgroundColor: withAlpha(STATUS_COLORS.UNVISITED, 0.14),
          borderColor: withAlpha(STATUS_COLORS.UNVISITED, 0.55),
        },
      },
      {
        label: "No saved places",
        className: REGION_SWATCH,
        style: { backgroundColor: PAPER, borderColor: withAlpha(INK, 0.16) },
      },
    ],
  },
  {
    title: "Pins & Rings",
    swatches: [
      { label: "Visited", className: PIN_SWATCH, style: pinStyle(STATUS_COLORS.VISITED) },
      { label: "Bucket list", className: PIN_SWATCH, style: pinStyle(STATUS_COLORS.UNVISITED) },
      {
        label: "Needs triage",
        className: PIN_SWATCH,
        style: pinStyle(STATUS_COLORS.NEEDS_TRIAGE),
      },
      {
        label: "Must-See / Preferred Hotel",
        className: `h-3.5 w-3.5 ${RING_SWATCH}`,
        style: ringStyle(MUST_SEE_RING),
      },
      { label: "Favorite", className: `h-3 w-3 ${RING_SWATCH}`, style: ringStyle(FAVORITE_RING) },
    ],
  },
];

function pinStyle(color: string): CSSProperties {
  return { backgroundColor: color, borderColor: PAPER };
}

function ringStyle(color: string): CSSProperties {
  return { backgroundColor: PAPER, borderColor: color };
}

export function MapLegend({ dossierOpen }: MapLegendProps) {
  const [open, setOpen] = useState(false);

  return (
    <div
      className={`pointer-events-auto fixed right-4 bottom-4 z-10 flex flex-col items-end gap-2 transition-[right] motion-reduce:transition-none ${
        dossierOpen ? "md:right-(--atlas-dossier-offset)" : ""
      }`}
    >
      {open && (
        <section
          data-testid="map-legend-popover"
          aria-label="Map legend"
          className="atlas-panel w-64 p-3.5 text-[12px]"
        >
          <div className="flex items-center justify-between gap-2 pb-2">
            <h3 className="font-editorial text-sm font-semibold">Map Legend</h3>
            <button
              type="button"
              aria-label="Close map legend"
              onClick={() => setOpen(false)}
              className="atlas-icon-button p-0.5"
            >
              <X className="h-3.5 w-3.5" aria-hidden="true" />
            </button>
          </div>

          {LEGEND_SECTIONS.map((section) => (
            <div key={section.title} className="border-t border-atlas-border py-2">
              <div className="mb-1 text-[11px] font-semibold uppercase tracking-wide text-atlas-muted">
                {section.title}
              </div>
              <ul className="space-y-1.5">
                {section.swatches.map((swatch) => (
                  <li key={swatch.label} className="flex items-center gap-2">
                    <span
                      className={`shrink-0 ${swatch.className}`}
                      style={swatch.style}
                      aria-hidden="true"
                    />
                    <span>{swatch.label}</span>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </section>
      )}

      <button
        type="button"
        data-testid="map-legend-toggle"
        aria-expanded={open}
        onClick={() => setOpen((previous) => !previous)}
        className="atlas-pill bg-atlas-surface py-1.5 shadow-sm backdrop-blur-md hover:bg-white/90"
      >
        <Info className="h-3.5 w-3.5" aria-hidden="true" />
        Legend
      </button>
    </div>
  );
}
