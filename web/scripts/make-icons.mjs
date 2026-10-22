// Renders the app icons (PNG) from the logo with headless Chrome. Run: node scripts/make-icons.mjs
import { chromium } from "playwright-core";
import fs from "node:fs";

const chrome = process.env.CHROME ?? "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";
// logo drawn in a 64x64 box; `pad` shrinks it into the centre (maskable icons need a safe zone)
const logo = (pad) => `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64" width="100%" height="100%">
  <rect width="64" height="64" fill="#141414"/>
  <g transform="translate(${32 * pad} ${32 * pad}) scale(${1 - pad})">
    <circle cx="24" cy="38" r="10" fill="#ef4444"/><circle cx="42" cy="26" r="10" fill="#3b82f6"/>
    <rect x="14" y="48" width="36" height="5" rx="2.5" fill="#b8b8b8"/>
  </g></svg>`;
const targets = [
  { file: "icon-192.png", size: 192, pad: 0, round: true },
  { file: "icon-512.png", size: 512, pad: 0, round: true },
  { file: "icon-maskable-512.png", size: 512, pad: 0.2, round: false },
  { file: "apple-touch-icon.png", size: 180, pad: 0, round: false },
];
const browser = await chromium.launch({ executablePath: chrome, headless: true });
for (const t of targets) {
  const page = await browser.newPage({ viewport: { width: t.size, height: t.size } });
  await page.setContent(`<body style="margin:0;background:#141414"><div style="width:${t.size}px;height:${t.size}px;border-radius:${t.round ? t.size * 0.22 : 0}px;overflow:hidden">${logo(t.pad)}</div></body>`);
  await page.screenshot({ path: `public/icons/${t.file}`, omitBackground: true });
  await page.close();
}
await browser.close();
console.log(targets.map((t) => `${t.file} (${fs.statSync(`public/icons/${t.file}`).size} B)`).join("\n"));
