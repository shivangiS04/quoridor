import { BOARD, type GameState, WALL_GRID } from "./rules.ts";

export interface MoveRecord {
  player: number;
  text: string;
  /** Set when the move placed a wall. */
  wall?: { row: number; col: number; horizontal: boolean };
  gameEnd?: boolean;
}

/** Squares are named like chess: files a–i left to right, ranks 1–9 from player 0's side. */
export const square = (row: number, col: number) => `${String.fromCharCode(97 + col)}${BOARD - row}`;
const notch = (row: number, col: number) => `${String.fromCharCode(97 + col)}${WALL_GRID - row}`;

/** What changed between two consecutive states, or null if nothing did. */
export function describe(old: GameState, next: GameState): MoveRecord | null {
  const mover = old.current_turn;
  const before = old.players[mover], after = next.players[mover];
  if (before.row !== after.row || before.col !== after.col) {
    return { player: mover, text: `${square(before.row, before.col)} → ${square(after.row, after.col)}` };
  }
  const has = (list: { row: number; col: number }[], w: { row: number; col: number }) =>
    list.some((x) => x.row === w.row && x.col === w.col);
  const h = next.walls.horizontal.find((w) => !has(old.walls.horizontal, w));
  if (h) return { player: mover, text: `Wall ${notch(h.row, h.col)} (horizontal)`, wall: { ...h, horizontal: true } };
  const v = next.walls.vertical.find((w) => !has(old.walls.vertical, w));
  if (v) return { player: mover, text: `Wall ${notch(v.row, v.col)} (vertical)`, wall: { ...v, horizontal: false } };
  if (old.status !== "finished" && next.status === "finished") {
    return { player: 1 - (next.winner ?? mover), text: "Game ended (resigned or timed out)", gameEnd: true };
  }
  return null;
}
