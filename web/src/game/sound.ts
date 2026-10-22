// Tiny synthesized sound effects (no audio files). Muted state is remembered in localStorage.
export type Sfx = "move" | "wall" | "invalid" | "win" | "lose";

let ctx: AudioContext | null = null;
const KEY = "quoridor.sound";

export const soundEnabled = (): boolean => {
  try { return localStorage.getItem(KEY) !== "off"; } catch { return true; }
};
export const setSoundEnabled = (on: boolean) => {
  try { localStorage.setItem(KEY, on ? "on" : "off"); } catch { /* private mode */ }
};

function tone(freq: number, start: number, dur: number, type: OscillatorType = "sine", gain = 0.06) {
  if (!ctx) return;
  const o = ctx.createOscillator(), g = ctx.createGain();
  o.type = type; o.frequency.value = freq;
  g.gain.setValueAtTime(gain, ctx.currentTime + start);
  g.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + start + dur);
  o.connect(g).connect(ctx.destination);
  o.start(ctx.currentTime + start); o.stop(ctx.currentTime + start + dur + 0.02);
}

export function play(sfx: Sfx) {
  if (!soundEnabled()) return;
  try {
    ctx ??= new AudioContext();
    if (ctx.state === "suspended") void ctx.resume();
    switch (sfx) {
      case "move": tone(520, 0, 0.08); break;
      case "wall": tone(220, 0, 0.12, "triangle", 0.09); break;
      case "invalid": tone(150, 0, 0.16, "square", 0.04); break;
      case "win": [523, 659, 784].forEach((f, i) => tone(f, i * 0.12, 0.2)); break;
      case "lose": [330, 262].forEach((f, i) => tone(f, i * 0.16, 0.25, "triangle")); break;
    }
  } catch { /* audio unavailable */ }
}
