ALTER TABLE sessions ADD COLUMN user_id TEXT;
ALTER TABLE sessions ADD COLUMN user_type TEXT;
UPDATE sessions SET user_id = courier_id;
UPDATE sessions SET user_type = 'courier-id';
ALTER TABLE sessions ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE sessions ALTER COLUMN user_type SET NOT NULL;
ALTER TABLE sessions DROP COLUMN courier_id;