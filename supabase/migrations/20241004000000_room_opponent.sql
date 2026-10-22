-- Lets a player in a room learn who they are playing against (name and photo only).
-- room_members is private (you can only see your own row), so this is the one safe doorway:
-- it answers only for rooms the caller is a member of, and returns null until an opponent has joined.
CREATE OR REPLACE FUNCTION public.get_room_opponent(p_room_id UUID) RETURNS jsonb
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public AS $$
  SELECT jsonb_build_object('display_name', p.display_name, 'photo_url', p.photo_url)
  FROM public.room_members me
  JOIN public.room_members other ON other.room_id = me.room_id AND other.player_id <> me.player_id
  JOIN public.profiles p ON p.id = other.player_id::UUID
  WHERE me.room_id = p_room_id AND me.player_id = auth.uid()::TEXT
  LIMIT 1;
$$;

REVOKE EXECUTE ON FUNCTION public.get_room_opponent(UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_room_opponent(UUID) TO authenticated;
