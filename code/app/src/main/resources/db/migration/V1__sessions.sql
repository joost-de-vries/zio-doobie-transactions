CREATE TABLE IF NOT EXISTS sessions (
  id UUID PRIMARY KEY,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  ended_at TIMESTAMPTZ,
  courier_id TEXT NOT NULL,
  locker_id TEXT,
  present_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS sessions_created_at_idx ON sessions(created_at);
CREATE INDEX IF NOT EXISTS sessions_ended_at_idx ON sessions(ended_at);
