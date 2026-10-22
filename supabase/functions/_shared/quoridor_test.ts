import {
  assert,
  assertEquals,
  assertFalse,
  assertThrows,
} from "jsr:@std/assert@1";
import {
  actionNotAllowedReason,
  applyAction,
  claimTimeout,
  GameState,
  getValidMoves,
  initialState,
  TURN_TIMEOUT_MS,
  WallPos,
} from "./quoridor.ts";

function makeState(o: {
  p0?: [number, number];
  p1?: [number, number];
  turn?: 0 | 1;
  hw?: WallPos[];
  vw?: WallPos[];
  walls0?: number;
} = {}): GameState {
  const [r0, c0] = o.p0 ?? [8, 4];
  const [r1, c1] = o.p1 ?? [0, 4];
  return {
    players: [
      { row: r0, col: c0, walls_left: o.walls0 ?? 10 },
      { row: r1, col: c1, walls_left: 10 },
    ],
    walls: { horizontal: o.hw ?? [], vertical: o.vw ?? [] },
    current_turn: o.turn ?? 0,
    status: "playing",
    winner: null,
  };
}

const has = (moves: { row: number; col: number }[], r: number, c: number) =>
  moves.some((m) => m.row === r && m.col === c);

Deno.test("initial state: players at opposite ends with 10 walls", () => {
  const s = initialState(0);
  assertEquals(s.players[0], { row: 8, col: 4, walls_left: 10 });
  assertEquals(s.players[1], { row: 0, col: 4, walls_left: 10 });
  assertEquals(s.current_turn, 0);
});

Deno.test("player 0 start moves: up, left, right only", () => {
  const m = getValidMoves(makeState(), 0);
  assertEquals(m.length, 3);
  assert(has(m, 7, 4) && has(m, 8, 3) && has(m, 8, 5));
});

Deno.test("horizontal wall blocks downward movement (both halves)", () => {
  assertFalse(
    has(
      getValidMoves(makeState({ p0: [7, 4], hw: [{ row: 7, col: 4 }] }), 0),
      8,
      4,
    ),
  );
  assertFalse(
    has(
      getValidMoves(makeState({ p0: [7, 4], hw: [{ row: 7, col: 3 }] }), 0),
      8,
      4,
    ),
  );
});

Deno.test("vertical wall blocks rightward movement", () => {
  assertFalse(
    has(
      getValidMoves(makeState({ p0: [7, 4], vw: [{ row: 7, col: 4 }] }), 0),
      7,
      5,
    ),
  );
});

Deno.test("straight jump over adjacent opponent", () => {
  const m = getValidMoves(makeState({ p0: [5, 4], p1: [4, 4] }), 0);
  assert(has(m, 3, 4));
  assertFalse(has(m, 4, 4));
});

Deno.test("diagonal jump when straight jump is walled", () => {
  const m = getValidMoves(
    makeState({ p0: [5, 4], p1: [4, 4], hw: [{ row: 3, col: 4 }] }),
    0,
  );
  assertFalse(has(m, 3, 4));
  assert(has(m, 4, 3) && has(m, 4, 5));
});

Deno.test("diagonal jump when straight jump is off the board", () => {
  const m = getValidMoves(makeState({ p0: [1, 4], p1: [0, 4] }), 0);
  assert(has(m, 0, 3) && has(m, 0, 5));
});

Deno.test("move advances state and passes the turn", () => {
  const s = applyAction(makeState(), 0, { type: "move", row: 7, col: 4 });
  assertEquals(s.players[0].row, 7);
  assertEquals(s.current_turn, 1);
  assertEquals(s.status, "playing");
});

Deno.test("illegal move is rejected", () => {
  assertThrows(
    () => applyAction(makeState(), 0, { type: "move", row: 5, col: 4 }),
    Error,
    "Invalid move",
  );
});

Deno.test("reaching the goal row finishes the game", () => {
  const s = applyAction(makeState({ p0: [1, 2], p1: [0, 8] }), 0, {
    type: "move",
    row: 0,
    col: 2,
  });
  assertEquals(s.status, "finished");
  assertEquals(s.winner, 0);
});

Deno.test("player 1 wins on row 8", () => {
  const s = applyAction(makeState({ p0: [3, 3], p1: [7, 5], turn: 1 }), 1, {
    type: "move",
    row: 8,
    col: 5,
  });
  assertEquals(s.status, "finished");
  assertEquals(s.winner, 1);
});

Deno.test("wall placement spends a wall and passes the turn", () => {
  const s = applyAction(makeState(), 0, {
    type: "wall",
    row: 3,
    col: 3,
    horizontal: true,
  });
  assertEquals(s.players[0].walls_left, 9);
  assertEquals(s.walls.horizontal, [{ row: 3, col: 3 }]);
  assertEquals(s.current_turn, 1);
});

Deno.test("wall conflicts: overlap, crossing; non-crossing allowed", () => {
  const base = makeState({ hw: [{ row: 3, col: 3 }] });
  const place = (row: number, col: number, horizontal: boolean) =>
    applyAction(base, 0, { type: "wall", row, col, horizontal });
  assertThrows(() => place(3, 3, true), Error, "conflicts");
  assertThrows(() => place(3, 4, true), Error, "conflicts");
  assertThrows(() => place(3, 2, true), Error, "conflicts");
  assertThrows(() => place(3, 3, false), Error, "conflicts");
  place(3, 5, true);
  place(3, 4, false);
});

Deno.test("wall conflicts: overlapping vertical walls", () => {
  const base = makeState({ vw: [{ row: 3, col: 3 }] });
  assertThrows(
    () =>
      applyAction(base, 0, { type: "wall", row: 4, col: 3, horizontal: false }),
    Error,
    "conflicts",
  );
  assertThrows(
    () =>
      applyAction(base, 0, { type: "wall", row: 2, col: 3, horizontal: false }),
    Error,
    "conflicts",
  );
  applyAction(base, 0, { type: "wall", row: 5, col: 3, horizontal: false });
});

Deno.test("wall out of bounds is rejected", () => {
  for (const [row, col] of [[-1, 0], [0, -1], [8, 0], [0, 8]]) {
    assertThrows(
      () =>
        applyAction(makeState(), 0, {
          type: "wall",
          row,
          col,
          horizontal: true,
        }),
      Error,
      "out of bounds",
    );
  }
});

Deno.test("no walls left is rejected", () => {
  assertThrows(
    () =>
      applyAction(makeState({ walls0: 0 }), 0, {
        type: "wall",
        row: 3,
        col: 3,
        horizontal: true,
      }),
    Error,
    "No walls left",
  );
});

Deno.test("a wall that seals a player in is rejected", () => {
  // Four horizontal walls seal every column but 8 off from row 7; a vertical wall then seals column 8.
  const hw = [0, 2, 4, 6].map((col) => ({ row: 7, col }));
  const s = makeState({ p0: [8, 0], hw });
  assertThrows(
    () =>
      applyAction(s, 0, { type: "wall", row: 7, col: 7, horizontal: false }),
    Error,
    "blocks a player's path",
  );
});

Deno.test("resigning gives the win to the opponent, even off-turn", () => {
  const s = applyAction(makeState({ turn: 1 }), 0, { type: "resign" });
  assertEquals(s.status, "finished");
  assertEquals(s.winner, 1);
});

Deno.test("timeout can only be claimed by the waiting player after the limit", () => {
  const s = makeState({ turn: 1 });
  const t0 = 1_000_000;
  assertThrows(
    () => claimTimeout(s, 1, t0, t0 + TURN_TIMEOUT_MS),
    Error,
    "your turn",
  );
  assertThrows(
    () => claimTimeout(s, 0, t0, t0 + TURN_TIMEOUT_MS - 1),
    Error,
    "still has time",
  );
  const done = claimTimeout(s, 0, t0, t0 + TURN_TIMEOUT_MS);
  assertEquals(done.status, "finished");
  assertEquals(done.winner, 0);
});

Deno.test("timeout cannot be claimed on a finished match", () => {
  const s = {
    ...makeState({ turn: 1 }),
    status: "finished" as const,
    winner: 1,
  };
  assertThrows(
    () => claimTimeout(s, 0, 0, TURN_TIMEOUT_MS * 2),
    Error,
    "not in progress",
  );
});

Deno.test("resigning is allowed on either player's turn, moves and walls only on your own", () => {
  const onTurn0 = makeState({ turn: 0 });
  for (const pi of [0, 1]) {
    assertEquals(actionNotAllowedReason(onTurn0, pi, { type: "resign" }), null);
  }
  assertEquals(actionNotAllowedReason(onTurn0, 0, { type: "move" }), null);
  assertEquals(
    actionNotAllowedReason(onTurn0, 1, { type: "move" }),
    "Not your turn",
  );
  assertEquals(
    actionNotAllowedReason(onTurn0, 1, { type: "wall" }),
    "Not your turn",
  );
});

Deno.test("nothing can be submitted once the match is over, not even a resignation", () => {
  const over = {
    ...makeState({ turn: 0 }),
    status: "finished" as const,
    winner: 1,
  };
  for (const type of ["move", "wall", "resign"]) {
    assertEquals(
      actionNotAllowedReason(over, 0, { type }),
      "Match is not in progress",
    );
  }
});
