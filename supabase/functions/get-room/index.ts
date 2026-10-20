import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { corsResponse, jsonResponse, errorResponse } from "../_shared/cors.ts";

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

  const url = new URL(req.url);
  const roomId = url.searchParams.get("room_id");
  if (!roomId) return errorResponse("room_id is required");

  // Verify membership
  const { data: member } = await svc
    .from("room_members")
    .select("player_index")
    .eq("room_id", roomId)
    .eq("player_id", user.id)
    .single();
  if (!member) return errorResponse("Not a room member", 403);

  const { data: room } = await svc.from("rooms").select("id, code").eq("id", roomId).single();
  if (!room) return errorResponse("Room not found", 404);

  const { data: match } = await svc
    .from("matches")
    .select("state, revision")
    .eq("room_id", roomId)
    .maybeSingle();

  return jsonResponse({
    room_id: room.id,
    code: room.code,
    player_index: member.player_index,
    match: match ?? null,
  });
});
