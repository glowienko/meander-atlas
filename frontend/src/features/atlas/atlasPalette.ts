import type { VisitStatus } from "../../api/client";

export const PAPER = "#faf9f6";
export const INK = "#16181d";
export const ACCENT = "#1d5b4c";
export const MUST_SEE_RING = "#d4a017";
export const FAVORITE_RING = "#b84a62";

export const STATUS_COLORS: Readonly<Record<VisitStatus, string>> = {
  VISITED: "#1f6f54",
  UNVISITED: "#c96a38",
  NEEDS_TRIAGE: "#6d597a",
};

export const STATUS_TEXT_COLORS: Readonly<Record<VisitStatus, string>> = {
  ...STATUS_COLORS,
  UNVISITED: "#b55829",
};

export function withAlpha(hex: string, alpha: number): string {
  const rgb = Number.parseInt(hex.slice(1), 16);
  return `rgba(${rgb >> 16}, ${(rgb >> 8) & 255}, ${rgb & 255}, ${alpha})`;
}
