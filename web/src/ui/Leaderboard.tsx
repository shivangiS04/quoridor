import { useEffect, useState } from "react";
import { getLeaderboard, type LeaderboardEntry } from "../lib/api.ts";
import { useAuth } from "../lib/auth.tsx";
import { Avatar } from "./Profile.tsx";

export function LeaderboardScreen({ onBack }: { onBack: () => void }) {
  const { user } = useAuth();
  const [rows, setRows] = useState<LeaderboardEntry[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  useEffect(() => {
    let alive = true;
    getLeaderboard(10).then((r) => { if (alive) setRows(r); }).catch((e) => { if (alive) setError(e instanceof Error ? e.message : "Could not load the leaderboard."); });
    return () => { alive = false; };
  }, []);

  return (
    <div className="page">
      <header className="topbar">
        <button className="icon" onClick={onBack} aria-label="Back">←</button>
        <h2 className="title">Leaderboard</h2>
      </header>
      <p className="muted small lb-sub">Top players by online wins</p>
      {error ? <p className="warn block">{error}</p>
        : !rows ? <p className="muted center">Loading…</p>
        : rows.length === 0 ? <p className="muted center">No online wins yet. Win a game against another player to be the first!</p>
        : (
          <ol className="lb">
            {rows.map((r, i) => (
              <li key={r.id} className={r.id === user?.id ? "me" : ""} aria-label={`Rank ${i + 1}, ${r.display_name}${r.id === user?.id ? ", you" : ""}, ${r.online_wins} online wins`}>
                <span className={`rank${i < 3 ? " top" : ""}`}>{i + 1}</span>
                <Avatar name={r.display_name} url={r.photo_url} size={36} />
                <span className="lb-name"><b>{r.id === user?.id ? `${r.display_name} (you)` : r.display_name}</b><small className="muted">{r.games_played} games</small></span>
                <span className="lb-wins"><strong>{r.online_wins}</strong><small className="muted"> wins</small></span>
              </li>
            ))}
          </ol>
        )}
    </div>
  );
}
