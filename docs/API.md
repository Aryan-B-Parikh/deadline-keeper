# DeadlineKeeper — REST API Reference

Base URL: `http://localhost:8080`

All authenticated endpoints require a Supabase JWT in the `Authorization` header:

```http
Authorization: Bearer <supabase-access-token>
```

## Health Check

### `GET /api/health`

No authentication required. Returns service status.

```json
{ "status": "ok" }
```

## Events

### `GET /api/events`

List the authenticated user's events.

**Query parameters:** `status` — `upcoming`, `due_soon`, `overdue`, or `done`.

### `GET /api/events/{id}`

Get one event owned by the authenticated user.

### `POST /api/events`

Create an event.

```json
{
  "title": "CS101 Final Exam",
  "type": "exam",
  "dueAt": "2026-12-15T14:00:00Z",
  "timezone": "America/New_York",
  "reminders": [
    { "offsetSeconds": 604800, "channel": "email", "enabled": true },
    { "offsetSeconds": 86400, "channel": "email", "enabled": true }
  ],
  "notes": "Room 302"
}
```

| Field | Required | Description |
|---|---|---|
| `title` | yes | Event title |
| `type` | yes | `exam`, `submission`, `hackathon`, or `other` |
| `dueAt` | yes | ISO-8601 timestamp representing the canonical UTC instant |
| `timezone` | no | IANA timezone used for display; defaults to `UTC` |
| `reminders` | no | Reminder definitions for the event |
| `notes` | no | Free-text notes |

### `PUT /api/events/{id}`

Replace the editable event fields. Uses the same request shape as `POST /api/events`.

### `DELETE /api/events/{id}`

Delete an event and its dependent reminders/deliveries according to the database foreign-key rules.

### `POST /api/events/{id}/done`

Mark an event as done.

### `POST /api/events/{id}/snooze`

Move the canonical `dueAt` forward.

```json
{ "duration": "1d" }
```

Supported duration suffixes: `d`, `h`, `m`.

## Extraction

### `POST /api/events/extract`

Extract deadlines from a screenshot or pasted text using the configured AI provider.

**Content-Type:** `multipart/form-data`

| Field | Type | Description |
|---|---|---|
| `screenshot` | file | PNG/JPEG/etc. |
| `pastedText` | string | Text containing deadline information |

Provide either `screenshot` or `pastedText`.

Extraction results use the same canonical event representation, including `dueAt`, `timezone`, and `aiConfidence`.

### `POST /api/events/extract/confirm`

Persist events after the user confirms the extraction preview. Confirmed event objects use the canonical `dueAt`/`timezone` representation and may include `reminders` and `notes`.

## Notifications

### `GET /api/notifications`

List notifications for the authenticated user.

**Query parameter:** `unreadOnly` — boolean, default `false`.

### `POST /api/notifications/{id}/read`

Mark a notification as read.

### `GET /api/notifications/unread-count`

Return the authenticated user's unread notification count.

```json
{ "count": 3 }
```

## User Profile

### `GET /api/user/profile`

Return the authenticated user's profile.

### `PUT /api/user/profile`

Update the authenticated user's profile. Supported profile fields include display name, IANA timezone, and notification preferences.

## Google Calendar Sync

### `GET /api/calendar/sync/start`

Initiate Google OAuth for the authenticated user. Returns a `302` redirect to Google's authorization page. The generated OAuth state is single-use and expires after a short period.

### `GET /api/calendar/sync/callback`

OAuth callback. Requires the `code` and single-use `state` parameters supplied by Google.

```json
{ "status": "connected" }
```

### `POST /api/calendar/sync/trigger`

Trigger a calendar sync for the authenticated user.

### `DELETE /api/calendar/sync`

Disconnect Google Calendar for the authenticated user.

## Inbox Webhook

### `POST /api/inbox/webhook/{token}`

SendGrid Inbound Parse webhook endpoint. The token is validated using a constant-time comparison and must be configured server-side.

**Content-Type:** `application/x-www-form-urlencoded`

| Param | Description |
|---|---|
| `from` | Sender email address |
| `subject` | Email subject |
| `text` | Plain-text body |
| `html` | HTML body |

## Error Responses

Errors use a structured envelope with a request ID for server-side correlation.

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "..."
  },
  "requestId": "..."
}
```

Common statuses:

| Status | Meaning |
|---|---|
| `400` | Invalid request or OAuth state |
| `401` | Missing/invalid authentication or webhook token |
| `403` | Authenticated user is not authorized for the resource |
| `404` | Resource not found |
| `409` | Database uniqueness/constraint conflict |
| `422` | Validation failure |
| `502` | External provider failure |
| `500` | Unexpected server failure |
