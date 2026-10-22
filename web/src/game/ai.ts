import { type Action, apply, type GameState, getValidMoves, isWallValid, shortestPath } from "./rules.ts";

export type Difficulty = "easy" | "medium" | "hard";
export const DIFFICULTIES: Difficulty[] = ["easy", "medium", "hard"];
export const DIFFICULTY_LABEL: Record<Difficulty, string> = { easy: "Easy", medium: "Medium", hard: "Hard" };

const WIN = 10_000;

/**
 * Computer opponent (a port of the Android QuoridorAi, running on the shared rules).
 * - easy: walks its shortest path, but a quarter of the time plays a random legal action
 * - medium: best position score one ply ahead
 * - hard: alpha-beta search three plies deep
 * Position score = (opponent's path length − own path length) × 10 + wall-count difference.
 */
export function chooseAction(state: GameState, me: number, difficulty: Difficulty, rng: () => number = Math.random): Action {
  if (difficulty === "easy") {
    const all = candidates(state, me);
    if (rng() < 0.25) return pick(all, rng);
    return stepAlongPath(state, me) ?? all[0];
  }
  return chooseBySearch(state, me, difficulty === "medium" ? 1 : 3, rng);
}

const pick = <T,>(xs: T[], rng: () => number): T => xs[Math.floor(rng() * xs.length)];

function pathLength(s: GameState, pi: number): number {
  return (shortestPath(s, pi)?.length ?? 82) - 1;
}

function withPawn(s: GameState, pi: number, row: number, col: number): GameState {
  return { ...s, players: s.players.map((p, i) => (i === pi ? { ...p, row, col } : p)) };
}

function stepAlongPath(s: GameState, me: number): Action | null {
  let best: Action | null = null;
  let bestLen = Infinity;
  for (const m of getValidMoves(s, me)) {
    const len = pathLength(withPawn(s, me, m.row, m.col), me);
    if (len < bestLen) {
      bestLen = len;
      best = { type: "move", row: m.row, col: m.col };
    }
  }
  return best;
}

/** Every legal pawn move (best-looking first), plus the walls that cut an edge of the opponent's shortest path. */
function candidates(s: GameState, pi: number): Action[] {
  const actions: Action[] = getValidMoves(s, pi)
    .map((m) => ({ m, len: pathLength(withPawn(s, pi, m.row, m.col), pi) }))
    .sort((a, b) => a.len - b.len)
    .map(({ m }) => ({ type: "move", row: m.row, col: m.col }) as Action);

  if (s.players[pi].walls_left > 0) {
    const path = shortestPath(s, 1 - pi);
    if (!path) return actions;
    const seen = new Set<string>();
    const add = (row: number, col: number, horizontal: boolean) => {
      const key = `${horizontal ? "h" : "v"}${row},${col}`;
      if (seen.has(key)) return;
      seen.add(key);
      if (isWallValid(s, pi, row, col, horizontal)) actions.push({ type: "wall", row, col, horizontal });
    };
    for (let i = 0; i + 1 < path.length; i++) {
      const a = path[i], b = path[i + 1];
      if (a.row !== b.row) { // vertical step: blocked by horizontal walls on the boundary
        const wr = Math.min(a.row, b.row);
        add(wr, a.col, true);
        add(wr, a.col - 1, true);
      } else {
        const wc = Math.min(a.col, b.col);
        add(a.row, wc, false);
        add(a.row - 1, wc, false);
      }
    }
  }
  return actions;
}

function evaluate(s: GameState, me: number): number {
  const opp = 1 - me;
  return (pathLength(s, opp) - pathLength(s, me)) * 10 + (s.players[me].walls_left - s.players[opp].walls_left);
}

function search(s: GameState, me: number, depth: number, alphaIn: number, betaIn: number): number {
  if (s.status === "finished") return s.winner === me ? WIN + depth : -WIN - depth;
  if (depth === 0) return evaluate(s, me);
  let alpha = alphaIn, beta = betaIn;
  const maximizing = s.current_turn === me;
  let best = maximizing ? -Infinity : Infinity;
  for (const action of candidates(s, s.current_turn)) {
    const next = apply(s, s.current_turn, action);
    if (!next) continue;
    const score = search(next, me, depth - 1, alpha, beta);
    if (maximizing) { best = Math.max(best, score); alpha = Math.max(alpha, best); }
    else { best = Math.min(best, score); beta = Math.min(beta, best); }
    if (alpha >= beta) break;
  }
  return best;
}

function chooseBySearch(s: GameState, me: number, depth: number, rng: () => number): Action {
  let best: Action[] = [];
  let bestScore = -Infinity;
  for (const action of candidates(s, me)) {
    const next = apply(s, me, action);
    if (!next) continue;
    const score = search(next, me, depth - 1, -Infinity, Infinity);
    if (score > bestScore) { bestScore = score; best = [action]; }
    else if (score === bestScore) best.push(action);
  }
  return pick(best, rng);
}
