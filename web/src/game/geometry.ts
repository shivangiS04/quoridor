import { BOARD, WALL_GRID } from "./rules.ts";

/** Board units: the SVG is drawn in a 1000×1000 box; a gutter is 14% of a cell. */
export const SIZE = 1000;
export const GAP_FRACTION = 0.14;

export interface Metrics { cell: number; gap: number; stride: number }

export function metrics(total = SIZE): Metrics {
  const gap = (total / (BOARD + (BOARD - 1) * GAP_FRACTION)) * GAP_FRACTION;
  const cell = (total - gap * (BOARD - 1)) / BOARD;
  return { cell, gap, stride: cell + gap };
}

/** Size of a wall piece in board units. */
export function wallBar(horizontal: boolean, m = metrics()) {
  const length = 2 * m.cell + m.gap;
  const thick = m.gap * 1.5;
  return horizontal ? { w: length, h: thick } : { w: thick, h: length };
}

/** Top-left of the bar for a wall at notch (row, col). */
export function wallOrigin(row: number, col: number, horizontal: boolean, m = metrics()) {
  const offset = (m.gap * 1.5 - m.gap) / 2; // the bar is a little thicker than the gutter, centred on it
  return horizontal
    ? { x: col * m.stride, y: row * m.stride + m.cell - offset }
    : { x: col * m.stride + m.cell - offset, y: row * m.stride };
}

/**
 * The wall notch (row, col) closest to a point. Notch (r, c) sits where the gutters between cells
 * (r..r+1, c..c+1) cross; points outside snap to the edge notch.
 */
export function nearestNotch(x: number, y: number, m = metrics()): { row: number; col: number } {
  const clamp = (v: number) => Math.min(WALL_GRID - 1, Math.max(0, v));
  return {
    col: clamp(Math.round((x - m.cell - m.gap / 2) / m.stride)),
    row: clamp(Math.round((y - m.cell - m.gap / 2) / m.stride)),
  };
}

export type Slot =
  | { kind: "cell"; row: number; col: number }
  | { kind: "groove"; row: number; col: number } // a wall notch
  | null;

/** What is under a point on the board: a cell, or a groove (wall notch). */
export function slotAt(x: number, y: number, m = metrics()): Slot {
  const idx = (v: number) => Math.min(BOARD - 1, Math.max(0, Math.floor(v / m.stride)));
  const inCell = (v: number, i: number) => v - i * m.stride <= m.cell;
  const r = idx(y), c = idx(x);
  if (inCell(y, r) && inCell(x, c)) return { kind: "cell", row: r, col: c };
  const gr = inCell(y, r) ? (r === BOARD - 1 ? r - 1 : r) : r;
  const gc = inCell(x, c) ? (c === BOARD - 1 ? c - 1 : c) : c;
  if (gr < 0 || gc < 0 || gr >= WALL_GRID || gc >= WALL_GRID) return null;
  return { kind: "groove", row: gr, col: gc };
}
