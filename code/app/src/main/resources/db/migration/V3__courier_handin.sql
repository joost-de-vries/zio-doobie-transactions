CREATE TABLE IF NOT EXISTS courier_handin (
  session_id UUID,
  state JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  PRIMARY KEY(session_id),
  CONSTRAINT fk_session
    FOREIGN KEY(session_id)
      REFERENCES sessions(id)
	ON DELETE CASCADE
);
