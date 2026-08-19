-- V7: Add lease columns for at-least-once delivery with crash recovery

ALTER TABLE notification_outbox
    ADD COLUMN processing_started_at TIMESTAMPTZ,
    ADD COLUMN lease_until TIMESTAMPTZ;

-- Index for the watchdog query: find expired processing rows
CREATE INDEX idx_notification_outbox_lease
    ON notification_outbox(status, lease_until)
    WHERE status = 'processing';
