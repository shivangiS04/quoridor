// The game rules have ONE source of truth: supabase/functions/_shared/quoridor.ts (it validates every online move).
// The web app uses an exact copy so the browser and the server can never disagree. Run after changing the rules.
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const src = path.join(here, "..", "..", "supabase", "functions", "_shared", "quoridor.ts");
const dst = path.join(here, "..", "src", "shared", "quoridor.ts");
const header = "// GENERATED COPY of supabase/functions/_shared/quoridor.ts — do not edit here; run `npm run sync-rules`.\n";
fs.writeFileSync(dst, header + fs.readFileSync(src, "utf8"));
console.log("rules synced ->", path.relative(process.cwd(), dst));
