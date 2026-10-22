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

// ---- Phase 1 guarantees still hold -----------------------------------------------------------
ok(await denied(() => as(id(1), "UPDATE profiles SET online_wins = 999 WHERE id = '" + id(1) + "'")), "players still cannot write their own stats");

console.log(failed ? `\n${failed} FAILED` : "\nall passed");
process.exit(failed ? 1 : 0);
