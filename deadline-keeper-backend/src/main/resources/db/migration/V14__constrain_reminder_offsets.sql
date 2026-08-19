-- V14: Keep reminder offsets within the scheduler's seven-day planning window.

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM reminders WHERE offset_seconds < 0 OR offset_seconds > 604800) THEN
        RAISE EXCEPTION 'V14 failed: reminder offset outside supported 0..7 day range';
    END IF;
END $$;

ALTER TABLE reminders
    ADD CONSTRAINT chk_reminders_offset_seconds
    CHECK (offset_seconds BETWEEN 0 AND 604800);
