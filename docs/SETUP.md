# DeadlineKeeper — Local Development Setup

## Prerequisites

| Tool | Version | Purpose |
|---|---|---|
| Java | 17+ | Spring Boot runtime |
| Maven | 3.8+ | Backend build tool |
| Node.js | 20+ | Frontend runtime |
| npm | 10+ | Frontend package manager |

## 1. Supabase Project

1. Create a free account at [supabase.com](https://supabase.com).
2. Create a new project — note the **Project URL** and **anon key** and **service_role key**.
3. Go to **Authentication → Providers** and enable:
   - **Email** (default, enabled)
   - **Google** — create a Google Cloud OAuth 2.0 client, add the redirect URL:
     `https://<your-project-ref>.supabase.co/auth/v1/callback`
4. Go to **SQL Editor** and run the trigger function from `DATABASE.md` (Supabase Triggers section)
   to auto-create `users` rows on signup.
5. Note your **Database connection string** (Transaction pooler or Direct) from
   **Settings → Database → Connection string**.

## 2. Google Gemini API

1. Go to [Google AI Studio](https://aistudio.google.com/) and generate an API key.
2. The backend uses `gemini-2.5-flash` (or latest available vision model).

## 3. SendGrid

1. Create an account at [sendgrid.com](https://sendgrid.com).
2. Create an **API Key** with "Mail Send" and "Inbound Parse" permissions.
3. Verify a sender identity (single sender or domain).
4. For inbox parsing: configure **Inbound Parse** in SendGrid settings:
   - Hostname: `deadlines.yourdomain.com` (or a SendGrid-provided test domain)
   - URL: `http://localhost:8080/api/inbox/webhook` (use ngrok for local dev)

## 4. Google Calendar API

1. Go to [Google Cloud Console](https://console.cloud.google.com/).
2. Create a new project (or reuse the one from Supabase Auth).
3. Enable the **Google Calendar API**.
4. Create an **OAuth 2.0 Client ID** (Web application type).
5. Add authorized redirect URIs:
   - `http://localhost:8080/api/calendar/sync/callback` (local dev)
   - Your production callback URL
6. Note the **Client ID** and **Client Secret**.

## 5. Environment Variables

Secrets are **not** stored in `application.yml` — they are read from environment
variables (or the `deadline-keeper-backend/.env` file, which is loaded automatically
by [spring-dotenv](https://github.com/paulschwarz/dotenv) and gitignored).

Create `deadline-keeper-backend/.env` (copy from `.env.example`):

```env
# Supabase
SUPABASE_URL=https://<your-project-ref>.supabase.co
SUPABASE_SERVICE_ROLE_KEY=<your-service-role-key>
SUPABASE_JWT_SECRET=<your-jwt-secret>

# Database (Supabase Postgres direct connection)
DATABASE_URL=jdbc:postgresql://db.<your-project-ref>.supabase.co:5432/postgres
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=<your-db-password>

# Google Gemini
GEMINI_API_KEY=<your-gemini-api-key>
GEMINI_MODEL=gemini-2.5-flash

# SendGrid
SENDGRID_API_KEY=<your-sendgrid-api-key>
SENDGRID_FROM_EMAIL=deadlines@yourdomain.com
SENDGRID_INBOX_PARSE_DOMAIN=deadlines.yourdomain.com

# Google Calendar
GOOGLE_CALENDAR_CLIENT_ID=<your-google-client-id>
GOOGLE_CALENDAR_CLIENT_SECRET=<your-google-client-secret>
GOOGLE_CALENDAR_REDIRECT_URI=http://localhost:8080/api/calendar/sync/callback

# App
APP_BASE_URL=http://localhost:8080
CORS_ALLOWED_ORIGINS=http://localhost:3000
```

Create `deadline-keeper-frontend/.env.local`:

```env
NEXT_PUBLIC_SUPABASE_URL=https://<your-project-ref>.supabase.co
NEXT_PUBLIC_SUPABASE_ANON_KEY=<your-anon-key>
NEXT_PUBLIC_API_URL=http://localhost:8080
```

## 6. Running the Backend

```bash
cd deadline-keeper-backend
mvn spring-boot:run
```

The backend starts on `http://localhost:8080`. Flyway migrations run automatically
on startup and create/update all database tables.

To verify:
```bash
curl http://localhost:8080/api/health
# Expected: {"status":"ok"}
```

## 7. Running the Frontend

```bash
cd deadline-keeper-frontend
npm install
npm run dev
```

The frontend starts on `http://localhost:3000`.

## 8. Creating a Test User

1. Open `http://localhost:3000/register`.
2. Sign up with an email and password.
3. The Supabase Auth trigger creates a `users` row automatically.
4. Log in — you'll be redirected to the dashboard.

## 9. Local Development with ngrok (for webhooks)

SendGrid inbound parse and other webhooks need a public URL during local development:

```bash
ngrok http 8080
```

Update your SendGrid inbound parse URL to the ngrok HTTPS URL:
`https://<your-ngrok-id>.ngrok.io/api/inbox/webhook`

## 10. Project Structure Reference

```
D:\Agent\
├── deadline-keeper-backend/     # Spring Boot backend
│   ├── src/main/java/com/deadlinekeeper/
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── db/migration/        # Flyway SQL migrations
│   ├── pom.xml
│   └── Dockerfile
├── deadline-keeper-frontend/    # Next.js frontend
│   ├── src/app/                 # App Router pages
│   ├── src/components/          # Reusable UI components
│   ├── src/lib/                 # API client, Supabase client
│   ├── package.json
│   └── next.config.js
├── docs/                        # This documentation
│   ├── ARCHITECTURE.md
│   ├── API.md
│   ├── DATABASE.md
│   └── SETUP.md
└── deadline-assistant-master-prompt.md
```

## Troubleshooting

| Problem | Fix |
|---|---|
| Flyway migration fails | Check `DATABASE_URL` and credentials; ensure the Supabase project is running |
| JWT validation fails | Verify `SUPABASE_JWT_SECRET` matches your project's JWT secret (Settings → API → JWT settings). The backend auto-detects the format: base64-encoded legacy secrets, `sb_secret_`-prefixed secrets, and raw strings are all handled |
| Backend starts with "Failed to configure a DataSource" | `DATABASE_URL` is empty — create `.env` from `.env.example` or set the env vars |
| Gemini extraction returns empty | Check API key and quota at Google AI Studio |
| SendGrid emails not sending | Verify sender identity; check API key permissions |
| CORS errors in frontend | Ensure `CORS_ALLOWED_ORIGINS` includes your frontend URL |
| Google Calendar OAuth fails | Check redirect URI matches exactly (no trailing slash) |
