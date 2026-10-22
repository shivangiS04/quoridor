import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { corsResponse, jsonResponse, errorResponse } from "../_shared/cors.ts";
import { applyAction, GameState } from "../_shared/quoridor.ts";

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

  let body;
  try {
    body = await req.json();
  } catch {
    return errorResponse("Invalid JSON body");
  }
  const { room_id, revision, action } = body ?? {};
  if (!room_id || typeof revision !== "number" || !action?.type) {
    return errorResponse("room_id, revision, and action are required");
  }

  // Verify membership
  const { data: member } = await svc
    .from("room_members")
    .select("player_index")
    .eq("room_id", room_id)
    .eq("player_id", user.id)
    .maybeSingle();
  if (!member) return errorResponse("Not a room member", 403);

  // Load current match
  const { data: match } = await svc
    .from("matches")
    .select("state, revision")
    .eq("room_id", room_id)
    .maybeSingle();
  if (!match) return errorResponse("Match not found", 404);

  if (match.revision !== revision) return errorResponse("Stale revision — fetch latest state", 409);

  const state: GameState = match.state as GameState;
  if (state.status !== "playing") return errorResponse("Match is not in progress");
  if (state.current_turn !== member.player_index) return errorResponse("Not your turn");

  let newState: GameState;
  try {
    newState = applyAction(state, member.player_index, action);
  } catch (e: unknown) {
    return errorResponse((e as Error).message);
  }

  // Update with optimistic concurrency check on revision
  const { error: updateErr } = await svc
    .from("matches")
    .update({ state: newState, revision: revision + 1, updated_at: new Date().toISOString() })
    .eq("room_id", room_id)
    .eq("revision", revision);

  if (updateErr) return errorResponse("Concurrent update conflict — retry", 409);

  return jsonResponse({ ok: true });
});
