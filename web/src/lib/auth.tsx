import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import type { Session, User } from "@supabase/supabase-js";
import { ApiError, client, onlineConfigured, supabase } from "./supabase.ts";

interface AuthValue {
  /** False until the saved session (if any) has been read. */
  ready: boolean;
  session: Session | null;
  user: User | null;
  isGuest: boolean;
  signInGuest: () => Promise<void>;
  signInGoogle: () => Promise<void>;
  linkGoogle: () => Promise<void>;
  signOut: () => Promise<void>;
}

const Ctx = createContext<AuthValue | null>(null);
export const useAuth = () => {
  const v = useContext(Ctx);
  if (!v) throw new Error("useAuth outside AuthProvider");
  return v;
};

const redirect = () => `${window.location.origin}/`;

export function AuthProvider({ children }: { children: ReactNode }) {
  const [ready, setReady] = useState(!onlineConfigured);
  const [session, setSession] = useState<Session | null>(null);

  useEffect(() => {
    if (!supabase) return;
    void supabase.auth.getSession().then(({ data }) => { setSession(data.session); setReady(true); });
    const { data } = supabase.auth.onAuthStateChange((_event, s) => setSession(s));
    return () => data.subscription.unsubscribe();
  }, []);

  const wrap = useCallback(async (fn: () => Promise<{ error: { message: string } | null }>, fallback: string) => {
    try {
      const { error } = await fn();
      if (error) throw new ApiError(error.message);
    } catch (e) {
      throw e instanceof ApiError ? e : new ApiError(`${fallback}. Check your connection and try again.`);
    }
  }, []);

  const value = useMemo<AuthValue>(() => ({
    ready,
    session,
    user: session?.user ?? null,
    isGuest: session?.user?.is_anonymous === true,
    signInGuest: () => wrap(() => client().auth.signInAnonymously(), "Could not start a guest session"),
    // These redirect the page to Google and back; the session arrives when the page reloads.
    signInGoogle: () => wrap(() => client().auth.signInWithOAuth({ provider: "google", options: { redirectTo: redirect() } }), "Google sign-in failed"),
    linkGoogle: () => wrap(() => client().auth.linkIdentity({ provider: "google", options: { redirectTo: redirect() } }), "Could not link your Google account"),
    signOut: () => wrap(() => client().auth.signOut(), "Could not sign out"),
  }), [ready, session, wrap]);

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}
