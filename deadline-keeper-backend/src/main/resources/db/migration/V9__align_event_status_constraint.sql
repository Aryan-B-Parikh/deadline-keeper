-- V9: Align the database event status constraint with the application state machine.
-- V8 predates the computed statuses used by DeadlineStatusService.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_events_status'
          AND conrelid = 'events'::regclass
    ) THEN
        ALTER TABLE events DROP CONSTRAINT chk_events_status;
    END IF;
END $$;

ALTER TABLE events
    ADD CONSTRAINT chk_events_status
    CHECK (status IN ('upcoming', 'due_soon', 'overdue', 'done', 'cancelled'));
