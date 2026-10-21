import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { corsResponse, errorResponse, jsonResponse } from "../_shared/cors.ts";
import { claimTimeout, GameState } from "../_shared/quoridor.ts";

// The waiting player calls this once the opponent has been idle past TURN_TIMEOUT_MS.
Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return corsResponse();

  const anon = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_ANON_KEY")!,
    { global: { headers: { Authorization: req.headers.get("Authorization")! } } },
  );
  const svc = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  );

  const { data: { user }, error: authErr } = await anon.auth.getUser();
  if (authErr || !user) return errorResponse("Unauthorized", 401);

  let roomId: unknown;
  try {
    ({ room_id: roomId } = await req.json());
  } catch {
    return errorResponse("Invalid JSON body");
  }
  if (typeof roomId !== "string" || !roomId) return errorResponse("room_id is required");

  const { data: member } = await svc
    .from("room_members")
    .select("player_index")
    .eq("room_id", roomId)
    .eq("player_id", user.id)
    .maybeSingle();
  if (!member) return errorResponse("Not a room member", 403);

  const { data: match } = await svc
    .from("matches")
    .select("state, revision, updated_at")
    .eq("room_id", roomId)
    .maybeSingle();
  if (!match) return errorResponse("Match not found", 404);

  let newState: GameState;
  try {
    newState = claimTimeout(
      match.state as GameState,
      member.player_index,
      new Date(match.updated_at).getTime(),
      Date.now(),
    );
  } catch (e: unknown) {
    return errorResponse((e as Error).message);
  }

  const { data: updated, error: updateErr } = await svc
    .from("matches")
    .update({ state: newState, revision: match.revision + 1, updated_at: new Date().toISOString() })
    .eq("room_id", roomId)
    .eq("revision", match.revision)
    .select("revision");
  if (updateErr || !updated?.length) return errorResponse("Concurrent update conflict — retry", 409);

  return jsonResponse({ ok: true });
});
