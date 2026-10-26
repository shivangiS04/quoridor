import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { corsResponse, jsonResponse, errorResponse } from "../_shared/cors.ts";
import { applyTimedAction, GameState } from "../_shared/quoridor.ts";

export async function handleRequest(
  req: Request,
  makeClient = createClient,
  env: (name: string) => string | undefined = (name) => Deno.env.get(name),
): Promise<Response> {
  if (req.method === "OPTIONS") return corsResponse();

  const anon = makeClient(
    env("SUPABASE_URL")!,
    env("SUPABASE_ANON_KEY")!,
    { global: { headers: { Authorization: req.headers.get("Authorization")! } } },
  );
  const svc = makeClient(
    env("SUPABASE_URL")!,
    env("SUPABASE_SERVICE_ROLE_KEY")!,
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

  // Run independent database reads in parallel.
  const [membership, current] = await Promise.all([
    svc.from("room_members").select("player_index").eq("room_id", room_id).eq("player_id", user.id).maybeSingle(),
    svc.from("matches").select("state, revision, updated_at").eq("room_id", room_id).maybeSingle(),
  ]);
  if (membership.error || current.error) return errorResponse("Could not load match", 500);
  const member = membership.data;
  const match = current.data;
  if (!member) return errorResponse("Not a room member", 403);
  if (!match) return errorResponse("Match not found", 404);

  if (match.revision !== revision) return errorResponse("Stale revision — fetch latest state", 409);

  const state: GameState = match.state as GameState;
  const now = Date.now();
  let newState: GameState;
  try {
    newState = applyTimedAction(state, member.player_index, action, Date.parse(match.updated_at), now);
  } catch (e: unknown) {
    return errorResponse((e as Error).message);
  }

  // Update with optimistic concurrency check on revision
  const { data: updated, error: updateErr } = await svc
    .from("matches")
    .update({ state: newState, revision: revision + 1, updated_at: new Date(now).toISOString() })
    .eq("room_id", room_id)
    .eq("revision", revision)
    .select("state, revision, updated_at")
    .maybeSingle();

  if (updateErr || !updated) return errorResponse("Concurrent update conflict — retry", 409);

  return jsonResponse({ ok: true, match: updated });
}
