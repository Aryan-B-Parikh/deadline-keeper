# DeadlineKeeper — Database Schema

All tables live in Supabase Postgres. Schema changes are managed via Flyway migrations
in `deadline-keeper-backend/src/main/resources/db/migration/`.

## Entity Relationship Diagram

```
auth.users (Supabase)
    │
    └──── users (extends auth.users)
              │
              ├── events (1:many)
              │     └── reminder_logs (1:many, cascade delete)
              │
              ├── notifications (1:many)
              │
              └── calendar_sync (1:1)
```

## Tables

### `users`

Extends Supabase `auth.users`. Created automatically via a Postgres trigger
when a new user signs up through Supabase Auth.

| Column | Type | Default | Description |
|---|---|---|---|
| `id` | `UUID` | — | PK, references `auth.users(id)` |
| `email` | `TEXT` | — | User's email (denormalized from auth.users) |
| `display_name` | `TEXT` | `NULL` | Optional display name |
| `timezone` | `TEXT` | `'UTC'` | IANA timezone identifier |
| `plan` | `TEXT` | `'free'` | Subscription tier: `free` or `pro` (not enforced yet) |
| `notification_prefs` | `JSONB` | `'{"channels":["email"],"default_offsets":["7d","1d","2h"]}'` | Notification configuration |
| `created_at` | `TIMESTAMPTZ` | `NOW()` | Account creation timestamp |
| `updated_at` | `TIMESTAMPTZ` | `NOW()` | Last update timestamp |

### `events`

Core table — each row is a single deadline/event.

| Column | Type | Default | Description |
|---|---|---|---|
| `id` | `UUID` | `gen_random_uuid()` | PK |
| `user_id` | `UUID` | — | FK → `users(id)` |
| `title` | `TEXT` | — | Event name / title |
| `type` | `TEXT` | — | One of: `exam`, `submission`, `hackathon`, `other` |
| `due_date` | `DATE` | — | Due date (required) |
| `due_time` | `TIME` | `NULL` | Due time (optional) |
| `timezone` | `TEXT` | `'UTC'` | Timezone for this event |
| `source` | `TEXT` | — | One of: `manual`, `screenshot`, `pasted_text`, `email`, `calendar_sync` |
| `source_reference` | `TEXT` | `NULL` | Original text or description for traceability |
| `source_file_url` | `TEXT` | `NULL` | Supabase Storage URL for uploaded screenshots |
| `confidence_score` | `REAL` | `1.0` | LLM extraction confidence (0.0–1.0); always 1.0 for manual |
| `status` | `TEXT` | `'upcoming'` | One of: `upcoming`, `due_soon`, `overdue`, `done` |
| `reminder_schedule` | `TEXT[]` | `'{"7d","1d","2h"}'` | Array of reminder offsets before due date |
| `notes` | `TEXT` | `NULL` | User-added notes |
| `created_at` | `TIMESTAMPTZ` | `NOW()` | Creation timestamp |
| `updated_at` | `TIMESTAMPTZ` | `NOW()` | Last update timestamp |

**Indexes:**
- `idx_events_user_status` on `(user_id, status)` — fast filtered queries per user
- `idx_events_due_date` on `(due_date)` — scheduler scans by date efficiently

**Status transitions:**
```
upcoming → due_soon   (when within 3 days of due_date, set by scheduler)
due_soon → overdue    (when past due_date, set by scheduler)
any      → done       (user marks complete)
```

### `reminder_logs`

Prevents duplicate reminder fires. One row per (event, offset) pair.

| Column | Type | Default | Description |
|---|---|---|---|
| `id` | `UUID` | `gen_random_uuid()` | PK |
| `event_id` | `UUID` | — | FK → `events(id)`, cascade delete |
| `offset_fired` | `TEXT` | — | Which offset was fired (e.g., `'7d'`, `'1d'`, `'2h'`) |
| `fired_at` | `TIMESTAMPTZ` | `NOW()` | When the reminder was sent |

**Constraint:** `UNIQUE(event_id, offset_fired)` — same offset never fires twice for the same event.

### `notifications`

In-app notification log. Also used as a general audit trail for all notification sends.

| Column | Type | Default | Description |
|---|---|---|---|
| `id` | `UUID` | `gen_random_uuid()` | PK |
| `user_id` | `UUID` | — | FK → `users(id)` |
| `event_id` | `UUID` | `NULL` | FK → `events(id)`, nullable for system notifications |
| `title` | `TEXT` | — | Notification title |
| `message` | `TEXT` | — | Notification body |
| `is_read` | `BOOLEAN` | `FALSE` | Whether the user has read it |
| `channel` | `TEXT` | `'in_app'` | Delivery channel: `in_app`, `email` |
| `created_at` | `TIMESTAMPTZ` | `NOW()` | Creation timestamp |

**Index:** `idx_notifications_user_read` on `(user_id, is_read)` — fast unread count queries.

### `calendar_sync`

Stores Google Calendar OAuth tokens and sync state per user.

| Column | Type | Default | Description |
|---|---|---|---|
| `id` | `UUID` | `gen_random_uuid()` | PK |
| `user_id` | `UUID` | — | FK → `users(id)`, UNIQUE (one sync per user) |
| `google_access_token` | `TEXT` | `NULL` | Current access token (short-lived) |
| `google_refresh_token` | `TEXT` | `NULL` | Refresh token (long-lived) |
| `sync_token` | `TEXT` | `NULL` | Google Calendar API syncToken for incremental sync |
| `last_synced_at` | `TIMESTAMPTZ` | `NULL` | When the last sync completed |
| `created_at` | `TIMESTAMPTZ` | `NOW()` | Connection creation timestamp |

## Supabase Triggers

A trigger function creates a row in `users` whenever a new user signs up via Supabase Auth:

```sql
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER AS $$
BEGIN
  INSERT INTO public.users (id, email)
  VALUES (NEW.id, NEW.email);
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE TRIGGER on_auth_user_created
  AFTER INSERT ON auth.users
  FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();
```

## Row-Level Security (RLS)

All tables have RLS enabled. Policies ensure users can only access their own data:

```sql
ALTER TABLE events ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users can view own events" ON events
  FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY "Users can insert own events" ON events
  FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can update own events" ON events
  FOR UPDATE USING (auth.uid() = user_id);
CREATE POLICY "Users can delete own events" ON events
  FOR DELETE USING (auth.uid() = user_id);
```

Similar policies apply to `notifications`, `reminder_logs`, and `calendar_sync`.

> **Note:** The Spring Boot backend connects via the Supabase service role key,
> which bypasses RLS. All data isolation is enforced in the application layer
> by always scoping queries to the authenticated `user_id` extracted from the JWT.

## Reminder Offset Format

Offsets are stored as strings in the `reminder_schedule` array:

| Format | Meaning |
|---|---|
| `7d` | 7 days before |
| `3d` | 3 days before |
| `1d` | 1 day before |
| `12h` | 12 hours before |
| `2h` | 2 hours before |
| `30m` | 30 minutes before |

The `ReminderService` parses these into `Duration` objects for comparison.
