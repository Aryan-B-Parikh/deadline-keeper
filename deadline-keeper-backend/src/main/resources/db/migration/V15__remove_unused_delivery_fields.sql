-- V15: Remove delivery fields that duplicated outbox state and were never populated.
ALTER TABLE reminder_deliveries DROP COLUMN IF EXISTS attempt_count;
ALTER TABLE reminder_deliveries DROP COLUMN IF EXISTS provider_message_id;
