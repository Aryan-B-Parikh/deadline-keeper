-- DeadlineKeeper V4: Add OAuth state columns for persistent state management
-- and webhooks for real-time calendar sync

-- Add OAuth state columns to calendar_connections
ALTER TABLE calendar_connections ADD COLUMN oauth_state TEXT;
ALTER TABLE calendar_connections ADD COLUMN oauth_state_expires_at TIMESTAMPTZ;
CREATE INDEX idx_calendar_connections_oauth_state ON calendar_connections(oauth_state) WHERE oauth_state IS NOT NULL;
