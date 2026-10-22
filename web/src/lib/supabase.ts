import { createClient, type SupabaseClient } from "@supabase/supabase-js";

const url = import.meta.env.VITE_SUPABASE_URL as string | undefined;
const key = import.meta.env.VITE_SUPABASE_ANON_KEY as string | undefined;

/** False when the build has no Supabase settings: the computer game still works, online features are hidden. */
export const onlineConfigured = Boolean(url && key);

/** Both values are public by design; row-level security protects the data. */
export const supabase: SupabaseClient | null = onlineConfigured
  ? createClient(url!, key!, { auth: { persistSession: true, autoRefreshToken: true, detectSessionInUrl: true } })
  : null;

export function client(): SupabaseClient {
  if (!supabase) throw new ApiError("Online play is not available in this build.");
  return supabase;
}

/** A failed request with a message that is safe to show the player. */
export class ApiError extends Error {
  constructor(message: string, public status?: number) {
    super(message);
  }
  /** The board changed under us (the opponent moved first); the caller should refetch. */
  get isStale() {
    return this.status === 409 && /stale/i.test(this.message);
  }
}
