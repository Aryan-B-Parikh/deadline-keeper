# Master Prompt: "DeadlineKeeper" — Personal Deadline & Reminder Assistant

> Use this document as a build spec. Paste the relevant sections into an AI coding
> tool (Claude Code, Cursor, etc.) phase by phase, or hand the whole thing to a dev
> team as a PRD.

---

## 1. One-line pitch

A product that remembers every submission deadline, exam date, and hackathon —
captured from a screenshot, an email, a calendar invite, or a quick text message —
and reliably reminds every user before it's too late.

## 2. Core problem it solves

People miss deadlines not because they don't care, but because the information
lives in scattered places (WhatsApp screenshots, emails, posters, portals, group
chats) and never makes it into one trustworthy system with reminders.

## 3. Product framing (updated)

This is being built as a **multi-user product**, not a personal script. That
changes the plan in a few concrete ways vs. a solo tool:

- Needs **user accounts** (sign-up/login) from day one, even if simple
  (email + password, or Google sign-in) — every event, reminder, and setting
  is scoped to a user.
- Data model needs a `user_id` on every table, not just an `Event` table.
- Notification delivery needs to scale per-user (a scheduler checking "all
  events for all users," not just one inbox).
- Worth thinking early about a **monetization/tier model** even if you don't
  build it yet (e.g., free tier with limited events/reminders, paid tier for
  unlimited + calendar sync + team sharing) — doesn't need building in the MVP,
  but the schema should be able to support a `plan` field on the user later
  without a rewrite.
- **Build order stays "core first, app later"** — see Section 9. Multi-user
  and "product-ready" does not mean "native mobile app on day one." It means
  the backend and data model are built correctly from the start so you're not
  migrating single-user data later.

## 4. Capture methods — build all three, ranked by effort vs. payoff

You said you want whatever's best and easiest to use, not necessarily easiest
to build — so here's the honest trade-off per method:

| Method | User effort | Build effort | Notes |
|---|---|---|---|
| **Manual entry** | Low | Very low | Always needed as a fallback; build first, it's trivial and unlocks testing everything else. |
| **Screenshot upload** | Very low | Medium | Needs a vision-capable LLM call; no auth/integration headaches, just an image → structured event. Best effort-to-payoff ratio — do this second. |
| **Forwarded text/email paste** | Low | Low–Medium | Same extraction pipeline as screenshots, just text input instead of image. Cheap to add once screenshot extraction exists — bundle them together. |
| **Calendar sync (Google/Outlook)** | Zero after setup | High | Requires OAuth, webhook/polling infra, and mapping external calendar events into your schema. Very convenient for the user once live, but the most expensive to build correctly and secure. |
| **Direct email inbox parsing** (forward deadline emails to a dedicated address) | Low | High | Needs a real mail-receiving pipeline (e.g., a service like Postmark/SendGrid inbound parse) plus the same extraction logic. |

**Recommendation**: build manual entry + screenshot + pasted text together as
the MVP input layer (they all funnel into the *same* extraction pipeline, so
it's one system, not three). Add calendar sync and inbox-forwarding as the
very next milestone right after the core loop works — not deferred to a
distant "Phase 3," but not blocking the first working version either.

## 5. Core user flows

1. **Capture** — screenshot, pasted text, typed manually, or (once built)
   auto-pulled from a synced calendar/inbox.
2. **Extract** — agent parses input and pulls out: event name, type
   (exam/submission/hackathon/other), date, time, source/link, confidence score.
3. **Confirm** — if extraction confidence is low or the date is ambiguous,
   agent asks a single clarifying question before saving.
4. **Store** — event saved with status (upcoming / due soon / overdue / done).
5. **Remind** — proactive notifications on a configurable schedule
   (e.g., 1 week, 3 days, 1 day, 2 hours before).
6. **Review** — user asks "what's due this week?", "show me all hackathons",
   "mark X as done", "push Y by 2 days," and gets a live dashboard/summary.

## 6. Data model (multi-user)

```
User
- id
- email
- auth_provider        (password | google | etc.)
- timezone
- plan                  (free | pro) -- for future monetization, not enforced yet
- notification_prefs    (channels, default reminder offsets)
- created_at

Event
- id
- user_id               (FK -> User)
- title
- type                  (exam | submission | hackathon | other)
- due_date
- due_time              (nullable)
- timezone
- source                (screenshot | manual | pasted_text | email | calendar_sync)
- source_reference       (original screenshot/text, for traceability)
- confidence_score
- status                (upcoming | due_soon | overdue | done)
- reminder_schedule      (list of offsets, e.g. [7d, 1d, 2h])
- notes
- created_at / updated_at

ReminderLog
- id
- event_id
- offset_fired           (which reminder offset already fired, to avoid duplicates)
- fired_at
```

## 7. Extraction step — what it needs to handle

- **Vision-capable LLM**, not classic OCR alone — needs to read messy layouts
  (posters, app screenshots, forwarded chat bubbles) and reason about which
  text is the actual deadline vs. surrounding noise.
- **Relative/ambiguous dates**: "next Monday," "in 2 weeks" → resolve using
  current date; ask for confirmation if ambiguous.
- **Missing year**: assume nearest future occurrence unless context says otherwise.
- **Time zones**: capture stated time zone if visible; otherwise use the
  user's account timezone and flag the assumption.
- **Multiple dates in one capture** (e.g. "registration closes X, event starts Y"):
  extract as separate linked events.
- **Confidence scoring**: always returned; low confidence → human confirmation,
  never a silent save.

## 8. Reminder engine logic

- Background scheduler checks all events across all users on a regular
  interval (e.g., hourly).
- For each event, compare `now` to `due_date - each reminder offset`.
- Fire once per offset per event (tracked in `ReminderLog` to prevent duplicates).
- Escalate tone as deadline nears.
- Support snooze and per-event mute.
- Notification channel is pluggable — start with one (see Section 9), design
  the scheduler to call a generic `send_notification(user, message, channel)`
  so adding channels later doesn't require touching the scheduler logic.

## 9. Build order — working core first, product polish after, native app last

This directly follows your instinct: get it *working* before making it
pretty or native. Suggested sequence:

**Step 1 — Core working loop (no app, no polish)**
- Backend + database with the multi-user schema above (even if you only
  create one test user manually at first).
- Manual entry + screenshot + pasted-text extraction pipeline, via a bare
  API or a single-page web form — ugly is fine.
- Reminder scheduler running and firing to ONE channel (email is fastest to
  wire up reliably; a Telegram bot is a close second and feels nicer to use).
- Goal: you personally use it daily and it doesn't miss a reminder.

**Step 2 — Make it a real product**
- Proper sign-up/login so more than one person can use it.
- Clean dashboard UI (web) — upcoming/due-soon/overdue/done views, edit/delete/snooze.
- Add calendar sync + inbox-forwarding as additional capture methods.
- Add a second notification channel.

**Step 3 — Package as an app**
- Once the backend/product is solid, wrap it as a native or PWA mobile app
  for push notifications and on-the-go capture (e.g., share a screenshot
  directly from your phone's gallery into the app).
- This is intentionally last — an app is a distribution/UX layer on top of
  a working system, not the system itself.

## 10. Suggested tech stack

- **Frontend**: React web dashboard (PWA-ready so it can later behave like an
  app without a separate native build)
- **Backend**: Node/Express or Python/FastAPI, with proper auth (e.g. Auth.js
  or Supabase Auth) from Step 2 onward
- **Database**: Postgres (supports multi-user cleanly; SQLite is fine only
  for the very first local prototype in Step 1)
- **Extraction**: Claude (vision) via the Anthropic API
- **Scheduler**: cron/background worker; move to a proper job queue
  (e.g. BullMQ, Celery) once you have real users and volume
- **Notifications**: email first (Resend/SendGrid), Telegram Bot API as a
  strong second option, push (Firebase/APNs) once the app exists
- **Calendar sync**: Google Calendar API / Microsoft Graph (OAuth) — build
  this once Step 1 is solid, it's the highest-effort integration

## 11. Non-functional requirements

- **Never silently mis-save a date** — always confirm low-confidence extractions.
- **No missed reminders** — scheduler must be reliable even across restarts/deploys.
- **Data isolation** — one user must never see another user's deadlines.
- **Fast capture** — adding an event should take under 10 seconds regardless of method.

## 12. Open questions worth answering before Step 2

- Free vs. paid tiers — what's gated (e.g. unlimited events, calendar sync,
  team/shared deadlines) once you're ready to think about that?
- Any team/shared-deadline use case (e.g. a hackathon team seeing the same
  event) — worth designing the schema to allow an event to belong to a group,
  not just a single user, even if you don't build sharing UI yet?

---

## Ready-to-paste prompt for an AI coding tool (Step 1)

```
Build the core backend + minimal UI for a personal deadline-tracking product
called DeadlineKeeper. Design it as a multi-user system from the start (User
and Event tables both exist, Event has a user_id), even though only one test
user will be created for now.

Users can add a deadline (exam, submission, hackathon) via: (1) typing it
directly, (2) pasting text, or (3) uploading a screenshot. Screenshot and
pasted text both go through the same extraction pipeline using a
vision-capable LLM, which returns: title, type, due_date, due_time, timezone,
and a confidence_score. If confidence is low or the date is ambiguous (e.g.
relative dates like "next Friday"), ask a clarifying question before saving.

Store events with: user_id, title, type, due_date, due_time, timezone, source,
confidence_score, status, reminder_schedule, notes.

Build a reminder scheduler that checks events hourly and sends an email
notification at each configured offset before the due date (default: 7 days,
1 day, 2 hours before), logging fired reminders to avoid duplicates.

Build a minimal dashboard (even a single-page app is fine) showing upcoming,
due-soon, overdue, and completed events, with edit/delete/snooze/mark-done.

Keep the notification-sending function generic (send_notification(user,
message, channel)) so more channels can be added later without touching the
scheduler.
```

---

### Updated assumptions (flag if still wrong)
- Step 1 will still start with one test account, but the schema/backend are
  built multi-user from day one so there's no painful migration later.
- Calendar sync and email-inbox parsing are real near-term goals, not
  "someday" — they come right after the core loop works, not after a full
  native app.
- "Product" for now means a working, reliable web-based system with real
  accounts — the native app is the packaging step at the end, not a
  prerequisite for calling it a product.
