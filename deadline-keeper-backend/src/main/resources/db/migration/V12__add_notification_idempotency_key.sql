-- V12: Give in-app notifications the same deterministic identity as their outbox job.

ALTER TABLE notifications ADD COLUMN idempotency_key TEXT;

UPDATE notifications
SET idempotency_key = 'legacy_' || id::text
WHERE idempotency_key IS NULL;

ALTER TABLE notifications ALTER COLUMN idempotency_key SET NOT NULL;
CREATE UNIQUE INDEX uq_notifications_idempotency_key
    ON notifications(idempotency_key);
