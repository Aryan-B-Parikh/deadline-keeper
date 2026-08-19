-- V11: Keep persisted notification channels aligned with implemented channels.

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM reminders WHERE channel NOT IN ('email', 'in_app')) THEN
        RAISE EXCEPTION 'V11 failed: unsupported reminder channel exists';
    END IF;
    IF EXISTS (SELECT 1 FROM reminder_deliveries WHERE channel NOT IN ('email', 'in_app')) THEN
        RAISE EXCEPTION 'V11 failed: unsupported delivery channel exists';
    END IF;
    IF EXISTS (SELECT 1 FROM notification_outbox WHERE channel NOT IN ('email', 'in_app')) THEN
        RAISE EXCEPTION 'V11 failed: unsupported outbox channel exists';
    END IF;
END $$;

ALTER TABLE reminders DROP CONSTRAINT IF EXISTS chk_reminders_channel;
ALTER TABLE reminder_deliveries DROP CONSTRAINT IF EXISTS chk_reminder_deliveries_channel;
ALTER TABLE notification_outbox DROP CONSTRAINT IF EXISTS chk_notification_outbox_channel;

ALTER TABLE reminders
    ADD CONSTRAINT chk_reminders_channel
    CHECK (channel IN ('email', 'in_app'));

ALTER TABLE reminder_deliveries
    ADD CONSTRAINT chk_reminder_deliveries_channel
    CHECK (channel IN ('email', 'in_app'));

ALTER TABLE notification_outbox
    ADD CONSTRAINT chk_notification_outbox_channel
    CHECK (channel IN ('email', 'in_app'));
