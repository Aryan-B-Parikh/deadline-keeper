-- V10: Scope external calendar events to their owning user.
-- Google event IDs are only unique within a user's calendar context, not globally across users.

ALTER TABLE external_events ADD COLUMN user_id UUID;

UPDATE external_events ee
SET user_id = e.user_id
FROM events e
WHERE e.id = ee.deadline_id
  AND ee.user_id IS NULL;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM external_events WHERE user_id IS NULL
    ) THEN
        RAISE EXCEPTION 'V10 failed: unable to backfill external_events.user_id';
    END IF;
END $$;

ALTER TABLE external_events ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE external_events
    ADD CONSTRAINT fk_external_events_user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE external_events DROP CONSTRAINT IF EXISTS external_events_provider_external_id_key;
DROP INDEX IF EXISTS idx_external_events_provider_external;

CREATE UNIQUE INDEX uq_external_events_user_provider_external
    ON external_events(user_id, provider, external_id);
CREATE INDEX idx_external_events_user_provider_external
    ON external_events(user_id, provider, external_id);
