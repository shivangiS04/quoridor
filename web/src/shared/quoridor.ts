// GENERATED COPY of supabase/functions/_shared/quoridor.ts — do not edit here; run `npm run sync-rules`.
// Shared Quoridor rule engine — mirrors the Kotlin QuoridorRules object.
// Used by submit-action to validate every action server-side.

export const BOARD = 9;
export const WALL_GRID = 8;
export const WALLS_PER_PLAYER = 10;

/** Total thinking time per player for the entire online game. */
export const GAME_TIME_MS = 120_000;
// Kept for clients built against the old name.
export const TURN_TIMEOUT_MS = GAME_TIME_MS;

export interface Pos { row: number; col: number }
export interface WallPos { row: number; col: number }

export interface PlayerState {
  row: number;
  col: number;
  walls_left: number;
}

export interface WallsState {
  horizontal: WallPos[];
  vertical: WallPos[];
}

export interface GameState {
  players: PlayerState[];
  walls: WallsState;
  current_turn: number;
  status: "waiting" | "playing" | "finished";
  winner: number | null;
  /** Stored balances at updated_at; only current_turn runs between actions. Optional for old saves/local games. */
  remaining_ms?: number[];
}

// ---------------------------------------------------------------------------
// Wall-blocking helpers
// Wall at notch (r,c) horizontal: left half blocks (r,c)↔(r+1,c); right half blocks (r,c+1)↔(r+1,c+1)
// Wall at notch (r,c) vertical:   top half blocks  (r,c)↔(r,c+1); bottom half blocks (r+1,c)↔(r+1,c+1)
// ---------------------------------------------------------------------------

function isDownBlocked(s: GameState, r: number, c: number): boolean {
  return s.walls.horizontal.some(w => w.row === r && (w.col === c || w.col === c - 1));
}
function isUpBlocked(s: GameState, r: number, c: number): boolean {
  return s.walls.horizontal.some(w => w.row === r - 1 && (w.col === c || w.col === c - 1));
}
function isRightBlocked(s: GameState, r: number, c: number): boolean {
  return s.walls.vertical.some(w => w.col === c && (w.row === r || w.row === r - 1));
}
function isLeftBlocked(s: GameState, r: number, c: number): boolean {
  return s.walls.vertical.some(w => w.col === c - 1 && (w.row === r || w.row === r - 1));
}

function isMovementBlocked(s: GameState, from: Pos, to: Pos): boolean {
  const dr = to.row - from.row, dc = to.col - from.col;
  if (dr === 1 && dc === 0) return isDownBlocked(s, from.row, from.col);
  if (dr === -1 && dc === 0) return isUpBlocked(s, from.row, from.col);
  if (dr === 0 && dc === 1) return isRightBlocked(s, from.row, from.col);
  if (dr === 0 && dc === -1) return isLeftBlocked(s, from.row, from.col);
  return true;
}

function inBounds(r: number, c: number): boolean {
  return r >= 0 && r < BOARD && c >= 0 && c < BOARD;
}

// ---------------------------------------------------------------------------
// Valid pawn moves
// ---------------------------------------------------------------------------
export function getValidMoves(s: GameState, pi: number): Pos[] {
  const p = s.players[pi];
  const opp = s.players[1 - pi];
  const moves: Pos[] = [];

  const dirs: [number, number][] = [[-1, 0], [1, 0], [0, -1], [0, 1]];
  for (const [dr, dc] of dirs) {
    const nr = p.row + dr, nc = p.col + dc;
    if (!inBounds(nr, nc)) continue;
    if (isMovementBlocked(s, { row: p.row, col: p.col }, { row: nr, col: nc })) continue;

    if (nr === opp.row && nc === opp.col) {
      // Adjacent to opponent — try straight jump
      const jr = nr + dr, jc = nc + dc;
      const straightOk = inBounds(jr, jc) &&
        !isMovementBlocked(s, { row: nr, col: nc }, { row: jr, col: jc });
      if (straightOk) {
        moves.push({ row: jr, col: jc });
      } else {
        // Diagonal jumps (perpendicular to approach direction)
        const perps: [number, number][] = dr !== 0
          ? [[0, -1], [0, 1]]
          : [[-1, 0], [1, 0]];
        for (const [pdr, pdc] of perps) {
          const diagR = nr + pdr, diagC = nc + pdc;
          if (!inBounds(diagR, diagC)) continue;
          if (!isMovementBlocked(s, { row: nr, col: nc }, { row: diagR, col: diagC })) {
            moves.push({ row: diagR, col: diagC });
          }
        }
      }
    } else {
      moves.push({ row: nr, col: nc });
    }
  }
  return moves;
}

// ---------------------------------------------------------------------------
// Wall conflict detection
// ---------------------------------------------------------------------------
function wallHasConflict(
  s: GameState,
  wr: number,
  wc: number,
  horizontal: boolean,
): boolean {
  if (horizontal) {
    // Conflict with other horizontal walls that share a slot (same row, |Δcol| ≤ 1)
    if (s.walls.horizontal.some(w => w.row === wr && Math.abs(w.col - wc) <= 1)) return true;
    // Crossing with vertical wall at same notch
    if (s.walls.vertical.some(w => w.row === wr && w.col === wc)) return true;
  } else {
    // Conflict with other vertical walls (same col, |Δrow| ≤ 1)
    if (s.walls.vertical.some(w => w.col === wc && Math.abs(w.row - wr) <= 1)) return true;
    // Crossing with horizontal wall at same notch
    if (s.walls.horizontal.some(w => w.row === wr && w.col === wc)) return true;
  }
  return false;
}

// ---------------------------------------------------------------------------
// Path check (BFS from player to goal row)
// ---------------------------------------------------------------------------
function hasPath(s: GameState, pi: number): boolean {
  const p = s.players[pi];
  const goalRow = pi === 0 ? 0 : BOARD - 1;
  const visited = Array.from({ length: BOARD }, () => new Array(BOARD).fill(false));
  const queue: Pos[] = [{ row: p.row, col: p.col }];
  visited[p.row][p.col] = true;

  while (queue.length > 0) {
    const cur = queue.shift()!;
    if (cur.row === goalRow) return true;
    for (const [dr, dc] of [[-1, 0], [1, 0], [0, -1], [0, 1]] as [number, number][]) {
      const nr = cur.row + dr, nc = cur.col + dc;
      if (!inBounds(nr, nc) || visited[nr][nc]) continue;
      if (isMovementBlocked(s, cur, { row: nr, col: nc })) continue;
      visited[nr][nc] = true;
      queue.push({ row: nr, col: nc });
    }
  }
  return false;
}

// ---------------------------------------------------------------------------
// Helpers shared with the web client (the computer opponent runs on the same rules).
// ---------------------------------------------------------------------------

/** The row a player must reach to win. */
export function goalRow(pi: number): number {
  return pi === 0 ? 0 : BOARD - 1;
}

/** Whether `pi` may place a wall at notch (wr, wc): has walls left, in bounds, no overlap, and no path is sealed. */
export function isWallValid(
  s: GameState,
  pi: number,
  wr: number,
  wc: number,
  horizontal: boolean,
): boolean {
  if (s.players[pi].walls_left <= 0) return false;
  if (wr < 0 || wr >= WALL_GRID || wc < 0 || wc >= WALL_GRID) return false;
  if (wallHasConflict(s, wr, wc, horizontal)) return false;
  const walls: WallsState = horizontal
    ? { horizontal: [...s.walls.horizontal, { row: wr, col: wc }], vertical: s.walls.vertical }
    : { horizontal: s.walls.horizontal, vertical: [...s.walls.vertical, { row: wr, col: wc }] };
  const spec: GameState = { ...s, walls };
  return hasPath(spec, 0) && hasPath(spec, 1);
}

/** Shortest wall-respecting path (ignoring the other pawn) to the goal row, start included; null if sealed. */
export function shortestPath(s: GameState, pi: number): Pos[] | null {
  const p = s.players[pi];
  const goal = goalRow(pi);
  const key = (r: number, c: number) => r * BOARD + c;
  const parent = new Map<number, number | null>([[key(p.row, p.col), null]]);
  const queue: Pos[] = [{ row: p.row, col: p.col }];
  for (let head = 0; head < queue.length; head++) {
    const cur = queue[head];
    if (cur.row === goal) {
      const path: Pos[] = [];
      let at: number | null = key(cur.row, cur.col);
      while (at !== null) {
        path.push({ row: Math.floor(at / BOARD), col: at % BOARD });
        at = parent.get(at) ?? null;
      }
      return path.reverse();
    }
    for (const [dr, dc] of [[-1, 0], [1, 0], [0, -1], [0, 1]] as [number, number][]) {
      const nr = cur.row + dr, nc = cur.col + dc;
      if (!inBounds(nr, nc) || parent.has(key(nr, nc))) continue;
      if (isMovementBlocked(s, cur, { row: nr, col: nc })) continue;
      parent.set(key(nr, nc), key(cur.row, cur.col));
      queue.push({ row: nr, col: nc });
    }
  }
  return null;
}

// ---------------------------------------------------------------------------
// Apply action and return new state (throws on invalid action)
// ---------------------------------------------------------------------------
export function applyAction(
  s: GameState,
  pi: number,
  action: { type: "move"; row: number; col: number } |
          { type: "wall"; row: number; col: number; horizontal: boolean } |
          { type: "resign" },
): GameState {
  if (action.type === "resign") {
    return { ...s, status: "finished", winner: 1 - pi };
  }
  if (action.type === "move") {
    const valid = getValidMoves(s, pi);
    if (!valid.some(m => m.row === action.row && m.col === action.col)) {
      throw new Error("Invalid move");
    }
    const newPlayers = s.players.map((p, i) =>
      i === pi ? { ...p, row: action.row, col: action.col } : p
    );
    const goalRow = pi === 0 ? 0 : BOARD - 1;
    const won = action.row === goalRow;
    return {
      ...s,
      players: newPlayers,
      current_turn: 1 - pi,
      status: won ? "finished" : "playing",
      winner: won ? pi : null,
    };
  } else {
    // Wall placement
    if (s.players[pi].walls_left <= 0) throw new Error("No walls left");
    const { row: wr, col: wc, horizontal } = action;
    if (wr < 0 || wr >= WALL_GRID || wc < 0 || wc >= WALL_GRID) {
      throw new Error("Wall out of bounds");
    }
    if (wallHasConflict(s, wr, wc, horizontal)) throw new Error("Wall conflicts with existing wall");

    // Speculatively apply and check paths remain open
    const newWalls: WallsState = horizontal
      ? { horizontal: [...s.walls.horizontal, { row: wr, col: wc }], vertical: s.walls.vertical }
      : { horizontal: s.walls.horizontal, vertical: [...s.walls.vertical, { row: wr, col: wc }] };
    const newPlayers = s.players.map((p, i) =>
      i === pi ? { ...p, walls_left: p.walls_left - 1 } : p
    );
    const specState: GameState = { ...s, walls: newWalls, players: newPlayers };

    if (!hasPath(specState, 0) || !hasPath(specState, 1)) {
      throw new Error("Wall blocks a player's path to goal");
    }

    return { ...specState, current_turn: 1 - pi };
  }
}

// ---------------------------------------------------------------------------
// Who may act? Returns an error message, or null when `pi` may submit `action` now.
// Moves and walls need your turn; resigning is allowed at any time while the match is on.
// ---------------------------------------------------------------------------
export function actionNotAllowedReason(
  s: GameState,
  pi: number,
  action: { type: string },
): string | null {
  if (s.status !== "playing") return "Match is not in progress";
  if (action.type !== "resign" && s.current_turn !== pi) return "Not your turn";
  return null;
}

// ---------------------------------------------------------------------------
// Game clock: settle only the active player's elapsed thinking time at the server timestamp.
// ---------------------------------------------------------------------------
export function remainingTime(s: GameState, seat: number, updatedAt: number, now: number): number {
  const balance = s.remaining_ms?.[seat] ?? GAME_TIME_MS;
  return Math.max(0, balance - (s.status === "playing" && s.current_turn === seat ? Math.max(0, now - updatedAt) : 0));
}

export function settleClock(s: GameState, updatedAt: number, now: number): GameState {
  return { ...s, remaining_ms: [0, 1].map(seat => remainingTime(s, seat, updatedAt, now)) };
}

/** Validate, debit the active clock and apply an action. An expired player cannot move to escape timeout. */
export function applyTimedAction(s: GameState, pi: number, action: Parameters<typeof applyAction>[2], updatedAt: number, now: number): GameState {
  const reason = actionNotAllowedReason(s, pi, action);
  if (reason) throw new Error(reason);
  const timed = settleClock(s, updatedAt, now);
  if (timed.remaining_ms![s.current_turn] === 0) {
    return { ...timed, status: "finished", winner: 1 - s.current_turn };
  }
  return applyAction(timed, pi, action);
}

export function claimTimeout(
  s: GameState,
  claimant: number,
  lastMoveAt: number,
  now: number,
): GameState {
  if (s.status !== "playing") throw new Error("Match is not in progress");
  if (s.current_turn === claimant) throw new Error("It is your turn");
  const timed = settleClock(s, lastMoveAt, now);
  if (timed.remaining_ms![s.current_turn] > 0) throw new Error("Opponent still has time");
  return { ...timed, status: "finished", winner: claimant };
}

// ---------------------------------------------------------------------------
// Initial game state
// ---------------------------------------------------------------------------
export function initialState(firstTurn: 0 | 1): GameState {
  return {
    players: [
      { row: 8, col: 4, walls_left: WALLS_PER_PLAYER }, // player 0 starts bottom, goals row 0
      { row: 0, col: 4, walls_left: WALLS_PER_PLAYER }, // player 1 starts top, goals row 8
    ],
    walls: { horizontal: [], vertical: [] },
    current_turn: firstTurn,
    status: "playing",
    winner: null,
    remaining_ms: [GAME_TIME_MS, GAME_TIME_MS],
  };
}
