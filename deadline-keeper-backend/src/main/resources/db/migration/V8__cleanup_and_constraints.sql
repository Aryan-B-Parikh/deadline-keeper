-- V8: Safe Data Validation, due_at Backfill, Schema Constraints, and Legacy Cleanups

-- Step 1: Normalize timezones to UTC if null or blank
UPDATE events 
SET timezone = 'UTC' 
WHERE timezone IS NULL OR TRIM(timezone) = '';

-- Step 2: Safe backfill of due_at from legacy columns if due_at is null
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'events' AND column_name = 'due_date'
    ) THEN
        UPDATE events 
        SET due_at = (due_date + COALESCE(due_time, '00:00:00'::time)) AT TIME ZONE COALESCE(timezone, 'UTC') 
        WHERE due_at IS NULL AND due_date IS NOT NULL;
    END IF;
END $$;

-- Step 3: Hard assertion verifying zero NULLs in due_at
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM events WHERE due_at IS NULL) THEN
        RAISE EXCEPTION 'V8 failed: unable to reconstruct due_at for existing events';
    END IF;
END $$;

-- Step 4: Enforce NOT NULL on due_at
ALTER TABLE events ALTER COLUMN due_at SET NOT NULL;

-- Step 5: Drop legacy compatibility columns
ALTER TABLE events DROP COLUMN IF EXISTS reminder_schedule;
ALTER TABLE events DROP COLUMN IF EXISTS confidence_score;
ALTER TABLE events DROP COLUMN IF EXISTS due_date;
ALTER TABLE events DROP COLUMN IF EXISTS due_time;

-- Step 6: Ensure notification_outbox idempotency_key is globally unique
CREATE UNIQUE INDEX IF NOT EXISTS uq_notification_outbox_idempotency_key 
    ON notification_outbox(idempotency_key);

-- Ensure 1:1 delivery to outbox invariant
CREATE UNIQUE INDEX IF NOT EXISTS uq_notification_outbox_delivery_id 
    ON notification_outbox(delivery_id) 
    WHERE delivery_id IS NOT NULL;

-- Ensure foreign keys exist with CASCADE on parent deletion
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_reminders_event') THEN
        ALTER TABLE reminders 
            ADD CONSTRAINT fk_reminders_event 
            FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_deliveries_event') THEN
        ALTER TABLE reminder_deliveries 
            ADD CONSTRAINT fk_deliveries_event 
            FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_deliveries_reminder') THEN
        ALTER TABLE reminder_deliveries 
            ADD CONSTRAINT fk_deliveries_reminder 
            FOREIGN KEY (reminder_id) REFERENCES reminders(id) ON DELETE CASCADE;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_outbox_delivery') THEN
        ALTER TABLE notification_outbox 
            ADD CONSTRAINT fk_outbox_delivery 
            FOREIGN KEY (delivery_id) REFERENCES reminder_deliveries(id) ON DELETE CASCADE;
    END IF;
END $$;

-- Step 7: Indexes
CREATE INDEX IF NOT EXISTS idx_reminders_event_id ON reminders(event_id);
CREATE INDEX IF NOT EXISTS idx_reminder_deliveries_status_scheduled ON reminder_deliveries(status, scheduled_at);
CREATE INDEX IF NOT EXISTS idx_events_user_due_at ON events(user_id, due_at);

-- Step 8: Status Check Constraints
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_events_status') THEN
        ALTER TABLE events 
            ADD CONSTRAINT chk_events_status 
            CHECK (status IN ('upcoming', 'completed', 'cancelled'));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_events_confirmation') THEN
        ALTER TABLE events 
            ADD CONSTRAINT chk_events_confirmation 
            CHECK (confirmation_status IN ('system', 'user_confirmed', 'auto_imported', 'rejected'));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_notification_outbox_status') THEN
        ALTER TABLE notification_outbox 
            ADD CONSTRAINT chk_notification_outbox_status 
            CHECK (status IN ('pending', 'processing', 'sent', 'failed'));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_reminder_deliveries_status') THEN
        ALTER TABLE reminder_deliveries 
            ADD CONSTRAINT chk_reminder_deliveries_status 
            CHECK (status IN ('pending', 'processing', 'sent', 'failed', 'cancelled'));
    END IF;
END $$;
