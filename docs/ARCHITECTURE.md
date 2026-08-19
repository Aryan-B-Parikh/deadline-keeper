# DeadlineKeeper — Current Architecture

DeadlineKeeper is a multi-user deadline and reminder platform. Users create deadlines manually or extract them from text/images/email, and can optionally import deadline-relevant Google Calendar events. Notifications are delivered through a database-backed outbox with leased worker ownership and crash recovery.

## Stack

- **Frontend:** Next.js / TypeScript
- **Backend:** Spring Boot 3 / Java 17
- **Database:** PostgreSQL (Supabase in production)
- **Schema:** Flyway migrations; Hibernate `ddl-auto=validate`
- **Auth:** Supabase JWT
- **Extraction:** Google Gemini
- **Email:** SendGrid
- **Calendar:** Google Calendar OAuth/API

## Core flow

```text
Frontend
   │ JWT
   ▼
Spring Boot API
   ├── EventService ───────────────┐
   ├── ExtractionService           │
   ├── ReminderService             │
   └── CalendarSyncService         │
                                   ▼
                               PostgreSQL
                                   │
                         ReminderDelivery
                                   │
                           NotificationOutbox
                                   │
                        SKIP LOCKED + lease
                                   ▼
                         Notification Worker
                              │         │
                         SendGrid    In-App
```

## Canonical Event model

The public and persistence model uses:

- `dueAt` — canonical UTC `Instant`
- `timezone` — IANA timezone used when interpreting user input
- `aiConfidence` — nullable confidence for extracted events
- `status` — `upcoming`, `due_soon`, `overdue`, `done`, or `cancelled`

The old `dueDate`, `dueTime`, `confidenceScore`, and `reminderSchedule` event fields are not part of the current API/domain model. Reminders are first-class rows in `reminders`.

## Notification reliability

Reminder delivery uses two database entities:

1. `reminder_deliveries` — business-level delivery state.
2. `notification_outbox` — durable worker queue.

Workers claim jobs atomically using PostgreSQL `FOR UPDATE SKIP LOCKED` and `UPDATE ... RETURNING id`. A worker processes only the IDs returned by its own claim operation.

Each processing lease has an expiry. The watchdog:

- requeues expired jobs with exponential backoff while attempts remain;
- permanently fails expired jobs that have exhausted attempts;
- synchronizes the associated delivery state.

State transitions from `processing` require the worker's ID and an unexpired lease. This prevents a stale worker from overwriting a job reclaimed by another worker.

### Delivery guarantee

The system provides **at-least-once processing**, not mathematically guaranteed exactly-once external email delivery. A deterministic delivery identity is attached to SendGrid `custom_args` for reconciliation and observability. SendGrid documents `custom_args` as metadata carried into Event Webhook events; it is not a provider-side deduplication guarantee. citeturn0search0turn0search3

The database outbox therefore prioritizes durable processing, ownership correctness, crash recovery, and explicit state transitions.

## Google Calendar sync

OAuth uses a short-lived, single-use state value stored against the authenticated user's calendar connection. `/start` requires authentication; `/callback` consumes the state atomically before exchanging the authorization code.

Calendar sync uses Google's `syncToken` for incremental updates. A `410/GONE` sync-token failure resets the token and permits exactly one full-resync attempt, avoiding unbounded recursion.

Imported external events are keyed by `(provider, external_id)` and are checked against the owning user before updates are applied.

## Security boundaries

- All normal API routes require authentication.
- Calendar start/trigger/disconnect operations derive the user ID from the authenticated JWT, never from request parameters.
- JWT validation checks the signing algorithm, issuer, audience, and expiration.
- Webhook authentication is fail-closed and uses constant-time token comparison.
- Production startup rejects missing encryption/database/provider configuration and unsafe localhost CORS/base URL settings.
- Health readiness never exposes raw database exception messages.

## Migrations and validation

All database changes are versioned through Flyway. Published migrations are not rewritten; corrections are added as newer migrations. Hibernate validates the resulting PostgreSQL schema at startup.

CI runs backend Maven verification and frontend install/lint/build. PostgreSQL concurrency and crash-recovery tests use Testcontainers rather than H2.
