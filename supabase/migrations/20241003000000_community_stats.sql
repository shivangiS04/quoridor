-- Phase 2: community counters, presence ("online now"), leaderboard, and automatic recording of online results.

-- ---------------------------------------------------------------------------------------------
-- Global counters (one row). Clients can never read or write this table directly; they use
-- get_community_stats() below.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE public.meta_stats (
  id           INTEGER PRIMARY KEY DEFAULT 1 CHECK (id = 1),
  total_users  INTEGER NOT NULL DEFAULT 0,
  games_played INTEGER NOT NULL DEFAULT 0
);
INSERT INTO public.meta_stats (id, total_users) SELECT 1, count(*) FROM public.profiles;

ALTER TABLE public.meta_stats ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.meta_stats FROM anon, authenticated;

-- Every new profile adds one to total_users (atomic increment). Never blocks sign-up.
CREATE OR REPLACE FUNCTION public.count_new_profile() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
  UPDATE public.meta_stats SET total_users = total_users + 1 WHERE id = 1;
  RETURN NEW;
EXCEPTION WHEN OTHERS THEN
  RAISE WARNING 'count_new_profile failed: %', SQLERRM;
  RETURN NEW;
END $$;

CREATE TRIGGER on_profile_created AFTER INSERT ON public.profiles
  FOR EACH ROW EXECUTE FUNCTION public.count_new_profile();

-- ---------------------------------------------------------------------------------------------
-- Presence: the app calls touch_presence() about once a minute while it is open.
-- "Online" means seen within the last 2 minutes.
-- ---------------------------------------------------------------------------------------------
CREATE INDEX profiles_last_seen_idx ON public.profiles (last_seen DESC);

CREATE OR REPLACE FUNCTION public.touch_presence() RETURNS void
LANGUAGE sql SECURITY DEFINER SET search_path = public AS $$
  UPDATE public.profiles SET last_seen = now() WHERE id = auth.uid();
$$;

CREATE OR REPLACE FUNCTION public.get_community_stats() RETURNS jsonb
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public AS $$
  SELECT jsonb_build_object(
    'online_count', (SELECT count(*) FROM public.profiles WHERE last_seen > now() - interval '2 minutes'),
    'total_users',  m.total_users,
    'games_played', m.games_played)
  FROM public.meta_stats m WHERE m.id = 1;
$$;

-- ---------------------------------------------------------------------------------------------
-- Leaderboard: top players by online wins (players with no online win yet are not listed).
-- ---------------------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.get_leaderboard(limit_n INTEGER DEFAULT 10) RETURNS jsonb
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public AS $$
  SELECT COALESCE(
    jsonb_agg(to_jsonb(t) - 'created_at' ORDER BY t.online_wins DESC, t.games_played ASC, t.created_at ASC),
    '[]'::jsonb)
  FROM (
    SELECT id, display_name, photo_url, online_wins, games_played, created_at
    FROM public.profiles
    WHERE online_wins > 0
    ORDER BY online_wins DESC, games_played ASC, created_at ASC
    LIMIT LEAST(GREATEST(limit_n, 1), 50)
  ) t;
$$;

REVOKE EXECUTE ON FUNCTION public.touch_presence(), public.get_community_stats(), public.get_leaderboard(INTEGER)
  FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.touch_presence(), public.get_community_stats(), public.get_leaderboard(INTEGER)
  TO authenticated;

-- ---------------------------------------------------------------------------------------------
-- Online results are recorded by the database itself, exactly once, at the moment a match first
-- becomes 'finished' (a win, a resignation or a timeout all end up here). Only the edge functions
-- (service role) can update matches, so a client cannot fake a result.
-- ---------------------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.record_finished_match() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  win_idx  INTEGER := NULLIF(NEW.state->>'winner', '')::INTEGER;
  win_uid  UUID;
  lose_uid UUID;
BEGIN
  IF win_idx IS NULL THEN RETURN NEW; END IF;
  SELECT player_id::UUID INTO win_uid  FROM public.room_members WHERE room_id = NEW.room_id AND player_index = win_idx;
  SELECT player_id::UUID INTO lose_uid FROM public.room_members WHERE room_id = NEW.room_id AND player_index = 1 - win_idx;
  IF win_uid IS NULL OR lose_uid IS NULL THEN RETURN NEW; END IF;

  UPDATE public.profiles SET games_played = games_played + 1, wins = wins + 1, online_wins = online_wins + 1
   WHERE id = win_uid;
  UPDATE public.profiles SET games_played = games_played + 1, losses = losses + 1
   WHERE id = lose_uid;
  UPDATE public.meta_stats SET games_played = games_played + 1 WHERE id = 1;
  RETURN NEW;
EXCEPTION WHEN OTHERS THEN
  -- A stats problem must never fail the move that ended the game.
  RAISE WARNING 'record_finished_match failed for room %: %', NEW.room_id, SQLERRM;
  RETURN NEW;
END $$;

CREATE TRIGGER on_match_finished AFTER UPDATE OF state ON public.matches
  FOR EACH ROW
  WHEN ((NEW.state->>'status') = 'finished' AND (OLD.state->>'status') IS DISTINCT FROM 'finished')
  EXECUTE FUNCTION public.record_finished_match();
