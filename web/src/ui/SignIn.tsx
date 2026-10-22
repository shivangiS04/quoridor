import { useState } from "react";
import { useAuth } from "../lib/auth.tsx";

/** Shown when something needs an account (online rooms, profile): Google, or a guest account. */
export function SignIn({ reason, onBack }: { reason: string; onBack: () => void }) {
  const auth = useAuth();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const run = async (fn: () => Promise<void>) => {
    setBusy(true);
    setError(null);
    try { await fn(); } catch (e) { setError(e instanceof Error ? e.message : "Something went wrong."); }
    setBusy(false);
  };

  return (
    <div className="home">
      <div className="home-inner">
        <button className="back" onClick={onBack} aria-label="Back">←</button>
        <h1>Quoridor</h1>
        <p className="muted">{reason}</p>
        <div className="stack">
          <button className="primary" disabled={busy} onClick={() => run(auth.signInGoogle)}>Continue with Google</button>
          <button className="secondary" disabled={busy} onClick={() => run(auth.signInGuest)}>Play as guest</button>
        </div>
        {error && <p className="warn block">{error}</p>}
        <p className="soon">Guests can link a Google account later and keep their stats.</p>
      </div>
    </div>
  );
}
