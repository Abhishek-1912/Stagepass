# Stagepass

Event discovery and ticketing platform: attendees discover and book events, organizers sell tickets and check
guests in, admins run the marketplace.

| Folder | What |
|---|---|
| `stagepass-api/` | Spring Boot 4.1.2 REST API (Java 17, jar, Maven wrapper) |
| `stagepass-web/` | React 19 + Vite + Tailwind 4 + shadcn/ui web app |
| `docs/superpowers/specs/` | Product and technical design |
| `docs/superpowers/plans/` | Implementation plans, one per milestone |
| `render.yaml` | Render deployment blueprint (Singapore) |

## Architecture in one paragraph

Supabase Auth handles sign-up, login, password reset and Google sign-in. The web app sends the Supabase access
token to the API, which validates it against Supabase's public keys (JWKS), creates a local `users` row on first
use, and loads roles (`ATTENDEE`, `ORGANIZER`, `ADMIN`) from **our** database — never from the token. All data
lives in Supabase Postgres, with the schema owned by Flyway migrations in `stagepass-api/src/main/resources/db/migration`.
Do not create tables from the Supabase dashboard or a `supabase/migrations` folder; Flyway is the single source of truth.

## Prerequisites

- JDK 17 (Temurin recommended)
- Node.js 24+
- A Supabase project (region **Mumbai / ap-south-1**)

No Docker is needed for development: API tests use an embedded Postgres 17.

## Supabase setup checklist

1. Create a project in the Mumbai region.
2. **Authentication → Providers:** enable Email (with "Confirm email") and Google.
3. **Authentication → URL configuration:**
   - Site URL: `http://localhost:5173` (dev) / your Render web URL (prod)
   - Redirect URLs: `http://localhost:5173/**` and `https://<your-web-domain>/**`
4. **Authentication → SMTP:** use Resend as custom SMTP so verification and reset emails come from your domain.
5. **Project Settings → Data API:** copy the project URL and the anon/publishable key (for the web app).
6. **Connect → Session pooler:** copy the JDBC host, user (`postgres.<project-ref>`) and password (for the API).
   The API derives the JWKS URL (`<SUPABASE_URL>/auth/v1/.well-known/jwks.json`) and issuer (`<SUPABASE_URL>/auth/v1`).

## Run locally

API:

```bash
cp stagepass-api/src/main/resources/application-local.properties.example stagepass-api/src/main/resources/application-local.properties
```

Fill in the values, then:

```bash
cd stagepass-api && ./mvnw spring-boot:run
```

Web:

```bash
cp stagepass-web/.env.example stagepass-web/.env.local
```

```bash
cd stagepass-web && npm install && npm run dev
```

Open http://localhost:5173. The email listed in `BOOTSTRAP_ADMIN_EMAILS` becomes an admin on first sign-in.

## Email

| Channel | Emails | Configured in |
|---|---|---|
| Supabase Auth → Resend SMTP | sign-in code (OTP), confirm sign-up, password reset | Supabase → Authentication → Emails (SMTP + templates from `docs/email-templates/`) |
| Stagepass API → Resend API | welcome, booking confirmed, refunds, reminders… | `RESEND_API_KEY`, `RESEND_FROM` |

App emails are queued in `email_outbox` and sent by a background worker every 15 s with retries. Without
`RESEND_API_KEY` they stay queued. Until a domain is verified in Resend, send from `onboarding@resend.dev`
(Resend then only delivers to the Resend account owner's inbox).

## Test accounts

Create these in Supabase → Authentication → Users → Add user, with **Auto Confirm User** ticked:
`admin@stagepass.test` (becomes admin via `BOOTSTRAP_ADMIN_EMAILS`), `organizer@stagepass.test`, `attendee@stagepass.test`.
Use a real inbox only when testing email delivery.

## Tests

```bash
cd stagepass-api && ./mvnw test
```

```bash
cd stagepass-web && npx vitest run && npm run lint && npm run build
```

## Environment variables

### API (`stagepass-api`)

| Variable | Required | Description |
|---|---|---|
| `DB_URL` | yes | JDBC URL of the Supabase session pooler, e.g. `jdbc:postgresql://aws-0-ap-south-1.pooler.supabase.com:5432/postgres` |
| `DB_USERNAME` / `DB_PASSWORD` | yes | Pooler credentials |
| `SUPABASE_URL` | yes | `https://<project-ref>.supabase.co` |
| `SUPABASE_JWKS_URI`, `SUPABASE_ISSUER` | no | Override the derived JWKS URL / issuer |
| `WEB_BASE_URL` | yes | Public URL of the web app |
| `CORS_ALLOWED_ORIGINS` | no | Comma-separated; defaults to `WEB_BASE_URL` |
| `BOOTSTRAP_ADMIN_EMAILS` | no | Comma-separated emails granted `ADMIN` on first sign-in |
| `QR_SECRET` | yes | Random secret (32+ chars) used to sign ticket QR codes |
| `PAYOUT_ENCRYPTION_KEY` | yes | Base64 32-byte key encrypting organizer bank details |
| `RESEND_API_KEY` | for email | Resend API key (sending access) for app emails |
| `RESEND_FROM` | no | Sender, e.g. `Stagepass <no-reply@yourdomain>`; default `onboarding@resend.dev` |
| `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET` | for images | Cloudinary account for signed event-image and logo uploads |
| `SPRING_PROFILES_ACTIVE` | prod | `prod` on Render (JSON logs) |

### Web (`stagepass-web`) — all public, bundled into the browser

| Variable | Description |
|---|---|
| `VITE_API_BASE_URL` | API origin, e.g. `https://stagepass-api.onrender.com` |
| `VITE_SUPABASE_URL` | Supabase project URL |
| `VITE_SUPABASE_ANON_KEY` | Supabase anon/publishable key |

## Deploy (Render)

1. Push this repository to GitHub.
2. In Render: **New → Blueprint**, pick the repository; Render reads `render.yaml`.
3. Enter the `sync: false` secrets when prompted. Set the API's `WEB_BASE_URL` to the static site's URL and the
   web app's `VITE_API_BASE_URL` to the API's URL.
4. Add the Render web URL to Supabase's Site URL / Redirect URLs.

Flyway migrates the database automatically when the API starts.
