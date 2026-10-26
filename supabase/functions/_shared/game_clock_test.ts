import { assertEquals, assertThrows } from "jsr:@std/assert@1";
import {
  applyTimedAction,
  claimTimeout,
  initialState,
  remainingTime,
} from "./quoridor.ts";

Deno.test("thinking time accumulates over pawn moves and walls, including resumed games", () => {
  let s = initialState(0);
  s = applyTimedAction(s, 0, { type: "move", row: 7, col: 4 }, 1000, 31000);
  assertEquals(s.remaining_ms, [90000, 120000]);
  s = applyTimedAction(
    s,
    1,
    { type: "wall", row: 3, col: 3, horizontal: true },
    31000,
    51000,
  );
  assertEquals(s.remaining_ms, [90000, 100000]);
  assertEquals(remainingTime(s, 0, 51000, 61000), 80000);
  assertEquals(remainingTime(s, 1, 51000, 61000), 100000);
  const resumed = JSON.parse(JSON.stringify(s));
  s = applyTimedAction(
    resumed,
    0,
    { type: "move", row: 6, col: 4 },
    51000,
    71000,
  );
  assertEquals(s.remaining_ms, [70000, 100000]);
});

Deno.test("timeout uses the remaining budget and an expired player cannot move or place a wall", () => {
  const s = { ...initialState(0), remaining_ms: [1500, 50000] };
  assertThrows(() => claimTimeout(s, 1, 1000, 2499), Error, "still has time");
  const done = claimTimeout(s, 1, 1000, 2500);
  assertEquals(done.remaining_ms, [0, 50000]);
  for (
    const action of [
      { type: "move" as const, row: 7, col: 4 },
      { type: "wall" as const, row: 3, col: 3, horizontal: true },
      { type: "resign" as const },
    ]
  ) {
    const expired = applyTimedAction(s, 0, action, 1000, 2500);
    assertEquals(expired.status, "finished");
    assertEquals(expired.winner, 1);
    assertEquals(expired.players, s.players);
    assertEquals(expired.walls, s.walls);
    assertEquals(expired.remaining_ms, [0, 50000]);
  }
});
