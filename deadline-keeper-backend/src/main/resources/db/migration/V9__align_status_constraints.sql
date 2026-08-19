-- V9: Align status constraints with the canonical application state machine.
-- V8 accidentally excluded legitimate Event statuses used by the application.
-- Keep V8 immutable and correct the deployed schema with a forward migration.

ALTER TABLE events DROP CONSTRAINT IF EXISTS chk_events_status;
ALTER TABLE events
    ADD CONSTRAINT chk_events_status
    CHECK (status IN ('upcoming', 'due_soon', 'overdue', 'done', 'cancelled'));

ALTER TABLE events DROP CONSTRAINT IF EXISTS chk_events_confirmation;
ALTER TABLE events
    ADD CONSTRAINT chk_events_confirmation
    CHECK (confirmation_status IN ('system', 'user_confirmed', 'auto_imported', 'rejected'));

ALTER TABLE reminder_deliveries DROP CONSTRAINT IF EXISTS chk_reminder_deliveries_status;
ALTER TABLE reminder_deliveries
    ADD CONSTRAINT chk_reminder_deliveries_status
    CHECK (status IN ('pending', 'processing', 'sent', 'failed', 'cancelled'));

ALTER TABLE notification_outbox DROP CONSTRAINT IF EXISTS chk_notification_outbox_status;
ALTER TABLE notification_outbox
    ADD CONSTRAINT chk_notification_outbox_status
    CHECK (status IN ('pending', 'processing', 'sent', 'failed'));

ALTER TABLE reminders DROP CONSTRAINT IF EXISTS chk_reminders_channel;
ALTER TABLE reminders
    ADD CONSTRAINT chk_reminders_channel
    CHECK (channel IN ('email', 'in_app', 'sms'));
