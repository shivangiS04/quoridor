-- Quick match (pair with a random waiting player) and rematch (same two players, seats swapped).

ALTER TABLE public.rooms   ADD COLUMN is_public BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE public.matches ADD COLUMN rematch_room_id UUID REFERENCES public.rooms(id) ON DELETE SET NULL;

-- ---------------------------------------------------------------------------------------------
-- Helpers (not callable by clients)
-- ---------------------------------------------------------------------------------------------

-- A unique 8-character room code, same alphabet as the create-room function (no 0/O/1/I).
CREATE OR REPLACE FUNCTION public.new_room_code() RETURNS TEXT LANGUAGE plpgsql AS $$
DECLARE
  chars  TEXT := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  v_code TEXT;
  i      INTEGER;
BEGIN
  LOOP
    v_code := '';
    FOR i IN 1..8 LOOP
      v_code := v_code || substr(chars, 1 + floor(random() * 32)::INTEGER, 1);
    END LOOP;
    EXIT WHEN NOT EXISTS (SELECT 1 FROM public.rooms WHERE rooms.code = v_code);
  END LOOP;
  RETURN v_code;
END $$;

-- The starting position. MUST match initialState() in supabase/functions/_shared/quoridor.ts
-- (a test on each side pins the same literal).
CREATE OR REPLACE FUNCTION public.initial_match_state(first_turn INTEGER) RETURNS jsonb
LANGUAGE sql IMMUTABLE AS $$
  SELECT jsonb_build_object(
    'players', jsonb_build_array(
      jsonb_build_object('row', 8, 'col', 4, 'walls_left', 10),
      jsonb_build_object('row', 0, 'col', 4, 'walls_left', 10)),
    'walls', jsonb_build_object('horizontal', '[]'::jsonb, 'vertical', '[]'::jsonb),
    'current_turn', first_turn,
    'status', 'playing',
    'winner', NULL::INTEGER);
$$;

REVOKE EXECUTE ON FUNCTION public.new_room_code(), public.initial_match_state(INTEGER) FROM PUBLIC, anon, authenticated;

-- ---------------------------------------------------------------------------------------------
-- quick_match(): join a random waiting player, or open a public room and wait.
-- Returns {room_id, player_index, status: 'started' | 'waiting', code?}.
-- A waiting public room only counts while it is fresh (created < 3 min ago) and its host has been seen
-- in the last 90 s (the lobby sends a heartbeat), so nobody is matched with someone who left.
-- Calls are serialized with an advisory lock: two players searching at the same instant can never end up
-- joining each other's separate rooms.
-- ---------------------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.quick_match() RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  uid    TEXT := auth.uid()::TEXT;
  rid    UUID;
  v_code TEXT;
  first  INTEGER;
BEGIN
  IF uid IS NULL THEN RAISE EXCEPTION 'Not signed in'; END IF;
  PERFORM pg_advisory_xact_lock(hashtext('quoridor.quick_match'));

  -- 1. someone else is waiting: take their room
  SELECT r.id INTO rid
    FROM public.rooms r
    JOIN public.room_members m ON m.room_id = r.id AND m.player_index = 0
    JOIN public.profiles p ON p.id = m.player_id::UUID
   WHERE r.is_public
     AND m.player_id <> uid
     AND r.created_at > now() - interval '3 minutes'
     AND p.last_seen  > now() - interval '90 seconds'
     AND NOT EXISTS (SELECT 1 FROM public.matches mt WHERE mt.room_id = r.id)
     AND (SELECT count(*) FROM public.room_members x WHERE x.room_id = r.id) = 1
   ORDER BY r.created_at
   LIMIT 1
   FOR UPDATE OF r SKIP LOCKED;
  IF FOUND THEN
    -- my own waiting rooms are now pointless
    DELETE FROM public.rooms r
     WHERE r.is_public
       AND NOT EXISTS (SELECT 1 FROM public.matches mt WHERE mt.room_id = r.id)
       AND EXISTS (SELECT 1 FROM public.room_members m WHERE m.room_id = r.id AND m.player_id = uid AND m.player_index = 0);
    INSERT INTO public.room_members (room_id, player_id, player_index) VALUES (rid, uid, 1);
    first := floor(random() * 2)::INTEGER;
    INSERT INTO public.matches (room_id, state, revision) VALUES (rid, public.initial_match_state(first), 0);
    RETURN jsonb_build_object('room_id', rid, 'player_index', 1, 'status', 'started');
  END IF;

  -- 2. I am already waiting in a fresh public room: keep it
  SELECT r.id, r.code INTO rid, v_code
    FROM public.rooms r
    JOIN public.room_members m ON m.room_id = r.id AND m.player_id = uid AND m.player_index = 0
   WHERE r.is_public
     AND r.created_at > now() - interval '3 minutes'
     AND NOT EXISTS (SELECT 1 FROM public.matches mt WHERE mt.room_id = r.id)
   ORDER BY r.created_at DESC
   LIMIT 1;
  IF FOUND THEN
    RETURN jsonb_build_object('room_id', rid, 'code', v_code, 'player_index', 0, 'status', 'waiting');
  END IF;

  -- 3. nobody waiting: open a public room
  v_code := public.new_room_code();
  INSERT INTO public.rooms (code, is_public) VALUES (v_code, TRUE) RETURNING id INTO rid;
  INSERT INTO public.room_members (room_id, player_id, player_index) VALUES (rid, uid, 0);
  RETURN jsonb_build_object('room_id', rid, 'code', v_code, 'player_index', 0, 'status', 'waiting');
END $$;

-- ---------------------------------------------------------------------------------------------
-- request_rematch(room): same two players in a NEW room, seats swapped (the player who moved second now
-- moves first). Idempotent: whoever asks first creates it, the other player (and any repeat) gets the same room.
-- The finished match row records rematch_room_id, so the other player's screen learns about it through the
-- realtime update on matches.
-- ---------------------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.request_rematch(p_room_id UUID) RETURNS UUID
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  uid      TEXT := auth.uid()::TEXT;
  st       jsonb;
  existing UUID;
  new_room UUID;
  v_code   TEXT;
BEGIN
  IF uid IS NULL OR NOT EXISTS (SELECT 1 FROM public.room_members WHERE room_id = p_room_id AND player_id = uid) THEN
    RAISE EXCEPTION 'Not a room member';
  END IF;
  SELECT state, rematch_room_id INTO st, existing FROM public.matches WHERE room_id = p_room_id FOR UPDATE;
  IF st IS NULL THEN RAISE EXCEPTION 'No match in this room'; END IF;
  IF st->>'status' <> 'finished' THEN RAISE EXCEPTION 'The game is not finished'; END IF;
  IF existing IS NOT NULL THEN RETURN existing; END IF;

  v_code := public.new_room_code();
  INSERT INTO public.rooms (code) VALUES (v_code) RETURNING id INTO new_room;
  INSERT INTO public.room_members (room_id, player_id, player_index)
    SELECT new_room, player_id, 1 - player_index FROM public.room_members WHERE room_id = p_room_id;
  INSERT INTO public.matches (room_id, state, revision) VALUES (new_room, public.initial_match_state(0), 0);
  UPDATE public.matches SET rematch_room_id = new_room WHERE room_id = p_room_id;
  RETURN new_room;
END $$;

REVOKE EXECUTE ON FUNCTION public.quick_match(), public.request_rematch(UUID) FROM PUBLIC, anon;
GRANT  EXECUTE ON FUNCTION public.quick_match(), public.request_rematch(UUID) TO authenticated;

-- ---------------------------------------------------------------------------------------------
-- cancel_quick_match(): stop waiting. Removes the caller's public rooms that nobody has joined yet
-- (never a game in progress, never anyone else's room).
-- ---------------------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.cancel_quick_match() RETURNS void
LANGUAGE sql SECURITY DEFINER SET search_path = public AS $$
  DELETE FROM public.rooms r
   WHERE r.is_public
     AND NOT EXISTS (SELECT 1 FROM public.matches mt WHERE mt.room_id = r.id)
     AND EXISTS (SELECT 1 FROM public.room_members m
                  WHERE m.room_id = r.id AND m.player_id = auth.uid()::TEXT AND m.player_index = 0);
$$;
REVOKE EXECUTE ON FUNCTION public.cancel_quick_match() FROM PUBLIC, anon;
GRANT  EXECUTE ON FUNCTION public.cancel_quick_match() TO authenticated;
