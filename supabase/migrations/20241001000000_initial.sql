-- Rooms
CREATE TABLE rooms (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  code TEXT UNIQUE NOT NULL,
  created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Members (exactly 2 per room: player_index 0 and 1)
CREATE TABLE room_members (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  room_id UUID NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
  player_id TEXT NOT NULL,
  player_index SMALLINT NOT NULL CHECK (player_index IN (0, 1)),
  joined_at TIMESTAMPTZ DEFAULT NOW(),
  UNIQUE (room_id, player_id),
  UNIQUE (room_id, player_index)
);

-- Matches (one per room, created when room is full)
CREATE TABLE matches (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  room_id UUID UNIQUE NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
  state JSONB NOT NULL,
  revision INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Enable Realtime for live match updates
ALTER PUBLICATION supabase_realtime ADD TABLE matches;

-- RLS
ALTER TABLE rooms ENABLE ROW LEVEL SECURITY;
ALTER TABLE room_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE matches ENABLE ROW LEVEL SECURITY;

-- Members can read their own rooms
CREATE POLICY "members_read_room" ON rooms FOR SELECT
  USING (
    EXISTS (
      SELECT 1 FROM room_members
      WHERE room_id = rooms.id AND player_id = auth.uid()::TEXT
    )
  );

-- Members can read their own membership rows
CREATE POLICY "read_own_membership" ON room_members FOR SELECT
  USING (player_id = auth.uid()::TEXT);

-- Members can read their match
CREATE POLICY "members_read_match" ON matches FOR SELECT
  USING (
    EXISTS (
      SELECT 1 FROM room_members
      WHERE room_id = matches.room_id AND player_id = auth.uid()::TEXT
    )
  );

-- Clients cannot write game state directly — only edge functions via service role can
-- (no INSERT/UPDATE/DELETE policies for matches)
