-- V13: Align persisted event statuses with DeadlineStatusService.
-- This is intentionally a new migration because V8 may already be applied in production.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM events
        WHERE status NOT IN ('upcoming', 'due_soon', 'overdue', 'done', 'cancelled')
    ) THEN
        RAISE EXCEPTION 'V13 failed: unsupported event status exists';
    END IF;
END $$;

ALTER TABLE events DROP CONSTRAINT IF EXISTS chk_events_status;

ALTER TABLE events
    ADD CONSTRAINT chk_events_status
    CHECK (status IN ('upcoming', 'due_soon', 'overdue', 'done', 'cancelled'));
