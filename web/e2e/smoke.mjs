// Drives the built app in a real browser. Usage: node e2e/smoke.mjs <baseUrl> <screenshotDir>
import { chromium } from "playwright-core";
import fs from "node:fs";

const base = process.argv[2] ?? "http://127.0.0.1:4173";
const shots = process.argv[3] ?? "shots";
fs.mkdirSync(shots, { recursive: true });
const chrome = process.env.CHROME ?? "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";

let failed = 0;
const ok = (c, m) => { console.log((c ? "PASS " : "FAIL ") + m); if (!c) failed++; };

const browser = await chromium.launch({ executablePath: chrome, headless: true });
const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, hasTouch: false });
const page = await ctx.newPage();
const errors = [];
page.on("pageerror", (e) => errors.push(e.message));
page.on("console", (m) => { if (m.type() === "error") errors.push(m.text()); });

await page.goto(base);
await page.screenshot({ path: `${shots}/1-home.png` });
ok(await page.locator("h1").innerText() === "Quoridor", "home screen renders");

await page.getByRole("button", { name: "Easy" }).click();
await page.waitForSelector("svg.board");
await page.screenshot({ path: `${shots}/2-game.png` });
ok((await page.locator("rect.hint").count()) === 3, "three reachable cells are outlined at the start");
ok((await page.locator(".card.active").innerText()).startsWith("You"), "your card is active at the start");

// board geometry in screen px
const box = await page.locator("svg.board").boundingBox();
const k = box.width / 1000;
const stride = 1000 / (9 + 8 * 0.14) * 1.14; // cell + gap in board units
const cellCentre = (r, c) => ({ x: box.x + (c * stride + stride / 2 - (stride - 1000 / (9 + 8 * .14) * (1 / 1)) / 2) * k, y: box.y + (r * stride + stride / 2) * k });
const pos = (r, c) => { const cell = (1000 - 8 * (1000 / (9 + 8 * .14) * .14)) / 9; return { x: box.x + (c * stride + cell / 2) * k, y: box.y + (r * stride + cell / 2) * k }; };

// move up one square
const up = pos(7, 4);
await page.mouse.click(up.x, up.y);
await page.waitForTimeout(100);
ok((await page.locator(".strip").innerText()).includes("thinking"), "after your move the computer starts thinking");
await page.waitForTimeout(1200); // computer replies
const afterAi = await page.locator(".strip").innerText();
ok(/Computer: /.test(afterAi), `the computer replied: "${afterAi}"`);
await page.screenshot({ path: `${shots}/3-after-moves.png` });

// drag a horizontal wall from the tray onto the board (notch row 4, col 3)
const cell = (1000 - 8 * (1000 / (9 + 8 * .14) * .14)) / 9, gap = 1000 / (9 + 8 * .14) * .14;
const notch = { x: box.x + (3 * stride + cell + gap / 2) * k, y: box.y + (4 * stride + cell + gap / 2) * k };
const tray = await page.getByRole("button", { name: /Horizontal wall/ }).boundingBox();
const sx = tray.x + tray.width / 2, sy = tray.y + tray.height / 2;
await page.mouse.move(sx, sy);
await page.mouse.down();
for (let i = 1; i <= 8; i++) await page.mouse.move(sx + (notch.x - sx) * i / 8, sy + (notch.y + 48 - sy) * i / 8);
await page.waitForTimeout(150);
ok((await page.locator(".held").count()) === 1, "a wall follows the pointer while dragging");
ok((await page.locator("rect.ghost").count()) === 1, "a dashed ghost shows where it will land");
await page.screenshot({ path: `${shots}/4-dragging.png` });
await page.mouse.up();
await page.waitForTimeout(300);
ok((await page.locator("rect.wall.mine").count()) === 1, "releasing over the board places the wall");
ok((await page.locator(".card").first().innerText()).includes("9"), "walls left dropped to 9");
await page.screenshot({ path: `${shots}/5-wall-placed.png` });

// drag and drop OFF the board cancels
await page.waitForTimeout(1300);
const tray2 = await page.getByRole("button", { name: /Vertical wall/ }).boundingBox();
await page.mouse.move(tray2.x + 20, tray2.y + 20); await page.mouse.down();
// carry it up past the board into the top bar (the held wall is 48px above the pointer, so this is off the board)
for (let i = 1; i <= 8; i++) await page.mouse.move(tray2.x + 20 + (195 - tray2.x - 20) * i / 8, tray2.y + 20 + (40 - tray2.y - 20) * i / 8);
ok((await page.locator("rect.ghost").count()) === 0, "no snap ghost while the held wall is off the board");
await page.mouse.up();
await page.waitForTimeout(200);
ok((await page.locator("rect.wall.mine").count()) === 1, "dropping a wall off the board cancels (still one wall)");

// tapping the groove where a wall already sits is refused, with a warning
await page.waitForTimeout(1300);
await page.mouse.click(notch.x, notch.y);
await page.waitForSelector(".warn", { timeout: 2000 });
ok((await page.locator(".warn").innerText()).includes("Invalid wall placement"), "tapping an occupied groove shows \"Invalid wall placement\"");

// history sheet + resign dialog + result overlay
await page.locator(".strip").click();
await page.waitForSelector(".sheet");
ok((await page.locator(".sheet li").count()) >= 3, "the history sheet lists the moves");
ok((await page.locator(".sheet").innerText()).includes("e1 → e2"), "…including your first move, e1 → e2");
await page.screenshot({ path: `${shots}/6-history.png` });
await page.locator(".sheet-wrap").click({ position: { x: 10, y: 10 } });
await page.getByRole("button", { name: /Resign/ }).click();
await page.screenshot({ path: `${shots}/7-resign.png` });
await page.getByRole("button", { name: "Resign", exact: true }).last().click();
await page.waitForSelector(".modal h2.lost");
await page.screenshot({ path: `${shots}/8-lost.png` });
ok(true, "resigning shows the result screen");
await page.getByRole("button", { name: "Play again" }).click();
ok((await page.locator("rect.wall").count()) === 0 && (await page.locator(".strip").innerText()) === "No moves yet", "Play again starts a fresh game");

// ---------------------------------------------------------------- two players, one device
await page.getByRole("button", { name: "Back to home" }).click();
await page.getByRole("button", { name: "Two players, one device" }).click();
await page.waitForSelector("svg.board");
ok((await page.locator(".card").nth(0).innerText()).includes("Player 1") && (await page.locator(".card").nth(1).innerText()).includes("Player 2"), "pass-and-play shows Player 1 and Player 2");
ok((await page.locator(".card.active").innerText()).includes("Player 1"), "Player 1 starts");
const gbox = await page.locator("svg.board").boundingBox(), gk = gbox.width / 1000;
const gcell = (1000 - 8 * (1000 / (9 + 8 * .14) * .14)) / 9, gstride = 1000 / (9 + 8 * .14) * 1.14;
const gpos = (r, c) => ({ x: gbox.x + (c * gstride + gcell / 2) * gk, y: gbox.y + (r * gstride + gcell / 2) * gk });
let gp = gpos(7, 4); await page.mouse.click(gp.x, gp.y);
await page.waitForFunction(() => /1\. Player 1: e1 → e2/.test(document.querySelector(".strip")?.textContent ?? ""), null, { timeout: 4000 });
ok((await page.locator(".card.active").innerText()).includes("Player 2"), "after Player 1 moves it is Player 2's turn (no computer reply)");
gp = gpos(1, 4); await page.mouse.click(gp.x, gp.y);
await page.waitForFunction(() => /2\. Player 2: e9 → e8/.test(document.querySelector(".strip")?.textContent ?? ""), null, { timeout: 4000 });
ok(true, "Player 2 moves the blue pawn: \"2. Player 2: e9 → e8\"");
await page.screenshot({ path: `${shots}/9-pvp.png` });
// Player 1 places a wall by dragging; it belongs to Player 1 (red)
const pt = await page.getByRole("button", { name: /Horizontal wall/ }).boundingBox();
const psx = pt.x + pt.width / 2, psy = pt.y + pt.height / 2, pn = { x: gbox.x + (3 * gstride + gcell + (1000 / (9 + 8 * .14) * .14) / 2) * gk, y: gbox.y + (4 * gstride + gcell + (1000 / (9 + 8 * .14) * .14) / 2) * gk };
await page.mouse.move(psx, psy); await page.mouse.down();
for (let i = 1; i <= 8; i++) await page.mouse.move(psx + (pn.x - psx) * i / 8, psy + (pn.y + 48 - psy) * i / 8);
await page.mouse.up();
await page.waitForSelector("rect.wall.mine", { timeout: 3000 });
ok((await page.locator(".card.active").innerText()).includes("Player 2"), "Player 1's wall is red and the turn passes to Player 2");
// turn the board towards the player on the move: Player 2's blue pawn must now be at the bottom
const py = (cls) => page.locator(`g.pawn circle.${cls}`).evaluate((c) => Number(/,\s*([\d.]+)px/.exec(c.parentElement.style.transform)[1]));
ok((await py("mine")) > (await py("theirs")), "by default the board stays put (red at the bottom)");
await page.getByRole("button", { name: /Turn the board/ }).click();
await page.waitForTimeout(300);
ok((await py("theirs")) > (await py("mine")), "with the toggle on, the board turns towards Player 2 (blue at the bottom)");
await page.screenshot({ path: `${shots}/10-pvp-turned.png` });
await page.getByRole("button", { name: /Resign/ }).click();
await page.getByText("Player 1 will be declared the winner.").waitFor({ timeout: 2000 });
await page.getByRole("button", { name: "Resign", exact: true }).last().click();
await page.waitForSelector(".modal h2.won");
ok((await page.locator(".modal h2").innerText()) === "Player 1 wins", "the player on the move can resign: \"Player 1 wins\"");
ok((await page.getByRole("button", { name: "Rematch" }).count()) === 0, "no online rematch button in a pass-and-play game");
await page.getByRole("button", { name: "Play again" }).click();
ok((await page.locator(".strip").innerText()) === "No moves yet", "Play again starts a fresh pass-and-play game");

// ---------------------------------------------------------------- install to home screen
const man = await page.request.get(base + "/manifest.webmanifest");
const mj = await man.json().catch(() => ({}));
ok(man.ok() && mj.name === "Quoridor" && mj.display === "standalone" && mj.icons?.length >= 3, "the web app manifest is served (name, standalone, icons)");
for (const ic of mj.icons ?? []) ok((await page.request.get(base + ic.src)).ok(), `icon ${ic.src} is served`);
ok((await page.request.get(base + "/icons/apple-touch-icon.png")).ok(), "the iPhone home-screen icon is served");
await page.goto(base);
ok((await page.locator('link[rel="manifest"]').count()) === 1 && (await page.locator('link[rel="apple-touch-icon"]').count()) === 1, "the page links the manifest and the iPhone icon");

ok(errors.length === 0, `no console or page errors${errors.length ? ": " + errors.join(" | ") : ""}`);
await browser.close();
console.log(failed ? `\n${failed} FAILED` : "\nall passed");
process.exit(failed ? 1 : 0);
