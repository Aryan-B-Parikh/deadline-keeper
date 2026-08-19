# DeadlineKeeper — Current Database Schema

PostgreSQL is the source of truth for application data. Schema changes are managed by Flyway under `deadline-keeper-backend/src/main/resources/db/migration/`. Hibernate runs with `ddl-auto=validate` and must match the migrated schema.

## Core relationships

```text
users
 ├── events
 │    └── reminders
 │         └── reminder_deliveries
 │              └── notification_outbox
 ├── notifications
 ├── calendar_connections
 └── external_events (through events)
```

## `users`

Application profile linked to the authenticated Supabase user.

Important fields include `id`, `email`, `display_name`, `timezone`, `plan`, notification preferences, and timestamps.

## `events`

The canonical deadline entity.

| Field | Meaning |
|---|---|
| `id` | UUID primary key |
| `user_id` | Owning application user |
| `title` | Deadline title |
| `type` | `exam`, `submission`, `hackathon`, or `other` |
| `due_at` | Required PostgreSQL `TIMESTAMPTZ`; canonical instant |
| `timezone` | IANA timezone used for user-facing interpretation |
| `source` | Origin such as manual, extraction, email, or calendar sync |
| `source_reference` | Optional source identifier/reference |
| `source_file_url` | Optional uploaded source URL |
| `ai_confidence` | Nullable extraction confidence |
| `confirmation_status` | `system`, `user_confirmed`, `auto_imported`, or `rejected` |
| `user_confirmed` | Whether the user confirmed the event |
| `status` | `upcoming`, `due_soon`, `overdue`, `done`, or `cancelled` |
| `notes` | Optional user/source notes |

The old `due_date`, `due_time`, `confidence_score`, and `reminder_schedule` columns were removed by the canonical migration. Do not reintroduce them as compatibility fields.

## `reminders`

First-class reminder configuration replacing the old event-level reminder array.

| Field | Meaning |
|---|---|
| `id` | UUID primary key |
| `event_id` | Owning event; cascades on event deletion |
| `offset_seconds` | Seconds before `due_at` |
| `channel` | `email`, `in_app`, or `sms` |
| `enabled` | Whether this reminder is active |

## `reminder_deliveries`

Represents a concrete scheduled delivery generated from a reminder.

Important fields include `reminder_id`, `event_id`, `scheduled_at`, `sent_at`, `status`, `attempt_count`, `provider_message_id`, `last_error`, and `channel`.

Delivery states include `pending`, `processing`, `sent`, `failed`, and `cancelled`.

## `notification_outbox`

Durable worker queue for notification delivery.

Important fields include:

- `id`
- `user_id`
- `event_id`
- `delivery_id`
- `channel`
- `status`
- `idempotency_key`
- `attempt_count`
- `max_attempts`
- `last_error`
- `scheduled_at`
- `processing_started_at`
- `lease_until`
- `next_retry_at`

Outbox states are `pending`, `processing`, `sent`, and `failed`.

Workers atomically claim pending jobs using PostgreSQL `FOR UPDATE SKIP LOCKED` and `UPDATE ... RETURNING id`. A lease prevents stale workers from completing transitions after ownership has expired.

## `notifications`

In-app notification records. Access is scoped to the authenticated user through application-level authorization.

## `calendar_connections`

One Google Calendar connection per application user.

Sensitive access and refresh tokens are encrypted before persistence. OAuth state is short-lived and consumed atomically so it cannot be replayed.

## `external_events`

Maps external provider events to local deadlines.

The provider/external identifier is unique, and synchronization verifies that an existing external record belongs to the current user before updating the linked deadline.

## Indexes and constraints

The schema includes indexes for common user/deadline and scheduler queries, foreign keys with appropriate cascading behavior, and status/channel checks. The V9 migration aligns the `events.status` constraint with the actual application state machine.

## Security model

The backend derives ownership from the authenticated Supabase JWT. Controllers and services scope event, notification, and calendar operations by the current user ID. Supabase RLS exists for database-side protection where applicable, but the Spring Boot service also performs explicit user-scoped queries.

## Migration rule

Once a versioned Flyway migration has been applied to a shared environment, do not rewrite it. Add a new migration for corrections or compatibility changes. This keeps fresh installs and upgrades deterministic.
