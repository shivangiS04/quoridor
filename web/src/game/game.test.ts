import fs from "node:fs";
import { describe, expect, it } from "vitest";
import { chooseAction, DIFFICULTIES } from "./ai.ts";
import { describe as describeMove } from "./history.ts";
import { flipCell, flipNotch, metrics, nearestNotch, slotAt, SIZE, wallBar, wallOrigin } from "./geometry.ts";
import { type Action, apply, getValidMoves, initialState, isWallValid, resign, type GameState } from "./rules.ts";

/** Small deterministic RNG so tests are repeatable. */
function seeded(seed: number) {
  let s = seed >>> 0;
  return () => ((s = (s * 1664525 + 1013904223) >>> 0) / 2 ** 32);
}

function randomPlayer(seed: number) {
  const rng = seeded(seed);
  return (s: GameState): Action => {
    const me = s.current_turn;
    if (s.players[me].walls_left > 0 && rng() < 0.25) {
      const walls: Action[] = [];
      for (let r = 0; r < 8; r++) for (let c = 0; c < 8; c++) for (const horizontal of [true, false]) {
        if (isWallValid(s, me, r, c, horizontal)) walls.push({ type: "wall", row: r, col: c, horizontal });
      }
      if (walls.length) return walls[Math.floor(rng() * walls.length)];
    }
    const moves = getValidMoves(s, me);
    const m = moves[Math.floor(rng() * moves.length)];
    return { type: "move", row: m.row, col: m.col };
  };
}

function play(a0: (s: GameState) => Action, a1: (s: GameState) => Action, maxPlies = 300): GameState {
  let s = initialState(0);
  for (let i = 0; i < maxPlies && s.status === "playing"; i++) {
    const who = s.current_turn;
    const next = apply(s, who, (who === 0 ? a0 : a1)(s));
    if (!next) throw new Error(`illegal action by player ${who}`);
    s = next;
  }
  return s;
}

describe("rules wrapper", () => {
  it("rejects illegal moves and moves out of turn", () => {
    const s = initialState(0);
    expect(apply(s, 0, { type: "move", row: 5, col: 4 })).toBeNull();
    expect(apply(s, 1, { type: "move", row: 1, col: 4 })).toBeNull();
  });
  it("moves the pawn and passes the turn", () => {
    const s = apply(initialState(0), 0, { type: "move", row: 7, col: 4 })!;
    expect(s.players[0].row).toBe(7);
    expect(s.current_turn).toBe(1);
  });
  it("places a wall, spends one, and rejects an overlapping one", () => {
    const s = apply(initialState(0), 0, { type: "wall", row: 3, col: 3, horizontal: true })!;
    expect(s.players[0].walls_left).toBe(9);
    expect(apply(s, 1, { type: "wall", row: 3, col: 4, horizontal: true })).toBeNull();
  });
  it("resigning gives the opponent the win", () => {
    const s = resign(initialState(0), 0);
    expect(s.status).toBe("finished");
    expect(s.winner).toBe(1);
  });
});

describe("the browser uses the exact rules the server uses", () => {
  it("src/shared/quoridor.ts is an up-to-date copy of supabase/functions/_shared/quoridor.ts", () => {
    const original = fs.readFileSync(new URL("../../../supabase/functions/_shared/quoridor.ts", import.meta.url), "utf8");
    const copy = fs.readFileSync(new URL("../shared/quoridor.ts", import.meta.url), "utf8");
    expect(copy.endsWith(original)).toBe(true); // run `npm run sync-rules` if this fails
  });
});

describe("computer opponent", () => {
  it("only plays legal actions, at every difficulty, for a whole game", () => {
    for (const d of DIFFICULTIES) {
      const rng = seeded(1);
      play((s) => chooseAction(s, 0, d, rng), randomPlayer(7));
    }
  });
  it("takes the winning move", () => {
    const base = initialState(1);
    const s: GameState = { ...base, players: [{ ...base.players[0], col: 8 }, { ...base.players[1], row: 7, col: 2 }] };
    for (const d of ["medium", "hard"] as const) {
      expect(chooseAction(s, 1, d, seeded(3))).toEqual({ type: "move", row: 8, col: 2 });
    }
  });
  it("hard beats a random player from either seat", () => {
    const rng = seeded(5);
    expect(play((s) => chooseAction(s, 0, "hard", rng), randomPlayer(11)).winner).toBe(0);
    expect(play(randomPlayer(12), (s) => chooseAction(s, 1, "hard", rng)).winner).toBe(1);
  }, 60_000);
  it("medium beats a random player", () => {
    const rng = seeded(9);
    expect(play((s) => chooseAction(s, 0, "medium", rng), randomPlayer(21)).winner).toBe(0);
  }, 60_000);
  it("hard picks a move quickly (under 2 s) from the opening position", () => {
    const t = performance.now();
    chooseAction(initialState(0), 0, "hard", seeded(4));
    expect(performance.now() - t).toBeLessThan(2000);
  });
});

describe("move history", () => {
  it("names squares like chess and describes pawn moves", () => {
    const old = initialState(0);
    const next = apply(old, 0, { type: "move", row: 7, col: 4 })!;
    expect(describeMove(old, next)).toEqual({ player: 0, text: "e1 → e2" });
  });
  it("describes walls with their position and orientation", () => {
    const old = initialState(0);
    const next = apply(old, 0, { type: "wall", row: 3, col: 2, horizontal: true })!;
    const rec = describeMove(old, next)!;
    expect(rec.text).toBe("Wall c5 (horizontal)");
    expect(rec.wall).toEqual({ row: 3, col: 2, horizontal: true });
  });
  it("records a resignation against the player who resigned", () => {
    const old = initialState(0);
    const rec = describeMove(old, resign(old, 0))!;
    expect(rec.gameEnd).toBe(true);
    expect(rec.player).toBe(0);
  });
  it("returns null when nothing changed", () => {
    expect(describeMove(initialState(0), initialState(0))).toBeNull();
  });
});

describe("board geometry", () => {
  const m = metrics();
  it("cells + gutters fill the board exactly", () => {
    expect(9 * m.cell + 8 * m.gap).toBeCloseTo(SIZE, 6);
  });
  it("a point on a notch centre snaps to that notch", () => {
    const x = (c: number) => c * m.stride + m.cell + m.gap / 2;
    expect(nearestNotch(x(0), x(0))).toEqual({ row: 0, col: 0 });
    expect(nearestNotch(x(5), x(3))).toEqual({ row: 3, col: 5 });
    expect(nearestNotch(x(7), x(7))).toEqual({ row: 7, col: 7 });
  });
  it("points outside the board clamp to the edge notch", () => {
    expect(nearestNotch(-500, -500)).toEqual({ row: 0, col: 0 });
    expect(nearestNotch(5000, 5000)).toEqual({ row: 7, col: 7 });
  });
  it("tells cells from grooves", () => {
    expect(slotAt(m.cell / 2, m.cell / 2)).toEqual({ kind: "cell", row: 0, col: 0 });
    expect(slotAt(m.cell + m.gap / 2, m.cell / 2)).toEqual({ kind: "groove", row: 0, col: 0 });
    expect(slotAt(m.cell + m.gap / 2, m.cell + m.gap / 2)).toEqual({ kind: "groove", row: 0, col: 0 });
    expect(slotAt(SIZE - 1, SIZE - 1)).toEqual({ kind: "cell", row: 8, col: 8 });
  });
});

describe("the board turned for the second player", () => {
  const m = metrics();
  it("flipping twice gives the original coordinates", () => {
    for (let n = 0; n < 9; n++) expect(flipCell(flipCell(n, true), true)).toBe(n);
    for (let n = 0; n < 8; n++) expect(flipNotch(flipNotch(n, true), true)).toBe(n);
    expect(flipCell(3, false)).toBe(3);
    expect(flipNotch(3, false)).toBe(3);
  });
  it("a flipped cell is exactly the cell rotated 180 degrees on the board", () => {
    for (const [r, c] of [[0, 0], [8, 4], [3, 7], [5, 2]]) {
      const x = c * m.stride, y = r * m.stride;
      const fx = flipCell(c, true) * m.stride, fy = flipCell(r, true) * m.stride;
      expect(fx).toBeCloseTo(SIZE - x - m.cell, 6);
      expect(fy).toBeCloseTo(SIZE - y - m.cell, 6);
    }
  });
  it("a flipped wall is exactly the wall rotated 180 degrees (both orientations, every notch)", () => {
    for (const horizontal of [true, false]) {
      const { w, h } = wallBar(horizontal, m);
      for (let r = 0; r < 8; r++) for (let c = 0; c < 8; c++) {
        const o = wallOrigin(r, c, horizontal, m);
        const f = wallOrigin(flipNotch(r, true), flipNotch(c, true), horizontal, m);
        expect(f.x).toBeCloseTo(SIZE - o.x - w, 6);
        expect(f.y).toBeCloseTo(SIZE - o.y - h, 6);
      }
    }
  });
  it("what the player taps on the turned board maps back to the right logical cell and notch", () => {
    // Seat 1 taps the cell drawn at the bottom middle: that is logical row 0, col 4 (their own start).
    const slot = slotAt(4 * m.stride + m.cell / 2, 8 * m.stride + m.cell / 2, m)!;
    expect(slot).toEqual({ kind: "cell", row: 8, col: 4 });
    expect([flipCell(slot.row, true), flipCell(slot.col, true)]).toEqual([0, 4]);
  });
});

describe("the starting position is the same everywhere", () => {
  it("initialState() equals the literal the database uses for quick match and rematch (initial_match_state in the SQL)", () => {
    // If you change this, change initial_match_state() in supabase/migrations/20261027000000_game_clocks.sql too.
    expect(initialState(0)).toEqual({
      players: [{ row: 8, col: 4, walls_left: 10 }, { row: 0, col: 4, walls_left: 10 }],
      walls: { horizontal: [], vertical: [] },
      current_turn: 0, status: "playing", winner: null, remaining_ms: [120000, 120000],
    });
    expect(initialState(1).current_turn).toBe(1);
  });
});
