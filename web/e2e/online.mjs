// Two real browsers play each other through the real backend.
//   node e2e/online.mjs <baseUrl> <screenshotDir>      (reads VITE_SUPABASE_* from .env.local or the environment)
// Creates throw-away guest accounts named "ZZ web … (delete me)".
import { chromium } from "playwright-core";
import fs from "node:fs";

const base = process.argv[2] ?? "http://127.0.0.1:4173";
const shots = process.argv[3] ?? "shots";
fs.mkdirSync(shots, { recursive: true });
const chrome = process.env.CHROME ?? "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";
const env = { ...Object.fromEntries((fs.existsSync(".env.local") ? fs.readFileSync(".env.local", "utf8") : "").split("\n").filter(Boolean).map((l) => [l.slice(0, l.indexOf("=")), l.slice(l.indexOf("=") + 1)])), ...process.env };
const SUPA = env.VITE_SUPABASE_URL, KEY = env.VITE_SUPABASE_ANON_KEY;

let failed = 0;
const ok = (c, m) => { console.log((c ? "PASS " : "FAIL ") + m); if (!c) failed++; };

const gap = (1000 / (9 + 8 * 0.14)) * 0.14, cell = (1000 - 8 * gap) / 9, stride = cell + gap;
const browser = await chromium.launch({ executablePath: chrome, headless: true });
const errors = [];
async function newPlayer(name) {
  const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2 });
  const page = await ctx.newPage();
  page.on("pageerror", (e) => errors.push(`${name}: ${e.message}`));
  page.on("console", (m) => { if (m.type() === "error" && !/Failed to load resource/.test(m.text())) errors.push(`${name}: ${m.text()}`); });
  return { name, ctx, page };
}
/** Screen position of a LOGICAL cell for a seat (seat 1 sees the board turned). */
async function cellPos(p, seat, r, c) {
  const box = await p.page.locator("svg.board").boundingBox(), k = box.width / 1000;
  const [vr, vc] = seat === 1 ? [8 - r, 8 - c] : [r, c];
  return { x: box.x + (vc * stride + cell / 2) * k, y: box.y + (vr * stride + cell / 2) * k };
}
async function notchPos(p, seat, r, c) {
  const box = await p.page.locator("svg.board").boundingBox(), k = box.width / 1000;
  const [vr, vc] = seat === 1 ? [7 - r, 7 - c] : [r, c];
  return { x: box.x + (vc * stride + cell + gap / 2) * k, y: box.y + (vr * stride + cell + gap / 2) * k };
}
async function guestRename(p, newName) {
  // sets the display name through the same API the profile screen uses, so test accounts are easy to find later
  await p.page.evaluate(async ({ SUPA, KEY, newName }) => {
    const raw = Object.entries(localStorage).find(([k]) => k.startsWith("sb-") && k.endsWith("-auth-token"))?.[1];
    const s = JSON.parse(raw);
    await fetch(`${SUPA}/rest/v1/profiles?id=eq.${s.user.id}`, { method: "PATCH", headers: { apikey: KEY, Authorization: `Bearer ${s.access_token}`, "Content-Type": "application/json" }, body: JSON.stringify({ display_name: newName }) });
  }, { SUPA, KEY, newName });
}

// ------------------------------------------------------------------ the host
const A = await newPlayer("A");
await A.page.goto(base);
ok(await A.page.getByRole("button", { name: "Create room" }).count() === 1, "home offers Create room and a Sign in chip");
await A.page.getByRole("button", { name: "Create room" }).click();
await A.page.getByText("Sign in to play online.").waitFor({ timeout: 8000 });
ok(true, "Create room while signed out asks you to sign in");
await A.page.screenshot({ path: `${shots}/o1-signin.png` });
await A.page.getByRole("button", { name: "Play as guest" }).click();
await A.page.getByText("Waiting for an opponent").waitFor({ timeout: 20000 });
const code = (await A.page.locator(".code").innerText()).trim();
ok(/^[A-Z0-9]{8}$/.test(code), `lobby shows an 8-character room code: ${code}`);
ok((await A.page.getByText(`/join/${code}`).count()) === 1, "lobby shows the shareable link");
await A.page.screenshot({ path: `${shots}/o2-lobby.png` });
await guestRename(A, "ZZ web A (delete me)");

// profile + resume while waiting
await A.page.getByRole("button", { name: "Back to home" }).click();
await A.page.getByRole("button", { name: "Resume game" }).waitFor({ timeout: 8000 });
ok(true, "after leaving the lobby, Home offers to resume the room");
await A.page.getByRole("button", { name: /Profile/ }).click();
await A.page.getByText("Guest account", { exact: true }).waitFor({ timeout: 8000 });
ok((await A.page.locator(".field input").inputValue()) === "ZZ web A (delete me)", "profile shows the display name");
await A.page.screenshot({ path: `${shots}/o3-profile.png` });
ok((await A.page.getByRole("button", { name: "Link Google account" }).count()) === 1, "a guest is offered Link Google account");
await A.page.getByRole("button", { name: "Back" }).click();
await A.page.getByRole("button", { name: "Resume game" }).click();
await A.page.getByText("Waiting for an opponent").waitFor();
ok(true, "Resume game returns to the same lobby");

// ------------------------------------------------------------------ a stranger follows a bad link while signed out
const C = await newPlayer("C");
await C.page.goto(`${base}/join/ZZZZZZZZ`);
await C.page.getByText("Sign in to join your friend's game.").waitFor({ timeout: 8000 });
ok(true, "a shared link opened while signed out asks you to sign in first");
await C.page.getByRole("button", { name: "Play as guest" }).click();
await C.page.locator(".warn").waitFor({ timeout: 20000 });
const badMsg = await C.page.locator(".warn").innerText();
ok(/not found/i.test(badMsg), `a wrong code gives a clear message: "${badMsg.trim()}"`);
ok((await C.page.locator("h1").innerText()) === "Quoridor", "…and returns to the home screen");
await C.ctx.close();

// ------------------------------------------------------------------ the guest opens the shared link
const B = await newPlayer("B");
await B.page.goto(base);
await B.page.getByRole("button", { name: "Sign in" }).click();
await B.page.getByRole("button", { name: "Play as guest" }).click();
await B.page.getByRole("button", { name: /Profile/ }).waitFor({ timeout: 20000 });
await guestRename(B, "ZZ web B (delete me)");
await B.page.goto(`${base}/join/${code}`);
await B.page.waitForSelector("svg.board", { timeout: 20000 });
ok(new URL(B.page.url()).pathname === "/", "the /join link joins the room and the address is tidied to /");
await A.page.waitForSelector("svg.board", { timeout: 15000 });
ok(true, "the host's lobby turned into the game by itself when the guest joined");
await A.page.waitForTimeout(1500); await B.page.waitForTimeout(500);
const titleA = await A.page.locator(".card").nth(1).innerText(), titleB = await B.page.locator(".card").nth(1).innerText();
ok(titleA.includes("ZZ web B (delete me)"), `host sees the opponent's name: "${titleA.split("\n")[0]}"`);
ok(titleB.includes("ZZ web A (delete me)"), `guest sees the opponent's name: "${titleB.split("\n")[0]}"`);

// seat 1 sees the board turned: their own (red) pawn is at the bottom
const pawnY = async (p, cls) => p.page.locator(`g.pawn circle.${cls}`).evaluate((c) => Number(/,\s*([\d.]+)px/.exec(c.parentElement.style.transform)[1]));
ok((await pawnY(A, "mine")) > (await pawnY(A, "theirs")), "host (seat 0): own pawn is at the bottom");
ok((await pawnY(B, "mine")) > (await pawnY(B, "theirs")), "guest (seat 1): own pawn is ALSO at the bottom (board turned)");
await A.page.screenshot({ path: `${shots}/o4-host.png` }); await B.page.screenshot({ path: `${shots}/o5-guest.png` });

// whose turn is it?
const seatOnTurn = (await A.page.locator(".card.active").first().innerText()).startsWith("You") ? 0 : 1;
const [first, second] = seatOnTurn === 0 ? [A, B] : [B, A];
ok((await second.page.locator(".card.active").innerText()).includes(second === A ? "ZZ web B" : "ZZ web A"), "both screens agree on whose turn it is");

// first player moves one square forward
const startRow = seatOnTurn === 0 ? 8 : 0, fwd = seatOnTurn === 0 ? 7 : 1;
const t0 = Date.now();
let pos = await cellPos(first, seatOnTurn, fwd, 4);
await first.page.mouse.click(pos.x, pos.y);
await second.page.getByText(/e[19] → e[28]|e[28] → e[19]|→/).first().waitFor({ timeout: 10000 }).catch(() => {});
await second.page.waitForFunction(() => /→/.test(document.querySelector(".strip")?.textContent ?? ""), null, { timeout: 10000 });
const latency = Date.now() - t0;
ok(true, `the opponent's screen shows the move: "${(await second.page.locator(".strip").innerText()).trim()}" (${latency} ms)`);
ok(latency < 6000, "the move reached the other player within 6 seconds");

// second player places a wall by dragging it from the tray (logical notch row 4, col 3, horizontal)
const secondSeat = 1 - seatOnTurn;
await second.page.waitForSelector(".card.active");
await second.page.waitForFunction(() => document.querySelector(".card.active")?.textContent?.startsWith("You"), null, { timeout: 8000 });
const tray = await second.page.getByRole("button", { name: /Horizontal wall/ }).boundingBox();
const sx = tray.x + tray.width / 2, sy = tray.y + tray.height / 2, np = await notchPos(second, secondSeat, 4, 3);
await second.page.mouse.move(sx, sy); await second.page.mouse.down();
for (let i = 1; i <= 8; i++) await second.page.mouse.move(sx + (np.x - sx) * i / 8, sy + (np.y + 48 - sy) * i / 8);
await second.page.waitForTimeout(150);
ok(await second.page.locator("rect.ghost:not(.bad)").count() === 1, "dragging shows a valid snap ghost");
await second.page.mouse.up();
await first.page.waitForSelector("rect.wall", { timeout: 10000 });
await second.page.waitForSelector("rect.wall");
ok((await second.page.locator("rect.wall.mine").count()) === 1 && (await first.page.locator("rect.wall.theirs").count()) === 1,
  "the wall appears for both players: red for its owner, blue for the opponent");
// the wall sits on the same groove for both views (rotated 180° for the turned board)
const wallBox = async (p) => p.page.locator("rect.wall").first().evaluate((r) => ({ x: +r.getAttribute("x"), y: +r.getAttribute("y"), w: +r.getAttribute("width"), h: +r.getAttribute("height") }));
const wf = await wallBox(first), ws = await wallBox(second);
const same = (a, b) => Math.abs(a - b) < 0.5;
const turned = seatOnTurn === 1 ? wf : ws, plain = seatOnTurn === 1 ? ws : wf; // whichever is seat 1's view
ok(same(turned.x, 1000 - plain.x - plain.w) && same(turned.y, 1000 - plain.y - plain.h), "the same wall is drawn on the same groove in both views (turned 180° for seat 1)");
await first.page.screenshot({ path: `${shots}/o6-wall-first.png` }); await second.page.screenshot({ path: `${shots}/o7-wall-second.png` });

// invalid wall is refused with a message (tap the occupied groove)
await first.page.waitForFunction(() => document.querySelector(".card.active")?.textContent?.startsWith("You"), null, { timeout: 8000 });
const occ = await notchPos(first, seatOnTurn, 4, 3);
await first.page.mouse.click(occ.x, occ.y);
await first.page.waitForSelector(".warn", { timeout: 3000 }).catch(async (e) => {
  console.log("DEBUG strip:", await first.page.locator(".strip").innerText(), "| active card:", (await first.page.locator(".card.active").allInnerTexts()).join(" / "),
    "| walls:", await first.page.locator("rect.wall").count(), "| seat:", seatOnTurn, "| click at", Math.round(occ.x), Math.round(occ.y));
  await first.page.screenshot({ path: `${shots}/debug-warn.png` });
  throw e;
});
ok((await first.page.locator(".warn").innerText()).includes("Invalid wall placement"), "an illegal wall is refused with a message");

// history sheet on the second player's side lists both moves
await second.page.locator(".strip").click();
await second.page.waitForSelector(".sheet");
ok((await second.page.locator(".sheet li").count()) >= 2, "the history sheet lists the moves played online");
await second.page.locator(".sheet-wrap").click({ position: { x: 10, y: 10 } });
// seat 1 (B) must see their OWN moves as "You" (red) and the host's as the host's name (blue); this used to be inverted
await B.page.locator(".strip").click();
await B.page.waitForSelector(".sheet");
const mineLabels = await B.page.locator(".sheet li b.mine").allInnerTexts(), theirLabels = await B.page.locator(".sheet li b.theirs").allInnerTexts();
ok(mineLabels.length >= 1 && mineLabels.every((t) => t === "You") && theirLabels.length >= 1 && theirLabels.every((t) => t.includes("ZZ web A")),
  `the second player's history reads correctly: yours = ${JSON.stringify(mineLabels)}, theirs = ${JSON.stringify(theirLabels)}`);
await B.page.locator(".sheet-wrap").click({ position: { x: 10, y: 10 } });

// ------------------------------------------------------------------ the player NOT on turn resigns (must be allowed)
const loser = (await first.page.locator(".card.active").first().innerText()).startsWith("You") ? second : first;
const winner = loser === A ? B : A;
await loser.page.getByRole("button", { name: /Resign/ }).click();
await loser.page.getByRole("button", { name: "Resign", exact: true }).last().click();
await loser.page.waitForSelector(".modal h2.lost", { timeout: 10000 });
await winner.page.waitForSelector(".modal h2.won", { timeout: 10000 });
ok(true, `resigning ends the game for both: ${loser.name} sees "You lost", ${winner.name} sees "You won"`);
await winner.page.screenshot({ path: `${shots}/o8-won.png` });
ok((await winner.page.locator(".modal").innerText()).includes("Moves"), "the result screen shows the game's stats");

// ------------------------------------------------------------------ rematch: seats are swapped
await loser.page.getByRole("button", { name: "Rematch" }).click();
await winner.page.getByText(/wants a rematch/).waitFor({ timeout: 12000 });
ok(true, "the winner's result screen says the opponent wants a rematch");
await winner.page.screenshot({ path: `${shots}/o9-rematch-offer.png` });
await winner.page.getByRole("button", { name: "Accept rematch" }).click();
// a brand-new game: the result screen is gone and nothing has been played (the player waiting for their turn sees "Waiting for …")
const fresh = () => !document.querySelector(".modal h2") && /^(No moves yet|Waiting for)/.test(document.querySelector(".strip")?.textContent ?? "");
await loser.page.waitForFunction(fresh, null, { timeout: 15000 });
await winner.page.waitForFunction(fresh, null, { timeout: 15000 });
ok(true, "both players land in a new game");
// the player who was seat 1 is now seat 0 and moves first
const newFirst = (A.page === loser.page ? (loser === A ? A : B) : null);
const wasSeat1 = (p) => p === B ? "B" : "A"; // B joined second, so B was seat 1
ok((await B.page.locator(".card.active").innerText()).startsWith("You") && !(await A.page.locator(".card.active").first().innerText()).startsWith("You"),
  "seats are swapped: B (second player before) now moves first");
ok((await pawnY(A, "mine")) > (await pawnY(A, "theirs")) && (await pawnY(B, "mine")) > (await pawnY(B, "theirs")), "each player still sees their own pawn at the bottom");
await A.page.screenshot({ path: `${shots}/o10-rematch-game.png` });
// the same player resigns again, so the same player wins both games
await loser.page.getByRole("button", { name: /Resign/ }).click();
await loser.page.getByRole("button", { name: "Resign", exact: true }).last().click();
await loser.page.waitForSelector(".modal h2.lost", { timeout: 10000 });
await winner.page.waitForSelector(".modal h2.won", { timeout: 10000 });
ok(true, "the rematch is a real, separate game that can be resigned too");
void newFirst; void wasSeat1;

// results are recorded on the profiles
await winner.page.getByRole("button", { name: "Home", exact: true }).click();
ok(await winner.page.getByRole("button", { name: "Resume game" }).count() === 0, "a finished game is no longer offered for resume");
await winner.page.getByRole("button", { name: /Profile/ }).click();
await winner.page.getByText("Online wins").waitFor();
const stat = async (p, label) => Number(await p.page.locator(".stat", { hasText: label }).first().locator("strong").innerText());
await winner.page.waitForTimeout(500);
ok((await stat(winner, "Online wins")) === 2 && (await stat(winner, "Wins")) >= 2 && (await stat(winner, "Games")) === 2, "the winner's profile shows 2 games, 2 wins, 2 online wins (original + rematch)");
// counters and leaderboard (the winner has 2 online wins now)
await winner.page.getByRole("button", { name: "Back" }).click();
await winner.page.waitForSelector(".community", { timeout: 10000 });
await winner.page.waitForTimeout(1300); // let the numbers finish counting up
const community = await winner.page.locator(".community").innerText();
const nums = community.match(/\d+/g).map(Number);
ok(/online/.test(community) && /joined/.test(community) && nums[0] >= 2 && nums[1] >= nums[0], `home shows live counters: "${community.trim()}"`);
await winner.page.screenshot({ path: `${shots}/o11-home-counters.png` });
await winner.page.getByRole("button", { name: "Leaderboard" }).click();
await winner.page.waitForSelector(".lb li.me", { timeout: 10000 });
const meRow = await winner.page.locator(".lb li.me").innerText();
ok(/\(you\)/.test(meRow) && /2\s*wins/.test(meRow.replace(/\n/g, " ")), `the leaderboard lists you with 2 wins: "${meRow.replace(/\n/g, " | ")}"`);
await winner.page.screenshot({ path: `${shots}/o12-leaderboard.png` });
await winner.page.getByRole("button", { name: "Back" }).click();
await loser.page.getByRole("button", { name: "Home", exact: true }).click();
await loser.page.getByRole("button", { name: /Profile/ }).click();
await loser.page.getByText("Online wins").waitFor();
await loser.page.waitForTimeout(500);
ok((await stat(loser, "Losses")) === 2 && (await stat(loser, "Online wins")) === 0, "the loser's profile shows 2 losses");
await loser.page.getByRole("button", { name: "Sign out" }).click();
await loser.page.getByRole("button", { name: "Sign in" }).waitFor({ timeout: 8000 });
ok(true, "Sign out returns to the signed-out home screen");

// ------------------------------------------------------------------ quick match: two strangers are paired
const E = await newPlayer("E"), F = await newPlayer("F");
await E.page.goto(base);
await E.page.getByRole("button", { name: "Quick match" }).click();
await E.page.getByText("Sign in to be matched with another player.").waitFor({ timeout: 8000 });
await E.page.getByRole("button", { name: "Play as guest" }).click();
await E.page.getByText("Looking for an opponent").waitFor({ timeout: 20000 });
ok(true, "Quick match asks you to sign in, then shows a searching lobby");
await guestRename(E, "ZZ web E (delete me)");
await F.page.goto(base);
await F.page.getByRole("button", { name: "Sign in" }).click();
await F.page.getByRole("button", { name: "Play as guest" }).click();
await F.page.getByRole("button", { name: /Profile/ }).waitFor({ timeout: 20000 });
await guestRename(F, "ZZ web F (delete me)");
await F.page.getByRole("button", { name: "Quick match" }).click();
await F.page.waitForSelector("svg.board", { timeout: 20000 });
await E.page.waitForSelector("svg.board", { timeout: 20000 });
ok(true, "the second searcher is paired instantly, and the first one's search turns into the game by itself");
await E.page.waitForTimeout(1800); await F.page.waitForTimeout(500);
ok((await E.page.locator(".card").nth(1).innerText()).includes("ZZ web F") && (await F.page.locator(".card").nth(1).innerText()).includes("ZZ web E"), "both see their random opponent's name");
await E.page.screenshot({ path: `${shots}/o13-quick-match.png` });
await F.page.getByRole("button", { name: /Resign/ }).click();
await F.page.getByRole("button", { name: "Resign", exact: true }).last().click();
await E.page.waitForSelector(".modal h2.won", { timeout: 10000 });
ok(true, "the quick-match game plays and ends like any other");
await E.ctx.close(); await F.ctx.close();

// cancelling a search removes your waiting room, so the next searcher is NOT paired with someone who left
const G = await newPlayer("G"), H = await newPlayer("H");
await G.page.goto(base); await G.page.getByRole("button", { name: "Quick match" }).click();
await G.page.getByRole("button", { name: "Play as guest" }).click();
await G.page.getByText("Looking for an opponent").waitFor({ timeout: 20000 });
await G.page.getByRole("button", { name: "Cancel" }).click();
await G.page.getByRole("button", { name: "Quick match" }).waitFor({ timeout: 8000 });
ok(true, "Cancel returns to the home screen");
await H.page.goto(base); await H.page.getByRole("button", { name: "Quick match" }).click();
await H.page.getByRole("button", { name: "Play as guest" }).click();
await H.page.getByText("Looking for an opponent").waitFor({ timeout: 20000 });
await H.page.waitForTimeout(6000);
ok((await H.page.locator("svg.board").count()) === 0 && (await H.page.getByText("Looking for an opponent").count()) === 1, "the next searcher keeps waiting: nobody was paired with the player who cancelled");
await H.page.getByRole("button", { name: "Cancel" }).click();
await G.ctx.close(); await H.ctx.close();

// ------------------------------------------------------------------ Google button starts the OAuth redirect
const D = await newPlayer("D");
await D.page.goto(base);
await D.page.getByRole("button", { name: "Sign in" }).click();
const [nav] = await Promise.all([
  D.page.waitForRequest((r) => /accounts\.google\.com|\/auth\/v1\/authorize/.test(r.url()), { timeout: 20000 }).catch(() => null),
  D.page.getByRole("button", { name: "Continue with Google" }).click(),
]);
const authUrl = nav?.url() ?? "";
ok(/\/auth\/v1\/authorize\?.*provider=google/.test(authUrl) || /accounts\.google\.com/.test(authUrl), "Continue with Google starts the Google sign-in redirect");
const redirectTo = new URL(authUrl.startsWith("http") ? authUrl : "http://x").searchParams.get("redirect_to");
console.log(`     (redirect_to = ${redirectTo})`);
await D.page.waitForTimeout(2500);
ok(/accounts\.google\.com/.test(D.page.url()), `Google accepts the sign-in request (landed on ${new URL(D.page.url()).host})`);
await D.ctx.close();

ok(errors.length === 0, `no console or page errors${errors.length ? ": " + errors.slice(0, 3).join(" | ") : ""}`);
await A.ctx.close(); await B.ctx.close(); await browser.close();
console.log(failed ? `\n${failed} FAILED` : "\nall passed");
process.exit(failed ? 1 : 0);
