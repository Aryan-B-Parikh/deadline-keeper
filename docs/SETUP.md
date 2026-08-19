# DeadlineKeeper — Local Development Setup

## Prerequisites

| Tool | Version | Purpose |
|---|---|---|
| Java | 17+ | Spring Boot runtime |
| Maven | 3.8+ | Backend build tool |
| Node.js | 20+ | Frontend runtime |
| npm | 10+ | Frontend package manager |
| Docker | Current | PostgreSQL/Testcontainers integration tests |

## 1. Supabase

1. Create a Supabase project.
2. Enable **Email** and **Google** authentication as required by the frontend.
3. Note the project URL and JWT secret.
4. Configure the database connection values.
5. Ensure the signup trigger creates the application's `users` row.

The backend does **not** require the Supabase service-role key.

## 2. Google Gemini

1. Create an API key in Google AI Studio.
2. Set `GEMINI_API_KEY` and optionally `GEMINI_MODEL`.

## 3. SendGrid

1. Create a SendGrid account and API key with Mail Send and Inbound Parse permissions.
2. Verify the sender identity used by DeadlineKeeper.
3. Configure an Inbound Parse domain, for example `deadlines.yourdomain.com`.
4. Configure SendGrid to POST inbound mail to:
   `https://<backend-domain>/api/inbox/webhook`
5. Users receive a unique forwarding address in the form:
   `deadline+<user-token>@<inbox-parse-domain>`

The recipient address carries the per-user forwarding token. The backend does not use a shared inbox webhook token or sender-email fallback for authentication.

For local development, expose the backend with a tunnel such as ngrok and configure the HTTPS webhook URL in SendGrid.

## 4. Google Calendar API

1. Create or reuse a Google Cloud project.
2. Enable the Google Calendar API.
3. Create an OAuth 2.0 Web Application client.
4. Add the callback URI:
   `http://localhost:8080/api/calendar/sync/callback`
5. Add the production callback URI when deploying.

## 5. Environment Variables

Secrets are read from environment variables or the gitignored `deadline-keeper-backend/.env` file.

Create `.env` from `.env.example`:

```env
# Supabase
SUPABASE_URL=https://<your-project-ref>.supabase.co
SUPABASE_JWT_SECRET=<your-jwt-secret>

# Database
DATABASE_URL=jdbc:postgresql://<your-project-ref>.supabase.co:5432/postgres?sslmode=require
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=<your-db-password>

# Gemini
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

# Base64-encoded 256-bit AES key
APP_ENCRYPTION_KEY=<base64-32-byte-key>
```

For production, set the equivalent variables in the deployment environment and activate the `prod` or `production` Spring profile.

## 6. Run the Backend

```bash
cd deadline-keeper-backend
mvn spring-boot:run
```

Flyway applies migrations automatically. The backend listens on `http://localhost:8080`.

Health check:

```bash
curl http://localhost:8080/api/health
```

## 7. Run the Frontend

```bash
cd deadline-keeper-frontend
npm ci
npm run dev
```

The frontend listens on `http://localhost:3000`.

Create `deadline-keeper-frontend/.env.local`:

```env
NEXT_PUBLIC_SUPABASE_URL=https://<your-project-ref>.supabase.co
NEXT_PUBLIC_SUPABASE_ANON_KEY=<your-anon-key>
NEXT_PUBLIC_API_URL=http://localhost:8080
```

## 8. Test User

1. Open `http://localhost:3000/register`.
2. Register with Supabase Auth.
3. Confirm the application `users` row is created.
4. Sign in and open the dashboard.
5. The Settings/profile area exposes the user's unique forwarding address.

## 9. Verification

Backend:

```bash
./mvnw clean verify
```

Frontend:

```bash
npm ci
npm run lint
npm run build
```

The backend integration suite uses PostgreSQL Testcontainers. Docker must be running for those tests.

## 10. Project Structure

```text
├── deadline-keeper-backend/
│   ├── src/main/java/com/deadlinekeeper/
│   ├── src/main/resources/db/migration/
│   ├── pom.xml
│   └── Dockerfile
├── deadline-keeper-frontend/
│   ├── src/app/
│   ├── src/components/
│   ├── src/lib/
│   └── package.json
├── docs/
└── .github/workflows/ci.yml
```

## Troubleshooting

| Problem | Fix |
|---|---|
| Flyway migration fails | Check database connectivity and credentials; inspect the first failing migration |
| JWT validation fails | Verify `SUPABASE_URL` and `SUPABASE_JWT_SECRET` |
| DataSource startup fails | Configure `DATABASE_URL`, username, and password |
| Gemini extraction fails | Check Gemini API key and quota |
| Email delivery fails | Verify SendGrid sender identity and Mail Send permission |
| Inbound mail is rejected | Verify the recipient uses the user's generated `deadline+<token>@...` address and that the webhook receives the `to` field |
| CORS errors | Set `CORS_ALLOWED_ORIGINS` to the exact frontend origin(s) |
| Calendar OAuth fails | Verify the Google redirect URI exactly matches the configured callback |
| Testcontainers fails | Start Docker Desktop and verify the Docker daemon is reachable |
