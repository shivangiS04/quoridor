import { useCallback, useEffect, useRef, useState } from "react";
import type { Difficulty } from "./game/ai.ts";
import { useLocalGame } from "./game/useLocalGame.ts";
import { useLocalPvP } from "./game/useLocalPvP.ts";
import { type CommunityStats, createRoom, getCommunityStats, getRoom, joinRoom, quickMatch, recordAiGame } from "./lib/api.ts";
import { usePresence } from "./lib/hooks.ts";
import { useAuth } from "./lib/auth.tsx";
import { onlineConfigured } from "./lib/supabase.ts";
import { GameScreen } from "./ui/GameScreen.tsx";
import { Home } from "./ui/Home.tsx";
import { HelpPage } from "./ui/Help.tsx";
import { LeaderboardScreen } from "./ui/Leaderboard.tsx";
import { ProfileScreen } from "./ui/Profile.tsx";
import { ROOM_KEY, RoomScreen } from "./ui/Room.tsx";
import { SignIn } from "./ui/SignIn.tsx";

type Screen =
  | { name: "home" }
  | { name: "signin"; reason: string }
  | { name: "local"; difficulty: Difficulty }
  | { name: "pvp" }
  | { name: "room"; roomId: string; quick: boolean }
  | { name: "profile" }
  | { name: "leaderboard" }
  | { name: "help" };

/** Something that needs an account; remembered across the Google redirect. */
type Intent = { type: "create" } | { type: "quick" } | { type: "join"; code: string };
const INTENT_KEY = "quoridor.intent";
const read = (k: string) => { try { return localStorage.getItem(k); } catch { return null; } };
const write = (k: string, v: string | null) => { try { v === null ? localStorage.removeItem(k) : localStorage.setItem(k, v); } catch { /* private mode */ } };

export default function App() {
  const auth = useAuth();
  const [screen, setScreen] = useState<Screen>({ name: "home" });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [resumeId, setResumeId] = useState<string | null>(null);
  const [intent, setIntentState] = useState<Intent | null>(() => {
    // Opening a shared link /join/CODE means "join this room"; the address is then tidied to /.
    const m = window.location.pathname.match(/^\/join\/([A-Za-z0-9]{4,12})\/?$/);
    if (m) { window.history.replaceState(null, "", "/"); return { type: "join", code: m[1].toUpperCase() }; }
    const saved = read(INTENT_KEY);
    return saved ? (JSON.parse(saved) as Intent) : null;
  });
  const setIntent = (i: Intent | null) => { setIntentState(i); write(INTENT_KEY, i ? JSON.stringify(i) : null); };
  const running = useRef(false);

  const [stats, setStats] = useState<CommunityStats | null>(null);
  usePresence(Boolean(auth.user));

  const enterRoom = useCallback((roomId: string, quick = false) => {
    write(ROOM_KEY, roomId); setResumeId(null); setScreen({ name: "room", roomId, quick });
  }, []);

  const perform = useCallback(async (i: Intent) => {
    if (running.current) return;
    running.current = true;
    setBusy(true); setError(null);
    try {
      if (i.type === "create") enterRoom((await createRoom()).room_id);
      else if (i.type === "quick") { const r = await quickMatch(); enterRoom(r.room_id, r.status === "waiting"); }
      else enterRoom((await joinRoom(i.code)).room_id);
      setIntent(null);
    } catch (e) {
      setIntent(null);
      setScreen({ name: "home" });
      setError(e instanceof Error ? e.message : "Something went wrong.");
    }
    setBusy(false);
    running.current = false;
  }, [enterRoom]);

  // A pending intent runs as soon as there is a signed-in user; without one, ask the player to sign in first.
  useEffect(() => {
    if (!onlineConfigured || !auth.ready || !intent) return;
    if (auth.user) void perform(intent);
    else if (screen.name !== "signin") setScreen({ name: "signin", reason: intent.type === "join" ? "Sign in to join your friend's game." : intent.type === "quick" ? "Sign in to be matched with another player." : "Sign in to play online." });
  }, [auth.ready, auth.user, intent, perform, screen.name]);

  // Signing in from the sign-in screen without a pending intent goes back to the home screen.
  useEffect(() => { if (auth.user && screen.name === "signin" && !intent) setScreen({ name: "home" }); }, [auth.user, screen.name, intent]);
  // Signing out anywhere returns home.
  useEffect(() => { if (auth.ready && !auth.user && (screen.name === "profile" || screen.name === "room")) setScreen({ name: "home" }); }, [auth.ready, auth.user, screen.name]);

  // Offer to resume an unfinished game from a previous visit.
  useEffect(() => {
    const saved = read(ROOM_KEY);
    if (!onlineConfigured || !auth.user || !saved || screen.name !== "home") { if (!saved) setResumeId(null); return; }
    let alive = true;
    getRoom(saved).then((r) => {
      if (!alive) return;
      if (r.match?.state.status === "finished") { write(ROOM_KEY, null); setResumeId(null); } else setResumeId(saved);
    }).catch((e) => { if (alive && e?.status) { write(ROOM_KEY, null); setResumeId(null); } });
    return () => { alive = false; };
  }, [auth.user, screen.name]);

  // The "N online / X joined" counters, refreshed while the home screen is open.
  useEffect(() => {
    if (!onlineConfigured || !auth.user || screen.name !== "home") return;
    let alive = true;
    const load = () => getCommunityStats().then((s) => { if (alive) setStats(s); }).catch(() => {});
    void load();
    const t = setInterval(load, 30_000);
    return () => { alive = false; clearInterval(t); };
  }, [auth.user, screen.name]);

  if (!auth.ready) return <div className="home"><p className="muted">Loading…</p></div>;

  switch (screen.name) {
    case "local":
      return <LocalGame key={screen.difficulty} difficulty={screen.difficulty} onHome={() => setScreen({ name: "home" })} />;
    case "help":
      return <HelpPage onBack={() => setScreen({ name: "home" })} />;
    case "pvp":
      return <PvPGame onHome={() => setScreen({ name: "home" })} />;
    case "room":
      return <RoomScreen key={screen.roomId} roomId={screen.roomId} quick={screen.quick} onHome={() => setScreen({ name: "home" })}
        onEnterRoom={(id) => enterRoom(id)} />;
    case "leaderboard":
      return auth.user ? <LeaderboardScreen onBack={() => setScreen({ name: "home" })} /> : null;
    case "profile":
      return auth.user ? <ProfileScreen onBack={() => setScreen({ name: "home" })} /> : null;
    case "signin":
      return <SignIn reason={screen.reason} onBack={() => { setIntent(null); setScreen({ name: "home" }); }} />;
    default:
      return (
        <Home
          signedIn={Boolean(auth.user)}
          guest={auth.isGuest}
          busy={busy}
          error={error}
          stats={stats}
          resume={Boolean(resumeId)}
          onResume={() => resumeId && enterRoom(resumeId)}
          onPlay={(difficulty) => setScreen({ name: "local", difficulty })}
          onPvP={() => setScreen({ name: "pvp" })}
          onHelp={() => setScreen({ name: "help" })}
          onProfile={() => setScreen({ name: "profile" })}
          onLeaderboard={() => (auth.user ? setScreen({ name: "leaderboard" }) : setScreen({ name: "signin", reason: "Sign in to see the leaderboard." }))}
          onQuick={() => { setError(null); if (auth.user) void perform({ type: "quick" }); else setIntent({ type: "quick" }); }}
          onSignIn={() => setScreen({ name: "signin", reason: "Sign in to play online and keep your stats." })}
          onCreate={() => { setError(null); if (auth.user) void perform({ type: "create" }); else setIntent({ type: "create" }); }}
          onJoin={(code) => { setError(null); if (auth.user) void perform({ type: "join", code }); else setIntent({ type: "join", code }); }}
        />
      );
  }
}

function PvPGame({ onHome }: { onHome: () => void }) {
  return <GameScreen g={useLocalPvP()} onHome={onHome} />;
}

function LocalGame({ difficulty, onHome }: { difficulty: Difficulty; onHome: () => void }) {
  const auth = useAuth();
  // A signed-in player's games against the computer count towards their stats (best effort).
  return <GameScreen g={useLocalGame(difficulty, (won) => { if (auth.user) void recordAiGame(won); })} onHome={onHome} />;
}
