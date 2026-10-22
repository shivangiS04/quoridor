import { useState } from "react";
import { DIFFICULTIES, DIFFICULTY_LABEL, type Difficulty } from "../game/ai.ts";
import { onlineConfigured } from "../lib/supabase.ts";

interface Props {
  onPlay: (d: Difficulty) => void;
  onCreate: () => void;
  onJoin: (code: string) => void;
  onProfile: () => void;
  onSignIn: () => void;
  signedIn: boolean;
  guest: boolean;
  resume: boolean;
  onResume: () => void;
  busy: boolean;
  error: string | null;
}

export function Home(p: Props) {
  const [code, setCode] = useState("");
  const valid = code.trim().length === 8;
  return (
    <div className="home">
      {onlineConfigured && (
        <div className="account">
          {p.signedIn
            ? <button className="chip" onClick={p.onProfile}>👤 {p.guest ? "Guest · Profile" : "Profile"}</button>
            : <button className="chip" onClick={p.onSignIn}>Sign in</button>}
        </div>
      )}
      <div className="home-inner">
        <p className="dots"><span className="mine" /><span className="theirs" /></p>
        <h1>Quoridor</h1>
        <p className="muted">Race to the other side. Block your opponent with walls.</p>

        <h3>Play the computer</h3>
        <div className="levels">
          {DIFFICULTIES.map((d) => (
            <button key={d} className="level" onClick={() => p.onPlay(d)}>{DIFFICULTY_LABEL[d]}</button>
          ))}
        </div>

        {onlineConfigured && (
          <>
            <hr />
            {p.resume && <button className="primary" onClick={p.onResume}>Resume game</button>}
            <button className={p.resume ? "secondary" : "primary"} disabled={p.busy} onClick={p.onCreate}>Create room</button>
            <form className="join" onSubmit={(e) => { e.preventDefault(); if (valid) p.onJoin(code); }}>
              <input
                value={code}
                onChange={(e) => setCode(e.target.value.toUpperCase().replace(/[^A-Z0-9]/g, "").slice(0, 8))}
                placeholder="Room code"
                aria-label="Room code"
                autoCapitalize="characters"
                autoComplete="off"
                spellCheck={false}
              />
              <button type="submit" className="secondary" disabled={!valid || p.busy}>Join room</button>
            </form>
            {p.error && <p className="warn block">{p.error}</p>}
          </>
        )}

        <footer>
          <a href="/privacy">Privacy</a> · <a href="https://github.com/shivangiS04/quoridor">Source</a>
        </footer>
      </div>
    </div>
  );
}
