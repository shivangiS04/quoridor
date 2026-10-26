import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { corsResponse, errorResponse, jsonResponse } from "../_shared/cors.ts";
import { claimTimeout, GameState } from "../_shared/quoridor.ts";

// The waiting player claims when the opponent has exhausted their total game time.
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

  const now = Date.now();
  let newState: GameState;
  try {
    newState = claimTimeout(
      match.state as GameState,
      member.player_index,
      new Date(match.updated_at).getTime(),
      now,
    );
  } catch (e: unknown) {
    return errorResponse((e as Error).message);
  }

  const { data: updated, error: updateErr } = await svc
    .from("matches")
    .update({ state: newState, revision: match.revision + 1, updated_at: new Date(now).toISOString() })
    .eq("room_id", roomId)
    .eq("revision", match.revision)
    .select("state, revision, updated_at")
    .maybeSingle();
  if (updateErr || !updated) return errorResponse("Concurrent update conflict — retry", 409);

  return jsonResponse({ ok: true, match: updated });
}
