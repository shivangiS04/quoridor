-- Player profiles and stats. One row per auth user (guest or Google).

CREATE TABLE public.profiles (
  id            UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  display_name  TEXT NOT NULL CHECK (char_length(display_name) BETWEEN 1 AND 30),
  photo_url     TEXT,
  is_guest      BOOLEAN NOT NULL DEFAULT TRUE,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  last_seen     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  games_played  INTEGER NOT NULL DEFAULT 0,
  wins          INTEGER NOT NULL DEFAULT 0,
  losses        INTEGER NOT NULL DEFAULT 0,
  ai_wins       INTEGER NOT NULL DEFAULT 0,
  online_wins   INTEGER NOT NULL DEFAULT 0
);

ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;

-- Any signed-in player can read profiles (opponent names, leaderboard).
CREATE POLICY "profiles_read" ON public.profiles FOR SELECT TO authenticated USING (true);
-- A player can edit only their own row...
CREATE POLICY "profiles_update_own" ON public.profiles FOR UPDATE TO authenticated
  USING (id = auth.uid()) WITH CHECK (id = auth.uid());

-- ...and only these columns. Stats can never be written directly by a client:
-- they change only through the SECURITY DEFINER functions below (and, later, edge functions).
REVOKE ALL ON public.profiles FROM anon, authenticated;
GRANT SELECT ON public.profiles TO authenticated;
GRANT UPDATE (display_name, photo_url, last_seen) ON public.profiles TO authenticated;

-- Create the profile when a user is created, and flip is_guest when a guest links a real account.
-- Wrapped in an exception handler so a profile problem can never block sign-in.
CREATE OR REPLACE FUNCTION public.sync_profile() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  real_name TEXT := COALESCE(NULLIF(NEW.raw_user_meta_data->>'full_name', ''), NULLIF(NEW.raw_user_meta_data->>'name', ''));
  photo     TEXT := COALESCE(NULLIF(NEW.raw_user_meta_data->>'avatar_url', ''), NULLIF(NEW.raw_user_meta_data->>'picture', ''));
  guest     BOOLEAN := COALESCE(NEW.is_anonymous, FALSE);
BEGIN
  IF TG_OP = 'INSERT' THEN
    INSERT INTO public.profiles (id, display_name, photo_url, is_guest)
    VALUES (NEW.id, COALESCE(real_name, 'Guest ' || lpad((1000 + floor(random() * 9000))::int::text, 4, '0')), photo, guest)
    ON CONFLICT (id) DO NOTHING;
  ELSE
    -- Guest upgraded to a real account: keep their stats, adopt the Google name/photo if they still had a guest name.
    UPDATE public.profiles
       SET is_guest = guest,
           display_name = CASE WHEN real_name IS NOT NULL AND display_name LIKE 'Guest %' THEN real_name ELSE display_name END,
           photo_url = COALESCE(photo_url, photo)
     WHERE id = NEW.id;
  END IF;
  RETURN NEW;
EXCEPTION WHEN OTHERS THEN
  RAISE WARNING 'sync_profile failed for %: %', NEW.id, SQLERRM;
  RETURN NEW;
END $$;

CREATE TRIGGER on_auth_user_created AFTER INSERT ON auth.users
  FOR EACH ROW EXECUTE FUNCTION public.sync_profile();
CREATE TRIGGER on_auth_user_upgraded AFTER UPDATE ON auth.users
  FOR EACH ROW WHEN (OLD.is_anonymous IS DISTINCT FROM NEW.is_anonymous)
  EXECUTE FUNCTION public.sync_profile();

-- Users that already exist (e.g. test guests) get a profile too.
INSERT INTO public.profiles (id, display_name, photo_url, is_guest)
SELECT u.id,
       COALESCE(NULLIF(u.raw_user_meta_data->>'full_name', ''), NULLIF(u.raw_user_meta_data->>'name', ''),
                'Guest ' || lpad((1000 + floor(random() * 9000))::int::text, 4, '0')),
       COALESCE(NULLIF(u.raw_user_meta_data->>'avatar_url', ''), NULLIF(u.raw_user_meta_data->>'picture', '')),
       COALESCE(u.is_anonymous, FALSE)
FROM auth.users u
ON CONFLICT (id) DO NOTHING;

-- Record the result of a game against the computer. This is played on the device, so the server
-- cannot verify it: treat ai_wins as a casual stat, never as something to rank or reward on.
CREATE OR REPLACE FUNCTION public.record_ai_game(won BOOLEAN) RETURNS void
LANGUAGE sql SECURITY DEFINER SET search_path = public AS $$
  UPDATE public.profiles
     SET games_played = games_played + 1,
         wins   = wins   + CASE WHEN won THEN 1 ELSE 0 END,
         losses = losses + CASE WHEN won THEN 0 ELSE 1 END,
         ai_wins = ai_wins + CASE WHEN won THEN 1 ELSE 0 END
   WHERE id = auth.uid();
$$;
REVOKE EXECUTE ON FUNCTION public.record_ai_game(BOOLEAN) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.record_ai_game(BOOLEAN) TO authenticated;
