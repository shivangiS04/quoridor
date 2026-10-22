import { useEffect, useRef, useState } from "react";
import { touchPresence } from "./api.ts";

/** A number that counts up to `value` (and re-counts smoothly when it changes). */
export function useCountUp(value: number, ms = 800): number {
  const [shown, setShown] = useState(0);
  const from = useRef(0);
  useEffect(() => {
    const start = performance.now(), begin = from.current;
    if (window.matchMedia?.("(prefers-reduced-motion: reduce)").matches) { setShown(value); from.current = value; return; }
    let raf = 0;
    const step = (t: number) => {
      const k = Math.min(1, (t - start) / ms), eased = 1 - (1 - k) ** 3;
      const v = Math.round(begin + (value - begin) * eased);
      setShown(v); from.current = v;
      if (k < 1) raf = requestAnimationFrame(step);
    };
    raf = requestAnimationFrame(step);
    return () => cancelAnimationFrame(raf);
  }, [value, ms]);
  return shown;
}

/** While signed in and the tab is visible, tell the server we are here once a minute ("N players online"). */
export function usePresence(signedIn: boolean) {
  useEffect(() => {
    if (!signedIn) return;
    const beat = () => { if (document.visibilityState === "visible") void touchPresence(); };
    beat();
    const t = setInterval(beat, 60_000);
    document.addEventListener("visibilitychange", beat);
    return () => { clearInterval(t); document.removeEventListener("visibilitychange", beat); };
  }, [signedIn]);
}
