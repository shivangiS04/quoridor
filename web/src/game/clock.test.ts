import { describe, expect, it } from "vitest";
import { applyTimedAction, claimTimeout, GAME_TIME_MS, initialState, remainingTime } from "../shared/quoridor.ts";

describe("cumulative server game clocks", () => {
  it("debits moves and walls across turns, leaving the waiting player's balance paused", () => {
    const start = initialState(0);
    const move = applyTimedAction(start, 0, { type: "move", row: 7, col: 4 }, 1000, 31000);
    expect(move.remaining_ms).toEqual([90000, 120000]);
    const wall = applyTimedAction(move, 1, { type: "wall", row: 3, col: 3, horizontal: true }, 31000, 51000);
    expect(wall.remaining_ms).toEqual([90000, 100000]);
    expect(remainingTime(wall, 0, 51000, 61000)).toBe(80000);
    expect(remainingTime(wall, 1, 51000, 61000)).toBe(100000);
    // A reload reads the stored balances and the same timestamp, not a new budget.
    const resumed = JSON.parse(JSON.stringify(wall));
    const next = applyTimedAction(resumed, 0, { type: "move", row: 6, col: 4 }, 51000, 71000);
    expect(next.remaining_ms).toEqual([70000, 100000]);
  });

  it("claims at the exact remaining-time boundary, even when the last turn was less than two minutes", () => {
    const state = { ...initialState(1), remaining_ms: [50000, 1500] };
    expect(() => claimTimeout(state, 0, 1000, 2499)).toThrow("still has time");
    expect(() => claimTimeout(state, 1, 1000, 2500)).toThrow("your turn");
    expect(claimTimeout(state, 0, 1000, 2500)).toMatchObject({ status: "finished", winner: 0, remaining_ms: [50000, 0] });
  });

  it.each([
    { type: "move" as const, row: 7, col: 4 },
    { type: "wall" as const, row: 3, col: 3, horizontal: true },
    { type: "resign" as const },
  ])("an expired player cannot escape by submitting $type", action => {
    const state = { ...initialState(0), remaining_ms: [500, 120000] };
    const result = applyTimedAction(state, 0, action, 1000, 1500);
    expect(result).toMatchObject({ players: state.players, walls: state.walls, status: "finished", winner: 1, remaining_ms: [0, 120000] });
  });

  it("rejects invalid/off-turn actions without mutating clocks and freezes completed games", () => {
    const state = initialState(0);
    expect(() => applyTimedAction(state, 1, { type: "move", row: 1, col: 4 }, 0, 100)).toThrow("Not your turn");
    expect(() => applyTimedAction(state, 0, { type: "move", row: 5, col: 4 }, 0, 100)).toThrow("Invalid move");
    expect(state.remaining_ms).toEqual([GAME_TIME_MS, GAME_TIME_MS]);
    const done = applyTimedAction(state, 1, { type: "resign" }, 0, 100);
    expect(done.remaining_ms).toEqual([119900, 120000]);
    expect(remainingTime(done, 0, 100, 900000)).toBe(119900);
  });

  it("supports old states and guards against a backwards client clock", () => {
    const old = initialState(0);
    delete old.remaining_ms;
    expect(remainingTime(old, 0, 1000, 2000)).toBe(119000);
    expect(remainingTime(old, 0, 1000, 0)).toBe(120000);
  });
});
