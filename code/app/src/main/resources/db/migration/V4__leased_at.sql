ALTER TABLE courier_handin
  ADD COLUMN leased_at TIMESTAMPTZ;
  
ALTER TABLE courier_handout
  ADD COLUMN leased_at TIMESTAMPTZ;  