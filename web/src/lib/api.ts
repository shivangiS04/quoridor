import { FunctionsFetchError, FunctionsHttpError } from "@supabase/supabase-js";
import type { GameState } from "../game/rules.ts";
import type { Action } from "../game/rules.ts";
import { ApiError, client } from "./supabase.ts";

export interface MatchRow { state: GameState; revision: number; updated_at: string | null }
export interface RoomInfo { room_id: string; code: string; player_index: number; match: MatchRow | null }
export interface Profile {
  id: string; display_name: string; photo_url: string | null; is_guest: boolean;
  games_played: number; wins: number; losses: number; ai_wins: number; online_wins: number;
}

const OFFLINE = "Can't reach the server. Check your connection.";

async function call<T>(name: string, options: { method?: "GET" | "POST"; body?: unknown } = {}): Promise<T> {
  const { data, error } = await client().functions.invoke(name, { method: options.method ?? "POST", body: options.body as never });
  if (error) {
    if (error instanceof FunctionsHttpError) {
      let message = `Request failed (${error.context.status})`;
      try { message = (await error.context.json())?.error ?? message; } catch { /* not JSON */ }
      throw new ApiError(message, error.context.status);
    }
    throw new ApiError(error instanceof FunctionsFetchError ? OFFLINE : error.message);
  }
  return data as T;
}

async function rpc<T>(fn: string, args: Record<string, unknown> = {}): Promise<T> {
  const { data, error } = await client().rpc(fn, args);
  if (error) throw new ApiError(error.message, Number(error.code) || undefined);
  return data as T;
}

export const createRoom = () => call<{ room_id: string; code: string }>("create-room");
export const joinRoom = (code: string) => call<{ room_id: string; player_index: number; status: string }>("join-room", { body: { code: code.trim().toUpperCase() } });
export const getRoom = (roomId: string) => call<RoomInfo>(`get-room?room_id=${encodeURIComponent(roomId)}`, { method: "GET" });
export const submitAction = (roomId: string, revision: number, action: Action | { type: "resign" }) =>
  call<{ ok: boolean }>("submit-action", { body: { room_id: roomId, revision, action } });
export const claimTimeout = (roomId: string) => call<{ ok: boolean }>("claim-timeout", { body: { room_id: roomId } });

/** Name and photo of the player on the other side of a room, or null until someone has joined. */
export const getOpponent = (roomId: string) =>
  rpc<{ display_name: string; photo_url: string | null } | null>("get_room_opponent", { p_room_id: roomId });

export async function getMyProfile(userId: string): Promise<Profile> {
  const { data, error } = await client().from("profiles").select("*").eq("id", userId).single();
  if (error) throw new ApiError(error.message);
  return data as Profile;
}

export async function renameProfile(userId: string, name: string): Promise<void> {
  const clean = name.trim();
  if (clean.length < 1 || clean.length > 30) throw new ApiError("Pick a name between 1 and 30 characters.");
  const { error } = await client().from("profiles").update({ display_name: clean }).eq("id", userId);
  if (error) throw new ApiError(error.message);
}

/** Best effort: add a finished game against the computer to the player's stats. Never throws (offline is fine). */
export async function recordAiGame(won: boolean): Promise<void> {
  try { await rpc("record_ai_game", { won }); } catch { /* ignore */ }
}
