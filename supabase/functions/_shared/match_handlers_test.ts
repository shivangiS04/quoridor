import { assert, assertAlmostEquals, assertEquals } from "jsr:@std/assert@1";
import { handleRequest as submit } from "../submit-action/handler.ts";
import { handleRequest as timeout } from "../claim-timeout/handler.ts";
import { initialState } from "./quoridor.ts";

function backend(
  options: {
    stale?: boolean;
    conflict?: boolean;
    member?: boolean;
    expired?: boolean;
    auth?: boolean;
  } = {},
) {
  const row = {
    state: {
      ...initialState(0),
      remaining_ms: options.expired ? [1000, 50000] : [120000, 120000],
    },
    revision: 4,
    updated_at: new Date(Date.now() - (options.expired ? 2000 : 30000))
      .toISOString(),
  };
  const updates: Record<string, unknown>[] = [];
  const filters: [string, unknown][] = [];
  const fake = {
    auth: {
      getUser: () =>
        Promise.resolve({
          data: { user: options.auth === false ? null : { id: "player" } },
          error: null,
        }),
    },
    from(table: string) {
      let patch: Record<string, unknown> | null = null;
      const query = {
        select(_columns: string) {
          return query;
        },
        eq(key: string, value: unknown) {
          if (patch) filters.push([key, value]);
          return query;
        },
        update(value: Record<string, unknown>) {
          patch = value;
          updates.push(value);
          return query;
        },
        maybeSingle() {
          if (patch) {
            return Promise.resolve({
              data: options.conflict ? null : patch,
              error: null,
            });
          }
          if (table === "room_members") {
            return Promise.resolve({
              data: options.member === false ? null : { player_index: 0 },
              error: null,
            });
          }
          return Promise.resolve({
            data: { ...row, revision: options.stale ? 5 : row.revision },
            error: null,
          });
        },
      };
      return query;
    },
  };
  return {
    row,
    updates,
    filters,
    factory: (() => fake) as unknown as NonNullable<
      Parameters<typeof submit>[1]
    >,
  };
}
const request = (action = { type: "move", row: 7, col: 4 }) =>
  new Request("https://example.test", {
    method: "POST",
    headers: {
      Authorization: "Bearer test",
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ room_id: "room", revision: 4, action }),
  });
const env = () => "test";

Deno.test("submit response includes confirmed board, revision, timestamp and cumulative clocks", async () => {
  const b = backend();
  const response = await submit(request(), b.factory, env);
  assertEquals(response.status, 200);
  const result = await response.json();
  assertEquals(result.match.state.players[0].row, 7);
  assertEquals(result.match.revision, 5);
  assertEquals(result.match.updated_at, b.updates[0].updated_at);
  assertAlmostEquals(result.match.state.remaining_ms[0], 90000, 1000);
  assertEquals(result.match.state.remaining_ms[1], 120000);
  assert(b.filters.some(([key, value]) => key === "revision" && value === 4));
});

Deno.test("zero-row compare-and-swap is a conflict, never a false success", async () => {
  for (const handler of [submit, timeout]) {
    const b = backend({ conflict: true, expired: true });
    // For timeout, claimant must be the waiting seat.
    if (handler === timeout) {
      b.row.state.current_turn = 1;
      b.row.state.remaining_ms = [50000, 1000];
    }
    const response = await handler(request(), b.factory, env);
    assertEquals(response.status, 409);
    assertEquals(b.updates.length, 1);
  }
});

Deno.test("stale, unauthenticated and nonmember requests never write a match", async () => {
  for (
    const [options, status] of [
      [{ stale: true }, 409],
      [{ auth: false }, 401],
      [{ member: false }, 403],
    ] as const
  ) {
    const b = backend(options);
    const response = await submit(request(), b.factory, env);
    assertEquals(response.status, status);
    assertEquals(b.updates.length, 0);
  }
});

Deno.test("expired action finishes the game without moving the pawn", async () => {
  const b = backend({ expired: true });
  const response = await submit(request(), b.factory, env);
  const { match } = await response.json();
  assertEquals(match.state.status, "finished");
  assertEquals(match.state.winner, 1);
  assertEquals(match.state.players[0].row, 8);
  assertEquals(match.state.remaining_ms, [0, 50000]);
});

Deno.test("timeout returns the confirmed match and uses the remaining balance", async () => {
  const b = backend({ expired: true });
  b.row.state.current_turn = 1;
  b.row.state.remaining_ms = [50000, 1000];
  const response = await timeout(request(), b.factory, env);
  assertEquals(response.status, 200);
  const { match } = await response.json();
  assertEquals(match.state.status, "finished");
  assertEquals(match.state.winner, 0);
  assertEquals(match.state.remaining_ms, [50000, 0]);
  assertEquals(match.revision, 5);
});
