import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { corsResponse, jsonResponse, errorResponse } from "../_shared/cors.ts";
import { initialState } from "../_shared/quoridor.ts";

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

  let code: unknown;
  try {
    ({ code } = await req.json());
  } catch {
    return errorResponse("Invalid JSON body");
  }
  if (typeof code !== "string" || !code) return errorResponse("code is required");

  // Look up room
  const { data: room, error: roomErr } = await svc
    .from("rooms")
    .select("id")
    .eq("code", code.trim().toUpperCase())
    .maybeSingle();
  if (roomErr || !room) return errorResponse("Room not found", 404);

  // Check room capacity
  const { data: members } = await svc
    .from("room_members")
    .select("player_id, player_index")
    .eq("room_id", room.id);

  if (!members) return errorResponse("Failed to read room");
  // Already a member? (rejoining is allowed even when the room is full)
  const existing = members.find((m) => m.player_id === user.id);
  if (existing) {
    return jsonResponse({
      room_id: room.id,
      player_index: existing.player_index,
      status: members.length >= 2 ? "started" : "waiting",
    });
  }

  if (members.length >= 2) return errorResponse("Room is full", 409);

  // Add as player 1
  const { error: joinErr } = await svc.from("room_members").insert({
    room_id: room.id,
    player_id: user.id,
    player_index: 1,
  });
  // Unique violation on (room_id, player_index): someone else took the seat first
  if (joinErr?.code === "23505") return errorResponse("Room is full", 409);
  if (joinErr) return errorResponse("Failed to join");

  // Both players present — create match with random first turn
  const firstTurn = Math.random() < 0.5 ? 0 : 1;
  const { error: matchErr } = await svc.from("matches").insert({
    room_id: room.id,
    state: initialState(firstTurn as 0 | 1),
    revision: 0,
  });
  if (matchErr) return errorResponse("Failed to start match");

  return jsonResponse({ room_id: room.id, player_index: 1, status: "started" });
});
