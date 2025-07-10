ALTER TABLE clickcollect_handin RENAME TO clickcollect;

ALTER TABLE clickcollect
  ADD COLUMN state_type TEXT NOT NULL DEFAULT 'handin_state';
