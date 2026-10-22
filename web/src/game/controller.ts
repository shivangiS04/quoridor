import type { MoveRecord } from "./history.ts";
import type { GameState } from "./rules.ts";

export interface WallRef { row: number; col: number; horizontal: boolean }

/** Everything the game screen needs, whether the opponent is the computer or a person online. */
export interface GameController {
  game: GameState;
  /** Which seat is yours (0 = bottom/first, 1 = top/second). */
  me: number;
  /** Draw the board turned 180° (seat 1 sees their own pawn at the bottom). */
  flipped: boolean;
  /** Card title, e.g. "Computer · Easy" or the other player's name. */
  opponentTitle: string;
  /** Short name used in sentences, e.g. "Computer" or "Alex". */
  opponentName: string;
  history: MoveRecord[];
  wallOwners: Map<string, number>;
  validMoves: { row: number; col: number }[];
  myTurn: boolean;
  finished: boolean;
  won: boolean;
  /** One line shown instead of the last move while something is going on ("Computer is thinking…"). */
  statusText: string | null;
  wallHorizontal: boolean;
  setWallHorizontal: (horizontal: boolean) => void;
  preview: WallRef | null;
  invalid: (WallRef & { tick: number }) | null;
  /** A message to show under the board (illegal wall, a failed request). */
  notice: string | null;
  busy: boolean;
  wallsUsed: number;
  myTurnsTaken: number;
  elapsedSeconds: number;
  moveTo: (row: number, col: number) => void;
  tapGroove: (row: number, col: number) => void;
  dropWall: (row: number, col: number, horizontal: boolean) => void;
  resign: () => void;
  /** Local games only. */
  playAgain?: () => void;
  /** Online games only: the idle-turn countdown and the claim-win button. */
  timer?: { secondsLeft: number | null; canClaim: boolean; claim: () => void };
}

/** Wall owners, walls spent and turns taken, derived from the move list. */
export function deriveStats(history: MoveRecord[], me: number, wallsLeft: number) {
  const wallOwners = new Map<string, number>();
  for (const r of history) if (r.wall) wallOwners.set(`${r.wall.horizontal ? "h" : "v"}${r.wall.row},${r.wall.col}`, r.player);
  return {
    wallOwners,
    wallsUsed: 10 - wallsLeft,
    myTurnsTaken: history.filter((r) => r.player === me && !r.gameEnd).length,
  };
}
