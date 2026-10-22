// Runs every migration in an in-memory Postgres (PGlite) with a stand-in for Supabase's auth schema,
// then checks profiles, presence, counters, leaderboard and automatic recording of online results.
//   cd supabase/tests && npm ci && npm test
import { PGlite } from "@electric-sql/pglite";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const migDir = path.join(here, "..", "migrations");
const db = new PGlite();

let failed = 0;
const ok = (cond, msg) => { console.log((cond ? "PASS " : "FAIL ") + msg); if (!cond) failed++; };
const q = async (sql) => (await db.query(sql)).rows;
/** Key-order-independent JSON, so two objects compare equal if they hold the same data. */
const canon = (v) => JSON.stringify(v, (_k, x) => (x && typeof x === "object" && !Array.isArray(x) ? Object.fromEntries(Object.entries(x).sort(([a], [b]) => a.localeCompare(b))) : x));
const id = (n) => `00000000-0000-0000-0000-${String(n).padStart(12, "0")}`;
/** Run SQL as a signed-in player (row-level security and grants apply). */
const as = async (uid, sql) => {
  await db.exec(`SET ROLE authenticated; SELECT set_config('request.jwt.claim.sub','${uid}',false)`);
  try { return await db.query(sql); } finally { await db.exec("RESET ROLE"); }
};
const denied = async (fn) => { try { await fn(); return false; } catch (e) { return /permission denied/i.test(e.message); } };

// ---- stand-in for what Supabase provides ----------------------------------------------------
await db.exec(`
  CREATE ROLE anon NOLOGIN; CREATE ROLE authenticated NOLOGIN; CREATE ROLE service_role NOLOGIN;
  CREATE SCHEMA auth;
  CREATE TABLE auth.users (id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    raw_user_meta_data jsonb DEFAULT '{}'::jsonb, is_anonymous boolean NOT NULL DEFAULT false);
  CREATE FUNCTION auth.uid() RETURNS uuid LANGUAGE sql STABLE AS
    $$ SELECT NULLIF(current_setting('request.jwt.claim.sub', true), '')::uuid $$;
  GRANT USAGE ON SCHEMA auth, public TO anon, authenticated, service_role;
  -- Supabase grants new tables and functions in public to the API roles by default; the migrations must
  -- explicitly take those grants away where they are not wanted, so the stand-in has to behave the same way.
  ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO anon, authenticated, service_role;
  ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT EXECUTE ON FUNCTIONS TO anon, authenticated, service_role;
  CREATE PUBLICATION supabase_realtime;
  -- a user that exists BEFORE the community migration (counter seeding)
  INSERT INTO auth.users (id, is_anonymous) VALUES ('${id(900)}', true);
`);

// ---- apply the migrations in order ----------------------------------------------------------
const files = fs.readdirSync(migDir).filter((f) => f.endsWith(".sql")).sort();
for (const f of files) {
  if (f > "20241002000000_profiles.sql" && f < "20241003000000") continue;
  try { await db.exec(fs.readFileSync(path.join(migDir, f), "utf8")); ok(true, `migration ${f} applies`); }
  catch (e) { ok(false, `migration ${f}: ${e.message}`); process.exit(1); }
  // seed the pre-existing user between migration 2 and 3 so the counter seeding is exercised
  if (f === "20241002000000_profiles.sql") {
    await db.exec(`INSERT INTO auth.users (id, is_anonymous) VALUES ('${id(901)}', true)`);
  }
}

// ---- counters --------------------------------------------------------------------------------
let r = (await q("SELECT total_users, games_played FROM meta_stats"))[0];
ok(r.total_users === 2 && r.games_played === 0, `meta_stats seeded from existing profiles: ${JSON.stringify(r)}`);
await db.exec(`INSERT INTO auth.users (id, is_anonymous) VALUES ('${id(1)}', true), ('${id(2)}', false), ('${id(3)}', true)`);
r = (await q("SELECT total_users FROM meta_stats"))[0];
ok(r.total_users === 5, `each new user adds one to total_users: ${r.total_users}`);
ok(await denied(() => as(id(1), "SELECT * FROM meta_stats")), "clients cannot read meta_stats directly");
ok(await denied(() => as(id(1), "UPDATE meta_stats SET games_played = 9999")), "clients cannot write meta_stats");

// ---- presence --------------------------------------------------------------------------------
await db.exec("UPDATE profiles SET last_seen = now() - interval '1 hour'");
let s = (await as(id(1), "SELECT get_community_stats() AS s")).rows[0].s;
ok(s.online_count === 0 && s.total_users === 5 && s.games_played === 0, `nobody online when everyone is stale: ${JSON.stringify(s)}`);
await as(id(1), "SELECT touch_presence()"); await as(id(2), "SELECT touch_presence()");
s = (await as(id(3), "SELECT get_community_stats() AS s")).rows[0].s;
ok(s.online_count === 2, `two players who sent a heartbeat are online: ${s.online_count}`);
await db.exec(`UPDATE profiles SET last_seen = now() - interval '119 seconds' WHERE id = '${id(3)}'`);
s = (await as(id(1), "SELECT get_community_stats() AS s")).rows[0].s;
ok(s.online_count === 3, "seen 119s ago still counts as online");
await db.exec(`UPDATE profiles SET last_seen = now() - interval '121 seconds' WHERE id = '${id(3)}'`);
s = (await as(id(1), "SELECT get_community_stats() AS s")).rows[0].s;
ok(s.online_count === 2, "seen 121s ago is no longer online");
const before = (await q(`SELECT last_seen FROM profiles WHERE id='${id(2)}'`))[0].last_seen;
await as(id(1), "SELECT touch_presence()");
ok((await q(`SELECT last_seen FROM profiles WHERE id='${id(2)}'`))[0].last_seen.getTime() === before.getTime(), "a heartbeat only touches the caller's own row");
ok(await denied(async () => { await db.exec("SET ROLE anon"); try { await db.exec("SELECT touch_presence()"); } finally { await db.exec("RESET ROLE"); } }), "signed-out visitors cannot send heartbeats");
ok(await denied(async () => { await db.exec("SET ROLE anon"); try { await db.exec("SELECT get_community_stats()"); } finally { await db.exec("RESET ROLE"); } }), "signed-out visitors cannot read community stats");

// ---- automatic recording of online results ---------------------------------------------------
// room A: player index 0 = user 1, index 1 = user 2.
const room = async (n, u0, u1, state) => {
  const rid = id(1000 + n);
  await db.exec(`INSERT INTO rooms (id, code) VALUES ('${rid}', 'CODE${n}')`);
  await db.exec(`INSERT INTO room_members (room_id, player_id, player_index) VALUES ('${rid}', '${u0}', 0), ('${rid}', '${u1}', 1)`);
  await db.exec(`INSERT INTO matches (room_id, state, revision) VALUES ('${rid}', '${JSON.stringify(state)}', 0)`);
  return rid;
};
const playing = { status: "playing", winner: null, current_turn: 0 };
const finish = (rid, winner) => db.exec(`UPDATE matches SET state = '${JSON.stringify({ status: "finished", winner, current_turn: 0 })}', revision = revision + 1 WHERE room_id = '${rid}'`);
const stats = async (u) => (await q(`SELECT games_played, wins, losses, online_wins, ai_wins FROM profiles WHERE id='${u}'`))[0];

const A = await room(1, id(1), id(2), playing);
await db.exec(`UPDATE matches SET revision = 1, state = '${JSON.stringify({ ...playing, current_turn: 1 })}' WHERE room_id = '${A}'`);
ok((await stats(id(1))).games_played === 0, "an ordinary move does not record anything");
await finish(A, 0);
let w = await stats(id(1)), l = await stats(id(2));
ok(w.games_played === 1 && w.wins === 1 && w.online_wins === 1 && w.losses === 0, `winner credited: ${JSON.stringify(w)}`);
ok(l.games_played === 1 && l.losses === 1 && l.wins === 0 && l.online_wins === 0, `loser credited: ${JSON.stringify(l)}`);
ok((await q("SELECT games_played FROM meta_stats"))[0].games_played === 1, "global games_played went up by one");
// finishing again (any later update of the already-finished state) must not double count
await db.exec(`UPDATE matches SET revision = revision + 1, state = '${JSON.stringify({ status: "finished", winner: 0, current_turn: 1 })}' WHERE room_id = '${A}'`);
w = await stats(id(1));
ok(w.games_played === 1 && w.wins === 1, "a second update of a finished match does not double count");
ok((await q("SELECT games_played FROM meta_stats"))[0].games_played === 1, "global counter not double counted either");
// player 1 (index 1) wins another room
const B = await room(2, id(1), id(2), playing); await finish(B, 1);
w = await stats(id(1)); l = await stats(id(2));
ok(w.games_played === 2 && w.wins === 1 && w.losses === 1, `index 1 winner maps to the right user: ${JSON.stringify(w)}`);
ok(l.games_played === 2 && l.wins === 1 && l.online_wins === 1, `…and user 2 now has a win: ${JSON.stringify(l)}`);
// a finished match with no winner (corrupt) is ignored and never errors
const C = await room(3, id(1), id(3), playing);
await db.exec(`UPDATE matches SET state = '${JSON.stringify({ status: "finished", winner: null })}', revision = 9 WHERE room_id = '${C}'`);
ok((await stats(id(3))).games_played === 0, "a finished match without a winner records nothing");
// a recording failure must not fail the update that ended the game
await db.exec("ALTER TABLE profiles ADD CONSTRAINT boom CHECK (games_played < 3) NOT VALID");
const D = await room(4, id(1), id(3), playing);
let threw = false; try { await finish(D, 0); } catch { threw = true; }
ok(!threw, "if stats recording fails the match update still succeeds");
await db.exec("ALTER TABLE profiles DROP CONSTRAINT boom");
// clients cannot finish a match, forge a result, or change/delete game rows. Row-level security makes these
// no-ops (0 rows) or rejects them; either way the match and the stats must be exactly as they were.
const E = await room(5, id(2), id(3), playing);
const statsBefore = JSON.stringify([await stats(id(2)), await stats(id(3))]);
await as(id(2), `UPDATE matches SET state = '{"status":"finished","winner":0}' WHERE room_id = '${E}'`);
await as(id(2), `DELETE FROM matches WHERE room_id = '${E}'`);
await as(id(2), `UPDATE room_members SET player_index = 1 - player_index WHERE room_id = '${E}'`);
let rejected = false; try { await as(id(2), `INSERT INTO matches (room_id, state) VALUES ('${E}', '{}')`); } catch { rejected = true; }
ok((await q(`SELECT state->>'status' AS s FROM matches WHERE room_id='${E}'`))[0]?.s === "playing", "a client cannot finish or delete a match");
ok(rejected, "a client cannot insert a match");
ok(JSON.stringify([await stats(id(2)), await stats(id(3))]) === statsBefore, "and none of that changed anyone's stats");

// ---- room opponent lookup --------------------------------------------------------------------
{
  await db.exec(`UPDATE profiles SET display_name = 'Alice', photo_url = 'https://x/a.png' WHERE id = '${id(1)}'`);
  await db.exec(`UPDATE profiles SET display_name = 'Bob' WHERE id = '${id(2)}'`);
  const rid = id(2000);
  await db.exec(`INSERT INTO rooms (id, code) VALUES ('${rid}', 'OPP1')`);
  await db.exec(`INSERT INTO room_members (room_id, player_id, player_index) VALUES ('${rid}', '${id(1)}', 0)`);
  const opp = async (uid) => (await as(uid, `SELECT get_room_opponent('${rid}') AS o`)).rows[0].o;
  ok((await opp(id(1))) === null, "no opponent yet while the room is still waiting");
  await db.exec(`INSERT INTO room_members (room_id, player_id, player_index) VALUES ('${rid}', '${id(2)}', 1)`);
  const forHost = await opp(id(1)), forGuest = await opp(id(2));
  ok(forHost?.display_name === "Bob" && forHost.photo_url === null, `host sees the guest: ${JSON.stringify(forHost)}`);
  ok(forGuest?.display_name === "Alice" && forGuest.photo_url === "https://x/a.png", `guest sees the host: ${JSON.stringify(forGuest)}`);
  ok(Object.keys(forHost).sort().join() === "display_name,photo_url", "only name and photo are exposed (no ids, no email)");
  ok((await opp(id(3))) === null, "someone who is not in the room learns nothing");
  ok(await denied(async () => { await db.exec("SET ROLE anon"); try { await db.exec(`SELECT get_room_opponent('${rid}')`); } finally { await db.exec("RESET ROLE"); } }), "signed-out visitors cannot call it");
}

// ---- leaderboard -----------------------------------------------------------------------------
await db.exec(`UPDATE profiles SET online_wins = 0`);
await db.exec(`UPDATE profiles SET online_wins = 5, games_played = 9, display_name = 'Five' WHERE id = '${id(1)}'`);
await db.exec(`UPDATE profiles SET online_wins = 5, games_played = 6, display_name = 'FiveFewerGames' WHERE id = '${id(2)}'`);
await db.exec(`UPDATE profiles SET online_wins = 2, games_played = 2, display_name = 'Two' WHERE id = '${id(3)}'`);
let lb = (await as(id(1), "SELECT get_leaderboard() AS lb")).rows[0].lb;
ok(lb.length === 3 && lb[0].display_name === "FiveFewerGames" && lb[1].display_name === "Five" && lb[2].display_name === "Two",
  `ordered by online wins, ties broken by fewer games: ${lb.map((p) => p.display_name).join(" > ")}`);
ok(!("created_at" in lb[0]) && "online_wins" in lb[0] && "photo_url" in lb[0], "leaderboard rows expose only the public fields");
lb = (await as(id(1), "SELECT get_leaderboard(1) AS lb")).rows[0].lb;
ok(lb.length === 1, "limit is respected");
lb = (await as(id(1), "SELECT get_leaderboard(100000) AS lb")).rows[0].lb;
ok(lb.length === 3, "limit is capped (and players without online wins are not listed)");
await db.exec("UPDATE profiles SET online_wins = 0");
lb = (await as(id(1), "SELECT get_leaderboard() AS lb")).rows[0].lb;
ok(Array.isArray(lb) && lb.length === 0, "empty leaderboard is an empty list, not null");
ok(await denied(async () => { await db.exec("SET ROLE anon"); try { await db.exec("SELECT get_leaderboard()"); } finally { await db.exec("RESET ROLE"); } }), "signed-out visitors cannot read the leaderboard");


// ---- quick match -----------------------------------------------------------------------------
{
  for (const n of [11, 12, 13, 14, 15, 16, 17, 18]) await db.exec(`INSERT INTO auth.users (id, is_anonymous) VALUES ('${id(n)}', true)`);
  await db.exec("UPDATE profiles SET last_seen = now() WHERE id IN (" + [11, 12, 13, 14, 15, 16, 17, 18].map((n) => `'${id(n)}'`).join(",") + ")");
  const qm = async (n) => (await as(id(n), "SELECT quick_match() AS r")).rows[0].r;
  const members = async (rid) => q(`SELECT player_id, player_index FROM room_members WHERE room_id='${rid}' ORDER BY player_index`);
  const expectedStart = (turn) => ({ players: [{ row: 8, col: 4, walls_left: 10 }, { row: 0, col: 4, walls_left: 10 }], walls: { horizontal: [], vertical: [] }, current_turn: turn, status: "playing", winner: null });

  const w1 = await qm(11);
  ok(w1.status === "waiting" && w1.player_index === 0 && /^[A-HJ-NP-Z2-9]{8}$/.test(w1.code), `with nobody waiting you open a public room: ${w1.code}`);
  ok((await q(`SELECT is_public FROM rooms WHERE id='${w1.room_id}'`))[0].is_public === true && (await q(`SELECT count(*)::int AS n FROM matches WHERE room_id='${w1.room_id}'`))[0].n === 0, "the waiting room is public and has no match yet");
  ok((await qm(11)).room_id === w1.room_id, "asking again while waiting returns the same room (no duplicates)");

  const j = await qm(12);
  ok(j.status === "started" && j.player_index === 1 && j.room_id === w1.room_id, "the next player is paired into the waiting room as seat 1");
  const mem = await members(w1.room_id);
  ok(mem.length === 2 && mem[0].player_id === id(11) && mem[1].player_id === id(12), "the room has exactly the two players, host in seat 0");
  const m1 = (await q(`SELECT state, revision FROM matches WHERE room_id='${w1.room_id}'`))[0];
  ok(m1.revision === 0 && [0, 1].includes(m1.state.current_turn) && canon({ ...m1.state, current_turn: 0 }) === canon(expectedStart(0)),
    `the match starts from exactly the standard position: ${JSON.stringify(m1.state).slice(0, 100)}…`);

  const w2 = await qm(13);
  ok(w2.status === "waiting" && w2.room_id !== w1.room_id, "a third player does not join a game already in progress; they wait");
  ok((await qm(13)).room_id === w2.room_id, "you are never paired with yourself");

  // a host who has gone quiet (no heartbeat for > 90 s) is not matched
  await db.exec(`UPDATE profiles SET last_seen = now() - interval '5 minutes' WHERE id = '${id(13)}'`);
  const w3 = await qm(14);
  ok(w3.status === "waiting" && w3.room_id !== w2.room_id, "a host who left (stale heartbeat) is skipped");
  // a room that has been waiting too long is not matched, even if the host looks active
  await db.exec(`UPDATE profiles SET last_seen = now() WHERE id = '${id(13)}'`);
  await db.exec(`UPDATE rooms SET created_at = now() - interval '4 minutes' WHERE id = '${w2.room_id}'`);
  await db.exec(`UPDATE profiles SET last_seen = now() WHERE id = '${id(14)}'`);
  const j2 = await qm(15);
  ok(j2.status === "started" && j2.room_id === w3.room_id, "an expired waiting room is skipped; the fresh one is joined");

  // private rooms made with create-room are never picked up
  const privId = id(3000);
  await db.exec(`INSERT INTO rooms (id, code) VALUES ('${privId}', 'PRIVATE1')`);
  await db.exec(`INSERT INTO room_members (room_id, player_id, player_index) VALUES ('${privId}', '${id(16)}', 0)`);
  const j3 = await qm(17);
  ok(j3.room_id !== privId && j3.status === "waiting", "a private (code-only) waiting room is never matched randomly");

  // joining someone else's room removes my own now-pointless waiting room
  const w4 = await qm(18);        // 18 is matched with waiting 17 (17 waits in a public room)
  ok(w4.status === "started" && w4.room_id === j3.room_id, "player 18 joins the waiting player 17");
  const w5 = await qm(11);        // 11 is in a started game but not waiting anywhere: opens a new room
  ok(w5.status === "waiting", "a player whose previous game is running can still open a new waiting room");
  await db.exec(`UPDATE profiles SET last_seen = now() WHERE id = '${id(16)}'`);
  await db.exec(`UPDATE profiles SET last_seen = now() WHERE id IN ('${id(11)}')`);
  const before = (await q(`SELECT count(*)::int AS n FROM rooms WHERE is_public AND id = '${w5.room_id}'`))[0].n;
  await db.exec(`INSERT INTO auth.users (id, is_anonymous) VALUES ('${id(19)}', true)`);
  await db.exec(`UPDATE profiles SET last_seen = now() WHERE id = '${id(19)}'`);
  const w6 = await qm(19);        // 19 takes 11's waiting room
  ok(before === 1 && w6.status === "started" && w6.room_id === w5.room_id, "a late joiner takes the waiting room");

  // cancelling removes my waiting room, but never a started game or someone else's room
  for (const n of [21, 22]) { await db.exec(`INSERT INTO auth.users (id, is_anonymous) VALUES ('${id(n)}', true)`); await db.exec(`UPDATE profiles SET last_seen = now() WHERE id = '${id(n)}'`); }
  const waitA = await qm(21);
  await as(id(22), "SELECT cancel_quick_match()");
  ok((await q(`SELECT count(*)::int AS n FROM rooms WHERE id='${waitA.room_id}'`))[0].n === 1, "cancelling does not touch someone else's waiting room");
  await as(id(21), "SELECT cancel_quick_match()");
  ok((await q(`SELECT count(*)::int AS n FROM rooms WHERE id='${waitA.room_id}'`))[0].n === 0, "cancelling removes my own waiting room");
  const waitB = await qm(21); await qm(22);   // 22 joins 21
  await as(id(21), "SELECT cancel_quick_match()");
  ok((await q(`SELECT count(*)::int AS n FROM rooms WHERE id='${waitB.room_id}'`))[0].n === 1, "cancelling never deletes a game that has started");
  ok(await denied(async () => { await db.exec("SET ROLE anon"); try { await db.exec("SELECT cancel_quick_match()"); } finally { await db.exec("RESET ROLE"); } }), "signed-out visitors cannot cancel");
  ok(await denied(async () => { await db.exec("SET ROLE anon"); try { await db.exec("SELECT quick_match()"); } finally { await db.exec("RESET ROLE"); } }), "signed-out visitors cannot use quick match");
  let threw = false; try { await db.exec("SELECT set_config('request.jwt.claim.sub','',false)"); await db.exec("SET ROLE authenticated"); await db.exec("SELECT quick_match()"); } catch (e) { threw = /Not signed in/.test(e.message); } finally { await db.exec("RESET ROLE"); }
  ok(threw, "without a user id it is refused");
}

// ---- rematch ---------------------------------------------------------------------------------
{
  const rid = id(3100);
  await db.exec(`INSERT INTO rooms (id, code) VALUES ('${rid}', 'REMATCH1')`);
  await db.exec(`INSERT INTO room_members (room_id, player_id, player_index) VALUES ('${rid}', '${id(11)}', 0), ('${rid}', '${id(12)}', 1)`);
  await db.exec(`INSERT INTO matches (room_id, state, revision) VALUES ('${rid}', '{"status":"playing","winner":null,"current_turn":0}', 0)`);
  const rematch = async (n, room) => (await as(id(n), `SELECT request_rematch('${room}') AS r`)).rows[0].r;
  const err = async (n, room) => { try { await rematch(n, room); return null; } catch (e) { return e.message; } };

  ok(/not finished/i.test((await err(11, rid)) ?? ""), "a rematch cannot be requested while the game is still on");
  await db.exec(`UPDATE matches SET state = '{"status":"finished","winner":1,"current_turn":0}', revision = 5 WHERE room_id = '${rid}'`);
  ok(/Not a room member/.test((await err(13, rid)) ?? ""), "someone who was not in the game cannot request a rematch");

  const rooms0 = (await q("SELECT count(*)::int AS n FROM rooms"))[0].n;
  const gamesBefore = (await q(`SELECT games_played FROM profiles WHERE id='${id(11)}'`))[0].games_played;
  const nr = await rematch(11, rid);
  ok(typeof nr === "string" && nr !== rid, "a finished game can be rematched: a new room is created");
  ok((await q("SELECT count(*)::int AS n FROM rooms"))[0].n === rooms0 + 1, "exactly one new room");
  const nm = await q(`SELECT player_id, player_index FROM room_members WHERE room_id = '${nr}' ORDER BY player_index`);
  ok(nm.length === 2 && nm[0].player_id === id(12) && nm[1].player_id === id(11), "seats are swapped: the previous second player is now first");
  const newMatch = (await q(`SELECT state, revision FROM matches WHERE room_id='${nr}'`))[0];
  ok(newMatch.revision === 0 && canon(newMatch.state) === canon({ players: [{ row: 8, col: 4, walls_left: 10 }, { row: 0, col: 4, walls_left: 10 }], walls: { horizontal: [], vertical: [] }, current_turn: 0, status: "playing", winner: null }),
    "the new match starts fresh from exactly the standard position, seat 0 to move");
  ok((await q(`SELECT rematch_room_id FROM matches WHERE room_id='${rid}'`))[0].rematch_room_id === nr, "the old match points at the new room (this is how the other player finds out)");
  ok((await rematch(12, rid)) === nr && (await rematch(11, rid)) === nr, "the other player (and repeats) get the SAME room: no duplicates");
  ok((await q("SELECT count(*)::int AS n FROM rooms"))[0].n === rooms0 + 1, "still exactly one new room after repeated requests");
  ok((await q(`SELECT games_played FROM profiles WHERE id='${id(11)}'`))[0].games_played === gamesBefore, "requesting a rematch does not change anyone's stats");
  ok(/^[A-HJ-NP-Z2-9]{8}$/.test((await q(`SELECT code FROM rooms WHERE id='${nr}'`))[0].code), "the new room has a valid 8-character code");
  const opp = (await as(id(12), `SELECT get_room_opponent('${nr}') AS o`)).rows[0].o;
  ok(opp !== null, "both players can see each other in the new room");
  ok(await denied(async () => { await db.exec("SET ROLE anon"); try { await db.exec(`SELECT request_rematch('${rid}')`); } finally { await db.exec("RESET ROLE"); } }), "signed-out visitors cannot request a rematch");
  ok(/No match/.test((await err(11, id(3999))) ?? "") || /Not a room member/.test((await err(11, id(3999))) ?? ""), "an unknown room is refused");
}

// ---- Phase 1 guarantees still hold -----------------------------------------------------------
ok(await denied(() => as(id(1), "UPDATE profiles SET online_wins = 999 WHERE id = '" + id(1) + "'")), "players still cannot write their own stats");

console.log(failed ? `\n${failed} FAILED` : "\nall passed");
process.exit(failed ? 1 : 0);
