import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { errorResponse, jsonResponse } from "../_shared/cors.ts";

const MAX_AGE_HOURS = 24;

// Deletes rooms (and, via ON DELETE CASCADE, their members and matches) older than
// MAX_AGE_HOURS. Meant to be called on a schedule (pg_cron / Supabase scheduled function)
// with the shared secret in the `x-cleanup-secret` header.
Deno.serve(async (req) => {
  const secret = Deno.env.get("CLEANUP_SECRET");
  if (!secret || req.headers.get("x-cleanup-secret") !== secret) {
    return errorResponse("Unauthorized", 401);
  }

  const svc = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  );
  const cutoff = new Date(Date.now() - MAX_AGE_HOURS * 3600_000).toISOString();
  const { data, error } = await svc.from("rooms").delete().lt("created_at", cutoff).select("id");
  if (error) return errorResponse(error.message, 500);

  return jsonResponse({ deleted: data?.length ?? 0 });
});
