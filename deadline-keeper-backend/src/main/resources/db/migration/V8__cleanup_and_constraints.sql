-- V8: Schema Constraints, Unique Indexes, and Consistency Cleanups

-- Ensure notification_outbox idempotency_key is globally unique
CREATE UNIQUE INDEX IF NOT EXISTS uq_notification_outbox_idempotency_key 
    ON notification_outbox(idempotency_key);

-- Ensure 1:1 delivery to outbox invariant
CREATE UNIQUE INDEX IF NOT EXISTS uq_notification_outbox_delivery_id 
    ON notification_outbox(delivery_id) 
    WHERE delivery_id IS NOT NULL;

-- Ensure reminders foreign key and indexes
CREATE INDEX IF NOT EXISTS idx_reminders_event_id ON reminders(event_id);
CREATE INDEX IF NOT EXISTS idx_reminder_deliveries_status_scheduled ON reminder_deliveries(status, scheduled_at);
CREATE INDEX IF NOT EXISTS idx_events_user_due_at ON events(user_id, due_at);

-- Check constraints for valid statuses
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_notification_outbox_status'
    ) THEN
        ALTER TABLE notification_outbox 
            ADD CONSTRAINT chk_notification_outbox_status 
            CHECK (status IN ('pending', 'processing', 'sent', 'failed'));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_reminder_deliveries_status'
    ) THEN
        ALTER TABLE reminder_deliveries 
            ADD CONSTRAINT chk_reminder_deliveries_status 
            CHECK (status IN ('pending', 'processing', 'sent', 'failed'));
    END IF;
END $$;
