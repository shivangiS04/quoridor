import {
  applyAction,
  BOARD,
  GAME_TIME_MS,
  type GameState,
  getValidMoves,
  goalRow,
  initialState,
  isWallValid,
  shortestPath,
  TURN_TIMEOUT_MS,
  WALL_GRID,
  WALLS_PER_PLAYER,
} from "../shared/quoridor.ts";

export type { GameState };
export { BOARD, GAME_TIME_MS, TURN_TIMEOUT_MS, WALL_GRID, WALLS_PER_PLAYER, getValidMoves, goalRow, initialState, isWallValid, shortestPath };

export type Action =
  | { type: "move"; row: number; col: number }
  | { type: "wall"; row: number; col: number; horizontal: boolean };

/** Applies an action for `pi`, or returns null if it is illegal (wrong turn, game over, bad move, bad wall). */
export function apply(s: GameState, pi: number, action: Action): GameState | null {
  if (s.status !== "playing" || s.current_turn !== pi) return null;
  if (action.type === "wall" && !isWallValid(s, pi, action.row, action.col, action.horizontal)) return null;
  try {
    return applyAction(s, pi, action);
  } catch {
    return null;
  }
}

/** The state after `pi` resigns (the opponent wins). */
export function resign(s: GameState, pi: number): GameState {
  return { ...s, status: "finished", winner: 1 - pi };
}
