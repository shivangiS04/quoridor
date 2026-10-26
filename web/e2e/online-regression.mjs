// Deterministic real-browser tests with delayed/rejected responses; no live accounts or backend needed.
// Run against the Vite DEVELOPMENT server: node e2e/online-regression.mjs http://127.0.0.1:5173
import { chromium } from "playwright-core";
import assert from "node:assert/strict";

const base = process.argv[2] ?? "http://127.0.0.1:5173";
const browser = await chromium.launch({ executablePath: process.env.CHROME ?? "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome", headless: true, args: ["--disable-background-timer-throttling", "--disable-renderer-backgrounding"] });
const errors = [];
const supabaseMock = `
export const onlineConfigured = false;
export class ApiError extends Error { constructor(message,status) { super(message); this.status=status; } get isStale() { return this.status===409; } }
export const client = () => ({
  channel: () => {
    let callback;
    const channel = { on(_event,_filter,cb) { callback=cb; return channel; }, subscribe() { window.driver.listeners.add(callback); return channel; }, cleanup() { window.driver.listeners.delete(callback); } };
    return channel;
  }, removeChannel: c => c.cleanup(),
});`;
const apiMock = `
export const submitAction = (_room, revision, action) => window.driver.submit(revision, action);
export const getRoom = async () => { window.driver.reads++; return { match: structuredClone(window.driver.row) }; };
export const claimTimeout = async () => { throw new Error('unexpected claim'); };
export const getRematchRoom = async () => null;
export const requestRematch = async () => 'rematch';`;
async function player(seat) {
  const page = await browser.newPage({ viewport: { width: 390, height: 844 } });
  page.on("pageerror", e => { errors.push(e.message); console.error(e.message); });
  await page.route(/\/src\/lib\/supabase\.ts(?:\?.*)?$/, r => r.fulfill({ contentType: "application/javascript", body: supabaseMock }));
  await page.route(/\/src\/lib\/api\.ts(?:\?.*)?$/, r => r.fulfill({ contentType: "application/javascript", body: apiMock }));
  await page.goto(`${base}/e2e/fixtures/online.html?seat=${seat}`);
  await page.waitForFunction(() => window.game && window.driver.listeners.size === 1);
  return page;
}
const wait = (p, fn) => p.waitForFunction(fn);
const pass = m => console.log(`PASS ${m}`);
try {
  const a = await player(0), b = await player(1);
  const start = await a.evaluate(() => window.driver.row);
  await b.evaluate(row => window.driver.reset(row), start);
  await wait(b, () => window.game.me === 1 && window.driver.listeners.size === 1);

  await a.bringToFront();
  const latency = await a.evaluate(async () => {
    const start = performance.now();
    window.game.moveTo(7, 4);
    // A second input before React renders must not submit twice.
    window.game.moveTo(7, 4);
    while (window.game.game.players[0].row !== 7) await new Promise(requestAnimationFrame);
    return performance.now() - start;
  });
  assert(latency < 250, `predicted pawn render took ${latency} ms`);
  assert.equal(await a.evaluate(() => window.driver.requests.length), 1);
  await new Promise(resolve => setTimeout(resolve, 1500));
  assert(await a.evaluate(() => window.game.busy && window.game.history.length === 0));
  let row = await a.evaluate(() => window.driver.accept());
  await wait(a, () => !window.game.busy && window.game.history.length === 1);
  assert.equal(await a.evaluate(() => window.driver.reads), 0);
  await b.evaluate(row => window.driver.broadcast(row), row);
  await wait(b, () => window.game.game.players[0].row === 7 && window.game.myTurn);
  pass(`pawn rendered in ${latency.toFixed(1)} ms despite a 1500 ms delayed response; opponent synced; no follow-up fetch`);

  await b.evaluate(() => window.game.dropWall(3, 3, true));
  await wait(b, () => window.game.game.walls.horizontal.length === 1 && window.game.busy);
  row = await b.evaluate(() => window.driver.accept(undefined, true));
  await wait(b, () => !window.game.busy && window.game.history.length === 2);
  assert.equal(await b.evaluate(() => window.game.game.players[1].walls_left), 9);
  await a.evaluate(row => window.driver.broadcast(row), row);
  await wait(a, () => window.game.myTurn && window.game.game.walls.horizontal.length === 1);
  pass("wall predicts immediately; Realtime arriving before the response records one move and debits one wall");

  await a.evaluate(() => window.game.moveTo(6, 4));
  await wait(a, () => window.game.game.players[0].row === 6);
  await a.evaluate(() => window.driver.requests.at(-1).reject(new Error("Connection lost")));
  await wait(a, () => !window.game.busy && window.game.game.players[0].row === 7);
  assert.equal(await a.evaluate(() => window.game.history.length), 2);
  assert.equal(await a.evaluate(() => window.driver.reads), 1);
  pass("failed submission resyncs and rolls back the board without adding false history");

  await a.evaluate(() => window.game.moveTo(6, 4));
  await wait(a, () => window.game.busy);
  row = await a.evaluate(() => {
    // The move committed but its HTTP response was lost; Realtime already delivered the row.
    const req = window.driver.requests.at(-1);
    const reject = req.reject;
    req.resolve = () => {};
    const row = window.driver.accept(undefined, true);
    reject(new Error("Response lost"));
    return row;
  });
  await wait(a, () => !window.game.busy && window.game.game.players[0].row === 6);
  assert.equal(await a.evaluate(() => window.game.history.length), 3);
  pass("lost HTTP response retains a move already confirmed by Realtime");

  const raceStart = { ...row, revision: 10, updated_at: new Date().toISOString(), state: { ...row.state, current_turn: 0 } };
  await a.evaluate(row => window.driver.reset(row), raceStart);
  await wait(a, () => window.game.myTurn && !window.game.busy);
  await a.evaluate(() => window.game.moveTo(5, 4));
  await wait(a, () => window.game.busy);
  row = await a.evaluate(() => {
    const req = window.driver.requests.at(-1);
    const resolve = req.resolve;
    req.resolve = () => {};
    const accepted = window.driver.accept(undefined, true);
    const newer = { ...accepted, revision: accepted.revision + 1,
      state: { ...accepted.state, current_turn: 0, players: accepted.state.players.map((p, i) => i === 1 ? { ...p, row: p.row + 1 } : p) } };
    window.driver.broadcast(newer);
    resolve({ ok: true, match: accepted });
    return newer;
  });
  await wait(a, () => !window.game.busy && window.game.myTurn);
  assert.equal(await a.evaluate(() => window.game.history.length), 2);
  pass("a late HTTP response cannot overwrite a newer Realtime revision");

  await a.evaluate(() => window.game.moveTo(4, 4));
  await wait(a, () => window.game.busy);
  await a.evaluate(async () => {
    const { ApiError } = await import("/src/lib/supabase.ts");
    window.driver.requests.at(-1).reject(new ApiError("Stale revision", 409));
  });
  await wait(a, () => !window.game.busy && window.game.game.players[0].row === 5);
  assert.match(await a.evaluate(() => window.game.notice), /board changed/i);
  pass("a stale revision resyncs, rolls back, and allows retry");

  // Reload the hook with server balances. Check the clock when the player returns to turn.
  const clockRow = { ...row, revision: 20, updated_at: new Date().toISOString(), state: { ...row.state, current_turn: 0, remaining_ms: [30000, 45000] } };
  await a.evaluate(row => window.driver.reset(row), clockRow);
  await wait(a, () => window.game.myTurn && window.game.timer.secondsLeft <= 30);
  assert(await a.evaluate(() => window.game.timer.secondsLeft >= 28));
  await a.evaluate(() => window.game.moveTo(4, 4));
  await wait(a, () => window.game.busy);
  row = await a.evaluate(() => window.driver.accept());
  await wait(a, () => !window.game.busy && window.game.timer.secondsLeft <= 45);
  row = { ...row, revision: 22, updated_at: new Date().toISOString(), state: { ...row.state, current_turn: 0, remaining_ms: [row.state.remaining_ms[0], 40000] } };
  await a.evaluate(row => window.driver.broadcast(row), row);
  await wait(a, () => window.game.myTurn);
  assert(await a.evaluate(() => window.game.timer.secondsLeft <= 30 && window.game.timer.secondsLeft >= 27));
  pass("stored budgets survive reload and alternate turns without resetting to two minutes");

  const win = { ...row, revision: 30, updated_at: new Date().toISOString(), state: { ...row.state, current_turn: 0, players: [{ row: 1, col: 0, walls_left: 10 }, { row: 8, col: 8, walls_left: 10 }] } };
  await a.evaluate(row => window.driver.reset(row), win);
  await wait(a, () => window.game.game.players[0].row === 1);
  await a.evaluate(() => window.game.moveTo(0, 0));
  await wait(a, () => window.game.game.players[0].row === 0);
  assert.equal(await a.evaluate(() => window.game.finished), false);
  await a.evaluate(() => window.driver.accept());
  await wait(a, () => window.game.finished && window.game.won);
  pass("a predicted winning move waits for server confirmation before showing the result");
  assert.deepEqual(errors, []);
} finally { await browser.close(); }
