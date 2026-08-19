-- Add per-user forwarding token for inbox
ALTER TABLE users ADD COLUMN forwarding_token TEXT UNIQUE DEFAULT encode(gen_random_bytes(16), 'hex');

-- Backfill existing users
UPDATE users SET forwarding_token = encode(gen_random_bytes(16), 'hex') WHERE forwarding_token IS NULL;

-- Make it not null after backfill
ALTER TABLE users ALTER COLUMN forwarding_token SET NOT NULL;
