# DeadlineKeeper — REST API Reference

Development base URL: `http://localhost:8080`. Production uses configured `APP_BASE_URL`.

All authenticated endpoints require a Supabase JWT in the `Authorization` header.

## Health

- `GET /api/health` — public basic service health.
- `GET /api/health/liveness` — public process liveness check.
- `GET /api/health/readiness` — public database readiness check; raw database errors are not exposed.

## Events

### `GET /api/events`
List the authenticated user's events. Optional `status`: `upcoming`, `due_soon`, `overdue`, or `done`.

### `GET /api/events/{id}`
Get one event owned by the authenticated user.

### `POST /api/events`
Create an event using the canonical representation:

```json
{
  "title": "CS101 Final Exam",
  "type": "exam",
  "dueAt": "2026-12-15T14:00:00Z",
  "timezone": "America/New_York",
  "reminders": [
    { "offsetSeconds": 604800, "channel": "email" },
    { "offsetSeconds": 86400, "channel": "email" }
  ],
  "notes": "Room 302"
}
```

`dueAt` is required and is the canonical instant. `timezone` is optional and defaults to `UTC`. Reminders are first-class reminder definitions.

### `PUT /api/events/{id}`
Replace editable event fields using the same request shape as POST.

### `DELETE /api/events/{id}`
Delete an owned event and dependent reminder data according to database foreign-key rules.

### `POST /api/events/{id}/done`
Mark an event as done.

### `POST /api/events/{id}/snooze`
Move `dueAt` forward. Example: `{ "duration": "1d" }`. Supported suffixes: `d`, `h`, `m`.

## Extraction

- `POST /api/events/extract` — multipart screenshot or pasted text extraction.
- `POST /api/events/extract/confirm` — persist user-confirmed extracted events.

Extraction results use `dueAt`, `timezone`, and `aiConfidence`.

## Notifications

- `GET /api/notifications?unreadOnly=false`
- `POST /api/notifications/{id}/read`
- `GET /api/notifications/unread-count`

All notification queries are scoped to the authenticated user.

## User Profile

- `GET /api/user/profile`
- `PUT /api/user/profile`

Profile updates include display name, IANA timezone, and notification preferences.

## Google Calendar

- `GET /api/calendar/sync/start` — authenticated OAuth initiation; returns a redirect.
- `GET /api/calendar/sync/callback` — consumes the single-use OAuth state and exchanges the authorization code.
- `POST /api/calendar/sync/trigger` — sync the authenticated user's calendar.
- `DELETE /api/calendar/sync` — disconnect the authenticated user's calendar.

## Inbox Webhook

`POST /api/inbox/webhook/{token}` accepts the SendGrid Inbound Parse payload. The configured token is required and is compared using a constant-time comparison. The endpoint fails closed when the token is not configured.

## Notification delivery semantics

Notification processing is **at least once**. PostgreSQL atomically assigns outbox jobs with `FOR UPDATE SKIP LOCKED`, worker leases protect ownership, and a watchdog recovers crashed workers. A deterministic `idempotency_key` is attached to SendGrid custom arguments for reconciliation and webhook correlation; it is not a provider-side exactly-once guarantee.

## Error envelope

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "..."
  },
  "requestId": "request-id"
}
```

Common statuses: `400` invalid request/state, `401` unauthenticated/invalid webhook token, `403` unauthorized resource, `404` not found, `409` constraint conflict, `422` validation failure, `502` external provider failure, `500` unexpected server failure.
