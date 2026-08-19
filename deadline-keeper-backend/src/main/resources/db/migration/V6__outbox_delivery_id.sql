-- V6: Add delivery_id and next_retry_at to notification_outbox

ALTER TABLE notification_outbox ADD COLUMN delivery_id UUID REFERENCES reminder_deliveries(id);
ALTER TABLE notification_outbox ADD COLUMN next_retry_at TIMESTAMPTZ;

CREATE INDEX idx_notification_outbox_delivery ON notification_outbox(delivery_id) WHERE delivery_id IS NOT NULL;

-- Drop the old idempotency_key unique index (key is now per-delivery, not global)
-- and create a composite index for the atomic claim query
DROP INDEX IF EXISTS idx_notification_outbox_status_scheduled;
CREATE INDEX idx_notification_outbox_status_scheduled ON notification_outbox(status, scheduled_at, next_retry_at);
