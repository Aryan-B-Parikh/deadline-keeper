# DeadlineKeeper — Architecture

## System Overview

DeadlineKeeper is a multi-user deadline-tracking and reminder system. Users capture
deadlines from screenshots, pasted text, manual entry, email forwarding, or Google
Calendar sync. A vision-capable LLM (Google Gemini) extracts structured event data
from unstructured inputs. A background scheduler fires timely reminders via email
(SendGrid) and in-app notifications.

## High-Level Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                      Next.js Frontend                        │
│  (Auth pages · Dashboard · Calendar view · Settings · PWA)  │
└──────────────┬──────────────────────────────┬───────────────┘
               │ REST API (JWT)               │ Auth (Supabase JS SDK)
               ▼                              ▼
┌──────────────────────────┐   ┌──────────────────────────────┐
│   Spring Boot Backend    │   │        Supabase              │
│                          │   │  ┌──────────┐ ┌───────────┐  │
│  Controllers             │   │  │ Postgres │ │   Auth    │  │
│    ↓                     │   │  │   DB     │ │ (email +  │  │
│  Services                │──▶│  │          │ │  Google)  │  │
│    ↓                     │   │  └──────────┘ └───────────┘  │
│  Repositories (JPA)      │   └──────────────────────────────┘
│    ↓                     │
│  Flyway Migrations       │   ┌──────────────────────────────┐
│                          │   │     External Services        │
│  Scheduler (hourly cron) │   │  ┌─────────┐ ┌───────────┐  │
│    ↓                     │   │  │ Gemini  │ │ SendGrid  │  │
│  Notification channels   │──▶│  │ (vision)│ │  (email)  │  │
│    - Email (SendGrid)    │   │  └─────────┘ └───────────┘  │
│    - In-app (DB write)   │   │  ┌─────────┐ ┌───────────┐  │
│                          │   │  │ Google  │ │ SendGrid  │  │
│                          │──▶│  │Calendar │ │ Inbound   │  │
│                          │   │  │   API   │ │  Parse    │  │
│                          │   │  └─────────┘ └───────────┘  │
└──────────────────────────┘   └──────────────────────────────┘
```

## Components

### Frontend (Next.js 14+, App Router, TypeScript)

- **Auth pages** — Login, Register, Forgot Password (Supabase Auth with email/password and Google OAuth).
- **Dashboard** — Overview with 4 sections: Upcoming, Due Soon, Overdue, Done. Quick actions per event (mark done, snooze, edit, delete).
- **Add Event** — Three input tabs: Manual form, Screenshot upload (drag-and-drop), Paste text. Screenshot and text go through the extraction pipeline.
- **Extraction Preview** — Shows extracted fields, highlights low-confidence values, editable before confirm.
- **Calendar View** — Month/week view of events, Google Calendar sync toggle.
- **Inbox Setup** — Shows the dedicated forwarding email address, lists recently parsed emails.
- **Settings** — Timezone, notification preferences, connected accounts.
- **Notification Bell** — Unread count badge with dropdown list.

### Backend (Spring Boot 3, Java 17+)

- **Controllers** — REST API endpoints for events, notifications, user profile, calendar sync, and inbox webhook.
- **Services** — Business logic: `EventService`, `ExtractionService`, `ReminderService`, `NotificationService`, `UserService`, `CalendarSyncService`, `InboxParseService`.
- **Scheduler** — Hourly cron job that checks all upcoming events, fires due reminders, and updates event statuses.
- **Notification Channels** — Pluggable `NotificationChannel` interface with `EmailNotificationChannel` (SendGrid) and `InAppNotificationChannel` (writes to DB).
- **Gemini Client** — Sends screenshots/text to Google Gemini vision API with a structured extraction prompt; returns JSON with event fields and confidence scores.
- **Security** — Spring Security filter validates Supabase JWTs from the `Authorization: Bearer` header.

### Database (Supabase Postgres)

- 5 tables: `users`, `events`, `reminder_logs`, `notifications`, `calendar_sync`.
- Row-level security enforced via `user_id` on every data table.
- All schema changes managed via Flyway migrations.
- See `DATABASE.md` for full schema reference.

## Data Flow

### Capture → Extract → Save

1. User submits input (screenshot, text, or manual form) via frontend.
2. Frontend sends to `POST /api/events/extract` (for screenshot/text) or `POST /api/events` (for manual).
3. Backend calls Gemini extraction pipeline (for screenshot/text).
4. If confidence ≥ 0.7 and no ambiguity: auto-save event, return created event.
5. If confidence < 0.7 or ambiguity detected: return preview to frontend for user review.
6. User edits/confirms in the frontend → `POST /api/events/extract/confirm` → event saved.

### Reminder Firing

1. `ReminderScheduler` runs every hour via `@Scheduled`.
2. Queries all events with status `upcoming` or `due_soon` across all users.
3. For each event, checks each configured reminder offset (e.g., 7d, 1d, 2h) against current time.
4. Skips offsets already logged in `reminder_logs` (prevents duplicates).
5. Calls `NotificationService.send(user, message, channel)` for each due reminder.
6. `NotificationService` delegates to registered `NotificationChannel` implementations.
7. Auto-updates event status: `upcoming` → `due_soon` (within 3 days) → `overdue` (past due_date).

### Google Calendar Sync

1. User connects Google account via OAuth 2.0 flow.
2. Backend fetches calendar events, runs them through Gemini extraction for deadline-relevant ones.
3. Creates events with `source = 'calendar_sync'`.
4. Incremental sync using Google's `syncToken` for efficiency.

### Inbox Parsing

1. User forwards deadline emails to `deadlines@deadlinekeeper.com`.
2. SendGrid Inbound Parse POSTs the email payload to `POST /api/inbox/webhook`.
3. Backend extracts email body and image attachments.
4. Runs through the same Gemini extraction pipeline.
5. Creates event with `source = 'email'`.
6. Sends confirmation email back to the sender.

## Key Design Decisions

1. **Frontend never talks to Supabase for data** — only for auth tokens. All data queries go through the Spring Boot REST API.
2. **Gemini extraction always returns a preview** — low-confidence results are never auto-saved silently.
3. **Generic notification interface** — `NotificationChannel` interface allows adding SMS, push, Telegram, etc. without modifying the scheduler.
4. **Flyway for schema migrations** — versioned, repeatable, safe for multi-environment deploys.
5. **`plan` column on users** — exists from day one for future monetization, not enforced yet.
6. **Database-backed scheduler state** — `reminder_logs` table ensures no missed or duplicate reminders across restarts.
