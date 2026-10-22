import { useCallback, useEffect, useState } from "react";
import { getMyProfile, type Profile, renameProfile } from "../lib/api.ts";
import { useAuth } from "../lib/auth.tsx";

export function Avatar({ name, url, size = 96 }: { name: string; url: string | null; size?: number }) {
  return (
    <div className="avatar" style={{ width: size, height: size, fontSize: size / 2.4 }}>
      {url ? <img src={url} alt={`${name}'s photo`} referrerPolicy="no-referrer" /> : name.slice(0, 1).toUpperCase()}
    </div>
  );
}

export function ProfileScreen({ onBack }: { onBack: () => void }) {
  const auth = useAuth();
  const userId = auth.user!.id;
  const [profile, setProfile] = useState<Profile | null>(null);
  const [draft, setDraft] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    try {
      const p = await getMyProfile(userId);
      setProfile(p);
      setDraft((d) => (d === "" || d === profile?.display_name ? p.display_name : d));
      setError(null);
    } catch (e) { setError(e instanceof Error ? e.message : "Could not load your profile."); }
  }, [userId, profile?.display_name]);
  useEffect(() => { void load(); }, [userId]); // eslint-disable-line react-hooks/exhaustive-deps

  const save = async () => {
    setSaving(true); setError(null); setMessage(null);
    try { await renameProfile(userId, draft); setMessage("Name saved"); setProfile(null); await load(); }
    catch (e) { setError(e instanceof Error ? e.message : "Could not save."); }
    setSaving(false);
  };
  const act = async (fn: () => Promise<void>) => { try { await fn(); } catch (e) { setError(e instanceof Error ? e.message : "Something went wrong."); } };

  return (
    <div className="page">
      <header className="topbar">
        <button className="icon" onClick={onBack} aria-label="Back">←</button>
        <h2 className="title">Profile</h2>
      </header>
      {!profile ? (error ? <p className="warn block">{error}</p> : <p className="muted center">Loading…</p>) : (
        <div className="profile">
          <Avatar name={profile.display_name} url={profile.photo_url} />
          <p className="muted small">{profile.is_guest ? "Guest account" : "Google account"}</p>
          <label className="field">
            <span>Display name</span>
            <input value={draft} maxLength={30} onChange={(e) => { setDraft(e.target.value); setMessage(null); }} />
          </label>
          <button className="primary" disabled={saving || draft.trim() === profile.display_name || draft.trim() === ""} onClick={save}>Save name</button>
          {message && <p className="ok small">{message}</p>}
          {error && <p className="warn block">{error}</p>}

          <div className="statgrid">
            <Stat label="Games" v={profile.games_played} /><Stat label="Wins" v={profile.wins} /><Stat label="Losses" v={profile.losses} />
            <Stat label="Wins vs computer" v={profile.ai_wins} wide /><Stat label="Online wins" v={profile.online_wins} wide />
          </div>

          {profile.is_guest && (
            <>
              <button className="secondary" onClick={() => act(auth.linkGoogle)}>Link Google account</button>
              <p className="muted small">Keeps your stats if you change device.</p>
            </>
          )}
          <button className="secondary danger-outline" onClick={() => act(auth.signOut)}>Sign out</button>
          {profile.is_guest && <p className="muted small">Signing out of a guest account loses its stats for good.</p>}
        </div>
      )}
    </div>
  );
}

const Stat = ({ label, v, wide }: { label: string; v: number; wide?: boolean }) => (
  <div className={`stat${wide ? " wide" : ""}`}><strong>{v}</strong><span>{label}</span></div>
);
