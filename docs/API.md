# DeadlineKeeper — REST API Reference

Base URL: `http://localhost:8080`

All authenticated endpoints require a Supabase JWT in the `Authorization` header:
```
Authorization: Bearer <supabase-access-token>
```

---

## Health Check

### `GET /api/health`

No auth required. Returns service status.

**Response:**
```json
{ "status": "ok" }
```

---

## Events

### `GET /api/events`

List the authenticated user's events.

**Query Parameters:**

| Param | Type | Description |
|---|---|---|
| `status` | string | Filter by status: `upcoming`, `due_soon`, `overdue`, `done` |

**Response:** `200 OK`
```json
[
  {
    "id": "uuid",
    "title": "CS101 Final Exam",
    "type": "exam",
    "dueDate": "2026-12-15",
    "dueTime": "09:00",
    "timezone": "America/New_York",
    "source": "manual",
    "confidenceScore": 1.0,
    "status": "upcoming",
    "reminderSchedule": ["7d", "1d", "2h"],
    "notes": null,
    "sourceFileUrl": null,
    "createdAt": "2026-08-16T10:00:00Z",
    "updatedAt": "2026-08-16T10:00:00Z"
  }
]
```

### `GET /api/events/{id}`

Get a single event.

**Response:** `200 OK` — same shape as a single event object.

**Errors:** `404` if not found, `403` if not the owner.

### `POST /api/events`

Create an event manually.

**Request Body:**
```json
{
  "title": "CS101 Final Exam",
  "type": "exam",
  "dueDate": "2026-12-15",
  "dueTime": "09:00",
  "timezone": "America/New_York",
  "reminderSchedule": ["7d", "1d", "2h"],
  "notes": "Room 302"
}
```

| Field | Required | Description |
|---|---|---|
| `title` | yes | Event title |
| `type` | yes | `exam`, `submission`, `hackathon`, or `other` |
| `dueDate` | yes | ISO date `YYYY-MM-DD` |
| `dueTime` | no | `HH:mm` format |
| `timezone` | no | IANA timezone (default: `UTC`) |
| `reminderSchedule` | no | Array of offsets (default: `["7d","1d","2h"]`) |
| `notes` | no | Free text notes |

**Response:** `201 Created` — created event object.

### `PUT /api/events/{id}`

Update an event. Same request body as `POST /api/events`.

**Response:** `200 OK` — updated event object.

### `DELETE /api/events/{id}`

Delete an event.

**Response:** `204 No Content`

### `POST /api/events/{id}/done`

Mark an event as done.

**Response:** `200 OK` — updated event with `status: "done"`.

### `POST /api/events/{id}/snooze`

Snooze an event by pushing the due date forward.

**Request Body:**
```json
{ "duration": "1d" }
```

Duration format: `7d` (7 days), `12h` (12 hours), `30m` (30 minutes).

**Response:** `200 OK` — updated event with new due date.

---

## Extraction

### `POST /api/events/extract`

Extract deadlines from a screenshot or pasted text. Uses Google Gemini vision API.

**Content-Type:** `multipart/form-data`

| Field | Type | Description |
|---|---|---|
| `screenshot` | file | Image file (PNG, JPEG, etc.) |
| `pastedText` | string | Text containing deadline info |

Provide **either** `screenshot` or `pastedText`, not both.

**Response:** `200 OK`
```json
{
  "events": [
    {
      "title": "Project Submission",
      "type": "submission",
      "dueDate": "2026-12-15",
      "dueTime": "23:59",
      "timezone": null,
      "confidenceScore": 0.85,
      "needsClarification": false
    }
  ],
  "needsConfirmation": false,
  "clarificationQuestion": null
}
```

If `needsConfirmation` is `true`, the frontend should show an `ExtractionPreview` for user review before saving.

### `POST /api/events/extract/confirm`

Save events after user reviews and confirms the extraction preview.

**Request Body:**
```json
{
  "events": [
    {
      "title": "Project Submission",
      "type": "submission",
      "dueDate": "2026-12-15",
      "dueTime": "23:59",
      "timezone": "UTC",
      "reminderSchedule": ["7d", "1d", "2h"],
      "notes": null
    }
  ],
  "sourceType": "screenshot",
  "sourceReference": "screenshot_2026-08-16.png",
  "sourceFileUrl": "https://storage.supabase.co/..."
}
```

| Field | Required | Description |
|---|---|---|
| `events` | yes | Array of confirmed event objects |
| `sourceType` | no | `manual`, `screenshot`, `pasted_text`, `email`, `calendar_sync` |
| `sourceReference` | no | Original source text/filename for traceability |
| `sourceFileUrl` | no | URL of uploaded file |

**Response:** `201 Created` — array of created event objects.

---

## Notifications

### `GET /api/notifications`

List the user's notifications.

**Query Parameters:**

| Param | Type | Default | Description |
|---|---|---|---|
| `unreadOnly` | boolean | `false` | Only return unread notifications |

**Response:** `200 OK`
```json
[
  {
    "id": "uuid",
    "eventId": "uuid",
    "title": "⏰ Deadline Reminder: CS101 Final Exam",
    "message": "Your deadline for \"CS101 Final Exam\" is in 1 day (due: 2026-12-15 at 09:00).",
    "isRead": false,
    "channel": "email",
    "createdAt": "2026-12-14T09:00:00Z"
  }
]
```

### `POST /api/notifications/{id}/read`

Mark a notification as read.

**Response:** `200 OK`

### `GET /api/notifications/unread-count`

Get the count of unread notifications.

**Response:** `200 OK`
```json
{ "count": 3 }
```

---

## User Profile

### `GET /api/user/profile`

Get the current user's profile.

**Response:** `200 OK`
```json
{
  "email": "user@example.com",
  "displayName": "Jane Doe",
  "timezone": "America/New_York",
  "plan": "free",
  "notificationPrefs": {
    "channels": ["email"],
    "default_offsets": ["7d", "1d", "2h"]
  }
}
```

### `PUT /api/user/profile`

Update the user's profile.

**Request Body:**
```json
{
  "displayName": "Jane Doe",
  "timezone": "America/New_York",
  "notificationPrefs": {
    "channels": ["email"],
    "default_offsets": ["7d", "1d", "2h"]
  }
}
```

All fields are optional — only included fields are updated.

**Response:** `200 OK` — updated profile.

---

## Google Calendar Sync

### `GET /api/calendar/sync/start`

Initiate the Google OAuth 2.0 flow. No auth required (public redirect).

**Response:** `302 Redirect` to Google's authorization page.

### `GET /api/calendar/sync/callback`

OAuth callback handler. Called by Google after user authorizes.

**Query Parameters:**

| Param | Description |
|---|---|
| `code` | Authorization code from Google |

**Response:** `200 OK`
```json
{ "status": "connected" }
```

### `POST /api/calendar/sync/trigger`

Manually trigger a calendar sync for the authenticated user.

**Response:** `200 OK`
```json
{ "status": "synced" }
```

### `DELETE /api/calendar/sync`

Disconnect Google Calendar. Synced events remain in the database.

**Response:** `204 No Content`

---

## Inbox Webhook

### `POST /api/inbox/webhook`

SendGrid Inbound Parse webhook endpoint. No auth required (public).

**Content-Type:** `application/x-www-form-urlencoded`

| Param | Description |
|---|---|
| `from` | Sender email address |
| `subject` | Email subject line |
| `text` | Plain text body |
| `html` | HTML body |

**Response:** `200 OK`
```json
{
  "status": "processed",
  "events_created": "2"
}
```

---

## Error Responses

All errors return a JSON body:

```json
{ "error": "Description of what went wrong" }
```

| Status | Meaning |
|---|---|
| `400` | Invalid request body or missing required fields |
| `401` | Missing or invalid JWT |
| `403` | User does not own the requested resource |
| `404` | Resource not found |
| `500` | Internal server error |
