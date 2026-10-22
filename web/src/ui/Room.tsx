import { useEffect, useState } from "react";
import { useOnlineGame } from "../game/useOnlineGame.ts";
import { cancelQuickMatch, getOpponent, getRoom, type MatchRow, quickMatch, type RoomInfo, touchPresence } from "../lib/api.ts";
import { ApiError, client } from "../lib/supabase.ts";
import { GameScreen } from "./GameScreen.tsx";

export const ROOM_KEY = "quoridor.room";
export const shareLink = (code: string) => `${window.location.origin}/join/${code}`;

/**
 * Loads a room, shows the lobby until an opponent joins, then the game.
 * `quick` rooms were opened by "Quick match": the lobby keeps looking for another waiting player.
 */
export function RoomScreen({ roomId, quick = false, onHome, onEnterRoom }: {
  roomId: string; quick?: boolean; onHome: () => void; onEnterRoom: (roomId: string) => void;
}) {
  const [info, setInfo] = useState<RoomInfo | null>(null);
  const [error, setError] = useState<string | null>(null);

  // Load once, and keep looking until the match exists (realtime INSERT, with a poll as a safety net).
  useEffect(() => {
    let alive = true;
    const load = async () => {
      try {
        const i = await getRoom(roomId);
        if (alive) setInfo(i);
      } catch (e) {
        if (!alive) return;
        if (e instanceof ApiError && (e.status === 403 || e.status === 404)) {
          try { localStorage.removeItem(ROOM_KEY); } catch { /* ignore */ }
          setError("This room no longer exists.");
        }
      }
    };
    void load();
    const c = client();
    const channel = c.channel(`lobby-${roomId}`)
      .on("postgres_changes", { event: "*", schema: "public", table: "matches", filter: `room_id=eq.${roomId}` }, () => { void load(); })
      .subscribe();
    const poll = setInterval(() => { if (alive) void load(); }, 3000);
    return () => { alive = false; clearInterval(poll); void c.removeChannel(channel); };
  }, [roomId]);

  if (error) return <div className="home"><div className="home-inner"><p className="warn block">{error}</p><button className="primary" onClick={onHome}>Home</button></div></div>;
  if (!info) return <div className="home"><p className="muted">Loading…</p></div>;
  if (!info.match) return <Lobby code={info.code} quick={quick} roomId={roomId} onHome={onHome} onEnterRoom={onEnterRoom} />;
  return <Playing key={roomId} roomId={roomId} me={info.player_index} match={info.match} onHome={onHome} onEnterRoom={onEnterRoom} />;
}

function Lobby({ code, quick, roomId, onHome, onEnterRoom }: {
  code: string; quick: boolean; roomId: string; onHome: () => void; onEnterRoom: (roomId: string) => void;
}) {
  const [copied, setCopied] = useState<string | null>(null);

  // Quick match: keep looking (another waiting player may have appeared), stay "seen", and tidy up on leaving.
  useEffect(() => {
    if (!quick) return;
    let alive = true;
    const look = async () => {
      void touchPresence();
      try {
        const r = await quickMatch();
        if (alive && r.room_id !== roomId) onEnterRoom(r.room_id);
      } catch { /* try again on the next tick */ }
    };
    const t = setInterval(look, 4000);
    return () => { alive = false; clearInterval(t); void cancelQuickMatch(); };
  }, [quick, roomId, onEnterRoom]);

  const link = shareLink(code);
  const copy = async (text: string, what: string) => {
    try { await navigator.clipboard.writeText(text); setCopied(what); setTimeout(() => setCopied(null), 1800); } catch { setCopied("press and hold to copy"); }
  };
  const share = async () => {
    const data = { title: "Quoridor", text: `Join my Quoridor game! Code: ${code}`, url: link };
    if (navigator.share) { try { await navigator.share(data); return; } catch { return; } }
    await copy(`${data.text} ${link}`, "link");
  };
  return (
    <div className="home">
      <div className="home-inner">
        <h2>{quick ? "Looking for an opponent…" : "Waiting for an opponent…"}</h2>
        {quick && <div className="spinner" role="status" aria-label="Searching" />}
        <p className="muted">{quick ? "You'll be paired with the next player who searches. Or invite a friend:" : "Share this code or link with a friend."}</p>
        <div className="code" aria-label={`Room code ${code.split("").join(" ")}`}>{code}</div>
        <div className="stack">
          <button className="primary" onClick={share}>Share invite link</button>
          <button className="secondary" onClick={() => copy(code, "code")}>Copy code</button>
        </div>
        <p className="ok small" aria-live="polite">{copied ? `Copied ${copied}` : " "}</p>
        <p className="soon">{link}</p>
        <button className="secondary" onClick={onHome}>{quick ? "Cancel" : "Back to home"}</button>
      </div>
    </div>
  );
}

function Playing({ roomId, me, match, onHome, onEnterRoom }: {
  roomId: string; me: number; match: MatchRow; onHome: () => void; onEnterRoom: (roomId: string) => void;
}) {
  const [opponent, setOpponent] = useState("Opponent");
  useEffect(() => {
    let alive = true, tries = 0;
    const find = async () => {
      try {
        const o = await getOpponent(roomId);
        if (alive && o) return setOpponent(o.display_name);
      } catch { /* retry */ }
      if (alive && ++tries < 5) setTimeout(find, 1500);
    };
    void find();
    return () => { alive = false; };
  }, [roomId]);

  const g = useOnlineGame(roomId, me, match, opponent, onEnterRoom);
  const home = () => {
    if (g.finished) { try { localStorage.removeItem(ROOM_KEY); } catch { /* ignore */ } }
    onHome();
  };
  return <GameScreen g={g} onHome={home} />;
}
