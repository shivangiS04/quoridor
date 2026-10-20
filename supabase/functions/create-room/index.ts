import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { corsHeaders, corsResponse, jsonResponse, errorResponse } from "../_shared/cors.ts";

function randomCode(): string {
  const chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  return Array.from({ length: 8 }, () => chars[Math.floor(Math.random() * chars.length)]).join("");
}

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

  // Generate unique code (retry on collision)
  let code = "";
  let roomId = "";
  for (let attempt = 0; attempt < 5; attempt++) {
    code = randomCode();
    const { data, error } = await svc.from("rooms").insert({ code }).select("id").single();
    if (!error && data) { roomId = data.id; break; }
    if (error?.code !== "23505") return errorResponse("Failed to create room"); // non-unique error
  }
  if (!roomId) return errorResponse("Could not generate unique code; try again");

  // Add creator as player 0
  const { error: memberErr } = await svc.from("room_members").insert({
    room_id: roomId,
    player_id: user.id,
    player_index: 0,
  });
  if (memberErr) return errorResponse("Failed to join room");

  return jsonResponse({ room_id: roomId, code });
});
