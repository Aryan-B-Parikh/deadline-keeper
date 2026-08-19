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

Important fields include `id`, `email`, `display_name`, `timezone`, `plan`, notification preferences, forwarding token, and timestamps.

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
| `offset_seconds` | Seconds before `due_at`, limited to the scheduler's seven-day planning window |
| `channel` | `email` or `in_app` |
| `enabled` | Whether this reminder is active |

## `reminder_deliveries`

Represents a concrete scheduled delivery generated from a reminder.

Important fields include `reminder_id`, `event_id`, `scheduled_at`, `sent_at`, `status`, `last_error`, and `channel`.

Delivery states include `pending`, `processing`, `sent`, `failed`, and `cancelled`.

Retry ownership and attempt counts live in the notification outbox, avoiding duplicate state between the delivery and worker queue.

## `notification_outbox`

Durable worker queue for notification delivery.

Important fields include:

- `id`
- `user_id`
- `event_id`
- `delivery_id` (nullable for direct notifications)
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

Workers atomically claim only due pending jobs using PostgreSQL `FOR UPDATE SKIP LOCKED` and `UPDATE ... RETURNING id`. A lease prevents stale workers from completing transitions after ownership has expired.

## `notifications`

In-app notification records. Each notification has a deterministic `idempotency_key` matching its outbox identity, preventing duplicate in-app records during worker retries.

## `calendar_connections`

One Google Calendar connection per application user.

Sensitive access and refresh tokens are encrypted before persistence. OAuth state is short-lived and consumed atomically so it cannot be replayed.

## `external_events`

Maps external provider events to local deadlines.

External identity is scoped by `(user_id, provider, external_id)` because provider event IDs are not globally unique across users. Synchronization verifies ownership before updating or cancelling a linked deadline.

## Indexes and constraints

The schema includes indexes for common user/deadline and scheduler queries, foreign keys with appropriate cascading behavior, and status/channel checks. Forward migrations align event statuses, supported notification channels, external-event ownership, notification idempotency, and reminder offset limits.

## Security model

The backend derives ownership from the authenticated Supabase JWT. Controllers and services scope event, notification, and calendar operations by the current user ID. The inbound email webhook uses each user's high-entropy forwarding token from the recipient address and does not fall back to sender-email identity.

## Migration rule

Once a versioned Flyway migration has been applied to a shared environment, do not rewrite it. Add a new migration for corrections or compatibility changes. This keeps fresh installs and upgrades deterministic.
