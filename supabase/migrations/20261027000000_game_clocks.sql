-- Persist the two game-wide balances in the match state. updated_at anchors the running clock.
-- All creation paths (room joins, quick match, rematch) receive fresh clocks, including older servers.
CREATE OR REPLACE FUNCTION public.initialize_match_clocks() RETURNS trigger
LANGUAGE plpgsql SET search_path = public AS $$
BEGIN
  NEW.state := jsonb_set(NEW.state, '{remaining_ms}', '[120000,120000]'::jsonb);
  RETURN NEW;
END $$;
REVOKE EXECUTE ON FUNCTION public.initialize_match_clocks() FROM PUBLIC, anon, authenticated;
CREATE TRIGGER initialize_match_clocks BEFORE INSERT ON public.matches
FOR EACH ROW EXECUTE FUNCTION public.initialize_match_clocks();

-- Old games have no move timestamps/history from which to recover earlier thinking time.
-- Start them with a single fresh budget at rollout; retain revision/updated_at for concurrency and the current turn.
UPDATE public.matches SET state = jsonb_set(state, '{remaining_ms}', '[120000,120000]'::jsonb)
WHERE NOT (state ? 'remaining_ms');

CREATE OR REPLACE FUNCTION public.initial_match_state(first_turn INTEGER) RETURNS jsonb
LANGUAGE sql IMMUTABLE AS $$
  SELECT jsonb_build_object(
    'players', jsonb_build_array(
      jsonb_build_object('row', 8, 'col', 4, 'walls_left', 10),
      jsonb_build_object('row', 0, 'col', 4, 'walls_left', 10)),
    'walls', jsonb_build_object('horizontal', '[]'::jsonb, 'vertical', '[]'::jsonb),
    'current_turn', first_turn,
    'status', 'playing',
    'winner', NULL::INTEGER,
    'remaining_ms', jsonb_build_array(120000, 120000));
$$;
