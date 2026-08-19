-- DeadlineKeeper V3: Fix core data model
-- Adds TIMESTAMPTZ due_at, reminder deliveries, notification outbox, calendar connections,
-- external events, AI confidence, confirmation status, and RLS policies.

-- =============================================================================
-- 1. events table: add due_at, ai_confidence, confirmation_status, user_confirmed
-- =============================================================================

ALTER TABLE events ADD COLUMN due_at TIMESTAMPTZ;
ALTER TABLE events ADD COLUMN ai_confidence REAL;
ALTER TABLE events ADD COLUMN confirmation_status TEXT DEFAULT 'system'
    CHECK (confirmation_status IN ('system', 'user_confirmed', 'auto_imported'));
ALTER TABLE events ADD COLUMN user_confirmed BOOLEAN DEFAULT FALSE;

-- Populate due_at from existing due_date + due_time + timezone
UPDATE events SET due_at =
    (due_date + COALESCE(due_time, TIME '23:59')) AT TIME ZONE timezone;

-- Make due_at NOT NULL now that existing rows are populated
ALTER TABLE events ALTER COLUMN due_at SET NOT NULL;

-- Migrate existing data: ai_confidence = old confidence_score
UPDATE events SET ai_confidence = confidence_score;

-- Existing events are user-entered, so mark as user_confirmed
UPDATE events SET confirmation_status = 'user_confirmed', user_confirmed = TRUE
    WHERE confidence_score >= 0.8;

-- Index on due_at for time-range queries
CREATE INDEX idx_events_due_at ON events(due_at);

-- Unique constraint on (user_id, source, source_reference) where source_reference is not null
CREATE UNIQUE INDEX idx_events_user_source_ref_unique
    ON events(user_id, source, source_reference)
    WHERE source_reference IS NOT NULL;

-- =============================================================================
-- 2. reminders table (proper reminder entity replacing reminder_schedule text[])
-- =============================================================================

CREATE TABLE reminders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    offset_seconds BIGINT NOT NULL,
    channel TEXT NOT NULL,
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_reminders_event ON reminders(event_id);
CREATE INDEX idx_reminders_enabled ON reminders(enabled) WHERE enabled = TRUE;

-- =============================================================================
-- 3. reminder_deliveries table
-- =============================================================================

CREATE TABLE reminder_deliveries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reminder_id UUID NOT NULL REFERENCES reminders(id) ON DELETE CASCADE,
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    scheduled_at TIMESTAMPTZ NOT NULL,
    sent_at TIMESTAMPTZ,
    status TEXT DEFAULT 'pending' CHECK (status IN ('pending', 'sent', 'failed', 'cancelled')),
    attempt_count INT DEFAULT 0,
    provider_message_id TEXT,
    last_error TEXT,
    channel TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(event_id, reminder_id, channel)
);

CREATE INDEX idx_reminder_deliveries_event_reminder ON reminder_deliveries(event_id, reminder_id);
CREATE INDEX idx_reminder_deliveries_status_scheduled ON reminder_deliveries(status, scheduled_at);

-- =============================================================================
-- 4. notification_outbox table
-- =============================================================================

CREATE TABLE notification_outbox (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    event_id UUID REFERENCES events(id),
    title TEXT NOT NULL,
    message TEXT NOT NULL,
    channel TEXT NOT NULL,
    status TEXT DEFAULT 'pending' CHECK (status IN ('pending', 'processing', 'sent', 'failed')),
    idempotency_key TEXT NOT NULL UNIQUE,
    attempt_count INT DEFAULT 0,
    max_attempts INT DEFAULT 3,
    last_error TEXT,
    scheduled_at TIMESTAMPTZ DEFAULT NOW(),
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_notification_outbox_status_scheduled ON notification_outbox(status, scheduled_at);

-- =============================================================================
-- 5. calendar_connections table (replaces calendar_sync)
-- =============================================================================

CREATE TABLE calendar_connections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) UNIQUE,
    provider TEXT NOT NULL DEFAULT 'google',
    encrypted_access_token TEXT,
    encrypted_refresh_token TEXT,
    token_iv TEXT,
    sync_token TEXT,
    last_synced_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- =============================================================================
-- 6. external_events table
-- =============================================================================

CREATE TABLE external_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    deadline_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    provider TEXT NOT NULL,
    external_id TEXT NOT NULL,
    external_updated_at TIMESTAMPTZ,
    etag TEXT,
    raw_data JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(provider, external_id)
);

CREATE INDEX idx_external_events_deadline ON external_events(deadline_id);
CREATE INDEX idx_external_events_provider_external ON external_events(provider, external_id);

-- =============================================================================
-- 7. RLS policies for new tables
-- =============================================================================

ALTER TABLE reminders ENABLE ROW LEVEL SECURITY;
ALTER TABLE reminder_deliveries ENABLE ROW LEVEL SECURITY;
ALTER TABLE notification_outbox ENABLE ROW LEVEL SECURITY;
ALTER TABLE calendar_connections ENABLE ROW LEVEL SECURITY;
ALTER TABLE external_events ENABLE ROW LEVEL SECURITY;

-- reminders: access via owning event's user_id
CREATE POLICY "Users can view own reminders" ON reminders
    FOR SELECT USING (
        EXISTS (SELECT 1 FROM events e WHERE e.id = reminders.event_id AND e.user_id = auth.uid())
    );
CREATE POLICY "Users can insert own reminders" ON reminders
    FOR INSERT WITH CHECK (
        EXISTS (SELECT 1 FROM events e WHERE e.id = reminders.event_id AND e.user_id = auth.uid())
    );
CREATE POLICY "Users can update own reminders" ON reminders
    FOR UPDATE USING (
        EXISTS (SELECT 1 FROM events e WHERE e.id = reminders.event_id AND e.user_id = auth.uid())
    );
CREATE POLICY "Users can delete own reminders" ON reminders
    FOR DELETE USING (
        EXISTS (SELECT 1 FROM events e WHERE e.id = reminders.event_id AND e.user_id = auth.uid())
    );

-- reminder_deliveries: access via owning event's user_id
CREATE POLICY "Users can view own reminder deliveries" ON reminder_deliveries
    FOR SELECT USING (
        EXISTS (SELECT 1 FROM events e WHERE e.id = reminder_deliveries.event_id AND e.user_id = auth.uid())
    );
CREATE POLICY "Users can insert own reminder deliveries" ON reminder_deliveries
    FOR INSERT WITH CHECK (
        EXISTS (SELECT 1 FROM events e WHERE e.id = reminder_deliveries.event_id AND e.user_id = auth.uid())
    );
CREATE POLICY "Users can update own reminder deliveries" ON reminder_deliveries
    FOR UPDATE USING (
        EXISTS (SELECT 1 FROM events e WHERE e.id = reminder_deliveries.event_id AND e.user_id = auth.uid())
    );
CREATE POLICY "Users can delete own reminder deliveries" ON reminder_deliveries
    FOR DELETE USING (
        EXISTS (SELECT 1 FROM events e WHERE e.id = reminder_deliveries.event_id AND e.user_id = auth.uid())
    );

-- notification_outbox: direct user_id ownership
CREATE POLICY "Users can view own notification outbox" ON notification_outbox
    FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY "Users can insert own notification outbox" ON notification_outbox
    FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can update own notification outbox" ON notification_outbox
    FOR UPDATE USING (auth.uid() = user_id);
CREATE POLICY "Users can delete own notification outbox" ON notification_outbox
    FOR DELETE USING (auth.uid() = user_id);

-- calendar_connections: direct user_id ownership
CREATE POLICY "Users can view own calendar connections" ON calendar_connections
    FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY "Users can insert own calendar connections" ON calendar_connections
    FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can update own calendar connections" ON calendar_connections
    FOR UPDATE USING (auth.uid() = user_id);
CREATE POLICY "Users can delete own calendar connections" ON calendar_connections
    FOR DELETE USING (auth.uid() = user_id);

-- external_events: access via owning event's user_id
CREATE POLICY "Users can view own external events" ON external_events
    FOR SELECT USING (
        EXISTS (SELECT 1 FROM events e WHERE e.id = external_events.deadline_id AND e.user_id = auth.uid())
    );
CREATE POLICY "Users can insert own external events" ON external_events
    FOR INSERT WITH CHECK (
        EXISTS (SELECT 1 FROM events e WHERE e.id = external_events.deadline_id AND e.user_id = auth.uid())
    );
CREATE POLICY "Users can update own external events" ON external_events
    FOR UPDATE USING (
        EXISTS (SELECT 1 FROM events e WHERE e.id = external_events.deadline_id AND e.user_id = auth.uid())
    );
CREATE POLICY "Users can delete own external events" ON external_events
    FOR DELETE USING (
        EXISTS (SELECT 1 FROM events e WHERE e.id = external_events.deadline_id AND e.user_id = auth.uid())
    );

-- =============================================================================
-- 8. updated_at trigger for calendar_connections
-- =============================================================================

CREATE TRIGGER update_calendar_connections_updated_at
    BEFORE UPDATE ON calendar_connections
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
