# Stagepass — Event Ticketing Platform: Design

Date: 2026-11-05
Status: Draft for approval

## 1. Intent

Stagepass is a real commercial event ticketing product for an Indian company. Attendees discover events,
book tickets, pay via Razorpay, and receive QR tickets. Organizers create and sell events, see sales, and
check attendees in at the door. Admins approve organizers and events, watch money flows, and handle
refunds, disputes, reports, and payouts.

It must feel like a premium SaaS product (Linear/Stripe/Luma polish, BookMyShow booking clarity,
Eventbrite organizer depth), and it must be genuinely working software — not a mockup.

## 2. Goals, non-goals, anti-goals

**Goals (launch)**
- End-to-end working flow in Razorpay **test mode**: discover → book → pay → verified on backend → QR ticket
  (screen + PDF + email) → scanned and checked in at the door.
- Organizer self-serve: apply, get approved, create events with multiple ticket types, submit for review,
  track sales, export attendees, refund, check in.
- Admin control: users, organizers, event approval, transactions, refunds/disputes, reports, categories,
  payouts ledger, platform analytics, fee configuration.
- Mobile-first responsive UI, light + dark themes.
- Production-shaped: migrations, tests, environment config, audit logs, deployable to Render.

**Non-goals (launch; data model leaves room)**
- Reserved seating / seat maps (general-admission ticket types only).
- Automated organizer payouts (RazorpayX) — payouts are recorded manually by admin.
- Razorpay Route split payments.
- Apple/Google Wallet passes — needs company Apple Developer + Google Wallet issuer accounts. A
  "Add to wallet" hook is designed but shipped disabled.
- Offline QR scanning.
- Native mobile apps.
- Multi-currency (INR only).

**Anti-goals (would be failure even if it "works")**
- Overselling a ticket type, or confirming a booking without backend-verified payment.
- Charging a customer twice or confirming one payment twice.
- A generic, cluttered, template-looking UI.
- Any admin/organizer capability reachable by a user without the role.

## 3. Constraints (decided by the user)

- **Name:** Stagepass. Java base package `com.stagepass`. Repos/folders: `stagepass-api`, `stagepass-web`.
- **Backend:** Java **17**, Spring Boot **4.1.2** (release — 4.1.2 is GA; snapshot not used because it can
  change under us), **jar** packaging, Maven (wrapper), `application.properties` + typed
  `@ConfigurationProperties` classes, profile files `application-dev.properties` /
  `application-prod.properties`. Spring Security, Spring Data JPA, REST.
- **Database:** Supabase Postgres (Mumbai region).
- **Auth:** Supabase Auth for identity (sign-up, login, password reset, email verification, Google OAuth).
  Spring Boot owns roles and authorization.
- **Payments:** Razorpay, test keys now; "collect and payout" model (all money lands in Stagepass's account).
- **Frontend:** React + Tailwind CSS + shadcn/ui + React Router + TanStack Query.
- **Integrations:** Cloudinary (images), Resend (email), Google Maps, a QR library.
- **Hosting:** Render (Singapore region) for both frontend (static site) and backend (web service).
- **Palette:** Primary #6C4CF1, Secondary #377DFF, Background #F8F9FC, Text #171923, Accent #FF6B6B.

## 4. Approaches considered

| | Approach | Trade-off |
|---|---|---|
| **A (chosen)** | Modular monolith Spring Boot jar + Vite React SPA as Render static site; Spring serves a tiny server-rendered share page at `/e/{slug}` with Open Graph tags that redirects to the SPA | One backend to deploy, clean module boundaries, can split later. Link previews (WhatsApp/Google) work via the share page; full-SPA pages are not SEO-rendered |
| B | Same backend + React Router framework mode (SSR) on a Node service | Best SEO, but a second runtime to host and more frontend complexity |
| C | Frontend talks to Supabase directly (RLS), Spring only for payments | Fast start, but overselling/role/audit rules split between SQL policies and Java — risky for money |

Microservices were rejected outright: operational cost with no benefit at this scale.
Chosen by: assistant recommendation, user delegated ("as your wish").

## 5. Architecture

### 5.1 Repository layout (single Git monorepo)

```
stagepass/
  stagepass-api/        Spring Boot (Maven wrapper)
  stagepass-web/        Vite + React + TypeScript
  docs/
  render.yaml           Render blueprint (static site + web service)
  .github/workflows/    CI: backend tests, frontend lint/test/build
```

### 5.2 Backend modules (packages under `com.stagepass`)

Each module: `api` (controllers + request/response DTOs), `service`, `domain` (JPA entities), `repository`.
Cross-module calls go through services only, never another module's repository.

- `identity` — user sync from Supabase JWT, roles, profile, organizer applications.
- `catalog` — categories, venues, events, ticket types, images, event lifecycle & approval.
- `discovery` — search, filters, home feeds (featured/trending/upcoming/nearby), share page.
- `booking` — inventory holds, bookings, fees calculation, booking limits, expiry job.
- `payment` — Razorpay orders, signature verification, webhooks, refunds, disputes.
- `ticketing` — ticket issuance, signed QR tokens, PDF generation.
- `checkin` — scan validation, check-in records.
- `organizer` — dashboards, analytics, attendee list, CSV export.
- `admin` — users, organizers, approvals, transactions, reports, payouts ledger, settings, analytics.
- `engagement` — saved events, reviews, event reports.
- `notification` — in-app notifications, Resend emails (async, outbox table).
- `common` — error handling, audit log, rate limiting, config properties, security, money utils.

### 5.3 Frontend structure (`stagepass-web/src`)

```
app/          router, providers (QueryClient, theme, auth)
components/ui shadcn primitives (themed)
components/   shared composites (EventCard, PriceTag, DateBadge, EmptyState, StatCard, DataTable…)
features/     auth, discovery, event, checkout, tickets, profile, organizer, scanner, admin
lib/          api client (fetch + JWT injection + error normalization), supabase client, format utils
```

Libraries: Vite, React 19, TypeScript, Tailwind v4, shadcn/ui, React Router v7 (declarative/data mode),
TanStack Query, react-hook-form + zod, Motion (animations), Recharts (charts), `@supabase/supabase-js`,
Razorpay Checkout script, `html5-qrcode` (camera scanner), lucide-react icons.

### 5.4 Communication

- SPA → API over HTTPS JSON at `/api/v1/**`. Every authed request sends `Authorization: Bearer <Supabase access token>`.
- API is CORS-restricted to the frontend origin(s) from config.
- Errors use RFC 9457 Problem Details (`type`, `title`, `status`, `detail`, `code`, `errors[]` for field errors).
- Pagination: `?page=&size=&sort=`, response `{ items, page, size, totalItems, totalPages }`.
- Razorpay → API: `POST /api/v1/webhooks/razorpay` (unauthenticated, signature-verified).

## 6. Identity, roles, and authorization

- Supabase Auth issues JWTs (asymmetric signing keys). Spring Security runs as an OAuth2 **resource server**
  validating tokens against the project JWKS URL (`https://<project>.supabase.co/auth/v1/.well-known/jwks.json`),
  checking issuer and audience `authenticated`.
- Our `users` table is keyed by Supabase user id (`uuid`). On first authenticated request (`GET /api/v1/me`)
  the user row is upserted from token claims (email, name). No cross-schema FK to `auth.users`.
- Roles live in our DB (`roles`, `user_roles`): `ATTENDEE` (default), `ORGANIZER`, `ADMIN`. A custom
  `JwtAuthenticationConverter` loads roles from DB (cached ~60s) — roles are never read from the token, so
  only our backend can grant them.
- Organizer onboarding: attendee submits an organizer application (org name, contact, description, logo,
  payout details). Admin approves → `ORGANIZER` role granted. (Assistant's call: approval required.)
- First admin is seeded via a config property `stagepass.bootstrap.admin-emails`; later admins granted by admins.
- Method-level security (`@PreAuthorize`) plus ownership checks in services (organizer can only touch own events).
- Auth emails (verify, reset) are sent by Supabase, configured with Resend as custom SMTP.
- Forgot/reset password and register screens are our UI calling `supabase-js`.

## 7. Domain model (Postgres, Flyway migrations)

Conventions: `uuid` PKs (`gen_random_uuid()`), `created_at`/`updated_at timestamptz`, money in **paise as
`bigint`**, timestamps stored UTC and displayed in the event's timezone (default `Asia/Kolkata`),
optimistic locking `version` column on mutable aggregates. Status columns are `text` with `CHECK` constraints.

| Table | Key columns / constraints |
|---|---|
| `users` | id (= Supabase uid), email unique, full_name, phone, avatar_url, city, status (ACTIVE/SUSPENDED) |
| `roles` | id, name unique |
| `user_roles` | user_id, role_id, PK(user_id, role_id) |
| `organizers` | id, user_id unique, org_name, slug unique, bio, logo_url, contact_email, contact_phone, website, status (PENDING/APPROVED/REJECTED/SUSPENDED), payout_account_name, payout_account_number_enc, payout_ifsc, commission_bps_override |
| `event_categories` | id, name, slug unique, icon, sort_order, active |
| `venues` | id, organizer_id, name, address_line, city, state, pincode, lat, lng, google_place_id; index(city), index(lat,lng) |
| `events` | id, organizer_id, category_id, venue_id (nullable for online), slug unique, title, summary, description (markdown), cover_image_url, gallery jsonb, starts_at, ends_at, timezone, status (DRAFT/PENDING_REVIEW/PUBLISHED/REJECTED/CANCELLED/COMPLETED), rejection_reason, is_featured, terms, max_tickets_per_user, convenience_fee_override, commission_bps_override, search_vector tsvector (generated) GIN-indexed, popularity_score; index(status, starts_at), index(category_id) |
| `ticket_types` | id, event_id, name, description, price_paise (≥0), sale_starts_at, sale_ends_at, min_per_order, max_per_order, sort_order, status (ACTIVE/HIDDEN/SOLD_OUT) |
| `ticket_inventory` | ticket_type_id PK, total, sold, held; CHECK(sold + held ≤ total, all ≥ 0) |
| `bookings` | id, code unique (e.g. `SP-7K2M9Q`), user_id, event_id, status (PENDING_PAYMENT/CONFIRMED/EXPIRED/CANCELLED/REFUNDED/PARTIALLY_REFUNDED), subtotal_paise, fee_paise, fee_gst_paise, total_paise, commission_paise, hold_expires_at, attendee_name, attendee_email, attendee_phone, idempotency_key unique; index(user_id), index(event_id, status) |
| `booking_items` | id, booking_id, ticket_type_id, quantity, unit_price_paise |
| `tickets` | id, booking_id, booking_item_id, ticket_type_id, event_id, code unique, holder_name, status (VALID/CHECKED_IN/CANCELLED/REFUNDED), qr_nonce |
| `payments` | id, booking_id, razorpay_order_id unique, razorpay_payment_id unique nullable, amount_paise, status (CREATED/AUTHORIZED/CAPTURED/FAILED/REFUNDED), method, raw jsonb |
| `refunds` | id, payment_id, booking_id, razorpay_refund_id unique, amount_paise, reason, status (REQUESTED/PROCESSING/PROCESSED/FAILED), requested_by |
| `disputes` | id, payment_id, razorpay_dispute_id unique, amount_paise, reason, status, raw jsonb |
| `webhook_events` | id, provider, event_id unique (Razorpay `x-razorpay-event-id`), type, payload jsonb, processed_at |
| `check_ins` | id, ticket_id **unique**, event_id, scanned_by, scanned_at, device_info |
| `scan_logs` | id, event_id, scanned_by, result (VALID/ALREADY_USED/INVALID/WRONG_EVENT), ticket_id nullable, at |
| `reviews` | id, event_id, user_id, rating 1–5, comment, UNIQUE(event_id, user_id) |
| `saved_events` | user_id, event_id, PK(user_id, event_id) |
| `event_reports` | id, event_id, reporter_id, reason, details, status (OPEN/RESOLVED/DISMISSED), resolution_note |
| `notifications` | id, user_id, type, title, body, link, read_at; index(user_id, read_at) |
| `email_outbox` | id, to, template, payload jsonb, status, attempts, last_error, send_after |
| `organizer_ledger_entries` | id, organizer_id, event_id, booking_id, type (SALE/REFUND/COMMISSION/PAYOUT/ADJUSTMENT), amount_paise (signed), created_at |
| `payouts` | id, organizer_id, amount_paise, reference, paid_at, recorded_by, note |
| `platform_settings` | key PK, value jsonb (fee config, etc.) |
| `audit_logs` | id, actor_id, action, entity_type, entity_id, before jsonb, after jsonb, ip, at; index(entity_type, entity_id) |

Payout bank account number is encrypted at the application layer (AES-GCM, key from env). Extensions:
`pgcrypto`, `pg_trgm`.

## 8. Key flows

### 8.1 Event lifecycle
DRAFT → (organizer submits) PENDING_REVIEW → (admin) PUBLISHED or REJECTED (with reason; organizer can edit
and resubmit). PUBLISHED → CANCELLED by organizer or admin (all confirmed bookings auto-refunded, emails sent).
PUBLISHED → COMPLETED by scheduled job after `ends_at`. An event can be **deleted** only in DRAFT/REJECTED or
when it has no bookings. Ticket type price cannot change once any ticket of it is sold (new type instead);
inventory can be increased, or decreased down to `sold + held`.

### 8.2 Fees (configurable in `platform_settings`, overridable per event/organizer)
- Attendee convenience fee per booking: `percent_bps × subtotal + flat_paise × ticket_count`. Default 2.5% + ₹10/ticket.
- GST 18% on the convenience fee only (ticket GST is organizer's responsibility; out of scope).
- Organizer commission: default 0 bps; recorded as ledger entry.
- Free events (subtotal 0): no fee, no Razorpay — booking confirms immediately.
- Rounding: half-up to nearest paisa, computed only server-side. `GET /api/v1/events/{id}/quote` returns the breakdown.

### 8.3 Booking and payment
1. `POST /api/v1/bookings` with items + attendee details + `Idempotency-Key` header.
2. In one DB transaction: validate sale window, per-order and per-user limits (counting CONFIRMED + live
   PENDING_PAYMENT), then for each item run an atomic
   `UPDATE ticket_inventory SET held = held + :q WHERE ticket_type_id = :id AND total - sold - held >= :q`;
   0 rows updated ⇒ `409 SOLD_OUT`, transaction rolls back. Create booking `PENDING_PAYMENT`, `hold_expires_at = now + 10 min`.
3. Create Razorpay order (amount = total, receipt = booking code, notes = booking id); save `payments` CREATED.
   Return `{bookingId, razorpayOrderId, keyId, amount}`.
4. SPA opens Razorpay Checkout (UPI, cards, netbanking, wallets). On success handler, SPA posts
   `{razorpay_payment_id, razorpay_order_id, razorpay_signature}` to `POST /api/v1/payments/verify`.
5. Backend verifies HMAC-SHA256 signature with key secret, fetches payment from Razorpay API to confirm
   `captured` (auto-capture enabled on orders) and amount, then **confirms** the booking.
6. Webhook `payment.captured` / `order.paid` does the same confirmation independently (covers closed tabs).
7. **Confirm is idempotent**: `SELECT … FOR UPDATE` on the booking; if already CONFIRMED, no-op. Move held → sold,
   issue tickets, write ledger SALE (+COMMISSION), enqueue confirmation email + notification. Webhook events are
   deduplicated by `webhook_events.event_id` unique.
8. Expiry job (every minute): PENDING_PAYMENT past `hold_expires_at` → EXPIRED, release held.
9. **Late payment** for an EXPIRED booking: try to re-take inventory; if available confirm, else auto-refund in full
   and notify the attendee.
10. `payment.failed` → payment FAILED; booking stays pending until expiry so the user can retry within the hold.

### 8.4 Refunds and disputes
- Refunds initiated by organizer (own events) or admin; attendee can *request* a refund if the event's refund
  policy allows (policy field on event: NONE / UNTIL_X_HOURS_BEFORE). Full or per-ticket partial.
- Backend calls Razorpay refund API, refund PROCESSING; webhook `refund.processed`/`refund.failed` finalizes.
  Tickets for refunded items → REFUNDED (no longer scannable), inventory sold decremented, ledger REFUND entry.
  Convenience fee refundable on event cancellation; not refundable on attendee-requested refunds (configurable).
- Disputes: `payment.dispute.*` webhooks recorded in `disputes`, surfaced in admin with status.

### 8.4a Money rules (assistant's calls)
- **Razorpay gateway fee** (~2% + GST) is absorbed by Stagepass out of the convenience fee; recorded per payment
  (`payments.gateway_fee_paise`, `gateway_tax_paise` from Razorpay's payment fetch) so platform net revenue =
  convenience fee + commission − gateway fees. Organizer's payable is never reduced by gateway fees.
- **Payout eligibility:** an organizer's balance for an event becomes payable **3 days after the event ends**
  (configurable `stagepass.payout.hold-days`). Admin payouts screen shows *pending* vs *payable* balance per organizer.
- **Negative balances** (refund after payout) carry forward and are netted against the next payout; admin sees them flagged.
- **Refund policy** column on `events`: `refund_policy` (NONE / UNTIL_HOURS_BEFORE) + `refund_cutoff_hours`.
  Attendee refund request within policy is **automatic** (ticket price refunded; convenience fee + its GST kept).
  Outside policy, the request goes to the organizer to approve/decline. Partial refunds are per ticket, splitting
  fee/GST/commission pro-rata by ticket price.
- **Editing a published event:** text, images and ticket additions apply immediately. Changing **date/time or venue**
  notifies all ticket holders by email and opens a 7-day window in which they can self-refund in full (fee included),
  regardless of policy. No re-review by admin, but changes are audit-logged and admins can unpublish.
- **GST invoices:** Stagepass issues a tax invoice for the convenience fee on each paid booking: `invoices` table
  (sequential number per financial year, e.g. `SP/2026-27/000123`), company GSTIN and state from config, CGST+SGST if
  buyer state = company state else IGST (buyer state from attendee details, default company state). Invoice PDF linked
  from booking. Organizer application collects **PAN** (required) and **GSTIN** (optional). TDS under 194-O is
  modeled as a `TDS` ledger entry type with configurable rate (default 0 until the company's CA confirms).
  *All tax specifics must be confirmed by the company's accountant before going live.*

### 8.5 Tickets and QR
- One `tickets` row per seat purchased, with a human code (`SP-XXXX-XXXX`).
- QR payload = compact signed token: `base64url(ticketId|eventId|qrNonce) + "." + HMAC-SHA256(secret)`. No PII in QR.
  Forged or altered codes fail signature check; `qr_nonce` can be rotated to invalidate a leaked QR (e.g. on ticket transfer later).
- QR generated server-side with ZXing (PNG endpoint, used in PDF and email) and rendered client-side from the token.
- PDF via OpenPDF: event, venue, time, holder, ticket type, booking code, QR, terms. Email includes PDF attachment.
- Ticket page shows status badge (Valid / Checked in / Cancelled / Refunded) and event details.

### 8.6 Check-in
- Scanner page (organizer or assigned door staff of that event — see "Door staff and gates" below)
  uses phone camera via `html5-qrcode`; manual code entry fallback.
- `POST /api/v1/checkin/scan {eventId, token}` → verifies signature, ticket belongs to event, ticket status.
  Check-in insert relies on `check_ins.ticket_id` unique constraint — concurrent duplicate scans: exactly one wins,
  others get `ALREADY_USED` with original scan time and scanner.
- Responses: `VALID` (green, attendee name, ticket type), `ALREADY_USED` (amber, when/by whom), `INVALID` (red),
  `WRONG_EVENT` (red). Every scan written to `scan_logs`. Live counter of checked-in / total.

**Door staff and gates (added 2026-11-06, user decision — supersedes "staff role is a later addition"):**
- **Event staff.** An organizer invites door staff by email, either to one event or to all of the organizer's events,
  with role `SCANNER`. An invite to an address without an account is held and applied on that person's first
  sign-in (email-code login creates the account). Staff can open the scanner only for events they are assigned to;
  they cannot see sales, payouts, attendee lists or event settings. The organizer can remove staff at any time
  (effective on the next scan). The organizer account itself can always scan its own events.
- **Gates.** An organizer defines named gates per event (e.g. "Gate 1", "VIP Gate"), each optionally limited to some
  ticket types. A scanner picks a gate when opening the scanner. A valid ticket whose type is not allowed at that gate
  answers `WRONG_GATE` (red) naming the allowed gate(s), and is **not** checked in. Events without gates work as before.
- **Records.** `check_ins` and `scan_logs` gain `gate_id` (nullable). `ALREADY_USED` shows when, at which gate, and by
  whom. Live counters: checked-in / total, per gate, per staff member; the organizer can review the scan log.
- Concurrency rule unchanged: the `check_ins.ticket_id` unique constraint means exactly one scan wins across all gates.
- Offline scanning stays out of scope (cannot prevent the same ticket entering at two gates).

### 8.7 Discovery
- Search: Postgres full-text on title/summary/venue/city (`search_vector`) + `pg_trgm` for fuzzy title.
- Filters: city, date range (today/tomorrow/weekend/custom), category, price range (min ticket price), free only.
  Sort: popularity (`popularity_score` = recent bookings weighted + saves), soonest.
- Home feeds: Featured (admin flag), Trending (popularity last 7 days), Upcoming, Near you (browser geolocation
  → haversine on venue lat/lng within radius; falls back to chosen city), category rails.
- Only PUBLISHED, non-ended events are public.
- Share: Web Share API on mobile, copy link / WhatsApp / X fallback; links point to `/e/{slug}` served by the API
  with OG meta tags, then redirect to `https://<web>/events/{slug}`.
- Maps: Google Maps Embed iframe on event page; Places Autocomplete in venue form to capture lat/lng.

### 8.8 Images
Cloudinary signed direct upload: API issues a short-lived signature restricted to folder `stagepass/events/{eventId}`,
allowed formats (jpg, png, webp), max 5 MB; browser uploads directly; API accepts only URLs whose public_id is in that
folder. Delivery via Cloudinary transformations (`f_auto,q_auto`, responsive widths).

### 8.9 Notifications and email
Emails (Resend): booking confirmed (with PDF), booking expired, refund processed, event cancelled, event approved/rejected,
organizer approved/rejected, event reminder (24 h before). Written to `email_outbox` within the business transaction
and sent by a scheduled worker with retries — so an email failure never breaks a booking. In-app notifications in the
`notifications` table with a bell menu.

**Email channels (added 2026-11-05, user decision):**
- *Auth emails* — sign-up confirmation, **6-digit email OTP sign-in**, password reset — are sent by Supabase Auth
  through Resend custom SMTP (`smtp.resend.com:465`, user `resend`, password = a Resend API key). Branded templates
  live in `docs/email-templates/` and are pasted into Supabase → Authentication → Emails. The magic-link template
  shows `{{ .Token }}` so it works as an OTP code.
- *App emails* — welcome on first account creation now; booking confirmed, refunds, reminders, approvals later — are
  sent by the API through the Resend HTTP API (`RESEND_API_KEY`, `RESEND_FROM`) via the `email_outbox` worker:
  polled every 15 s, `FOR UPDATE SKIP LOCKED`, Resend `Idempotency-Key` = outbox id, retry with exponential backoff
  (1, 2, 4 … capped 60 min) up to 8 attempts; 4xx answers fail immediately. With no API key configured, emails stay
  queued (logged) instead of failing.
- Login page offers **Email code** (default) and **Password**. Email code creates the account if it does not exist.
- Development uses `@stagepass.test` identities created as auto-confirmed users in Supabase; real inboxes only when the
  user supplies one. Resend without a verified domain sends from `onboarding@resend.dev` to the account owner only.

## 9. Pages

**Public:** Home, Explore, Event details, Login, Register, Forgot password, Reset password, Organizer public profile,
and legal pages required for Razorpay live activation: Terms, Privacy, Refund & Cancellation, Contact/About (placeholder
copy for the company to finalize).
**Attendee:** Dashboard (upcoming tickets), My bookings (upcoming/past), Booking detail, Digital ticket, Profile/edit,
Saved events, Checkout (+ success/failure states), Become an organizer.
**Organizer:** Overview (KPIs: events, tickets sold, revenue, upcoming, recent bookings, sales chart), Events list,
Create/Edit event (multi-step: basics → date & venue → tickets → media → review/submit), Event detail with tabs
(sales analytics, bookings, attendees + CSV export, refunds, check-in stats), QR Scanner, Payouts/ledger view, Organization settings.
**Admin:** Overview (GMV, platform revenue, users, events, charts), Users (search, suspend, roles), Organizers
(applications approve/reject, suspend), Event approval queue (+ feature/unfeature), Transactions (payments, refunds,
disputes; CSV export of transactions for reconciliation), Reports (user-flagged event reports), Categories, Payouts (balance per organizer, record payout), Settings (fees), Audit log.

Role-gated route guards on the SPA mirror (but never replace) backend authorization.

## 10. Design system

- Typography: **Inter** for UI, **Bricolage Grotesque** (or similar display face) for headings; tight tracking on large display text.
- Tokens as CSS variables (shadcn convention) for light and dark: primary #6C4CF1, secondary #377DFF, accent #FF6B6B,
  background #F8F9FC (dark: near-black #0B0C10 with elevated #13141A surfaces), foreground #171923.
- Radius 12–16 px cards, subtle layered shadows in light, borders + glow in dark. Gradient primary→secondary used
  sparingly (hero, CTAs, featured badges).
- Motion: 150–250 ms ease-out, card hover lift, page fades, skeleton loaders; respects `prefers-reduced-motion`.
- Mobile-first: bottom tab bar for attendee on mobile; sticky "Book" bar on event page; organizer/admin use a
  collapsible sidebar.
- Imagery: Cloudinary-delivered event covers; seed data uses royalty-free event photos (Unsplash license).
- Accessibility: WCAG AA contrast, keyboard navigable, focus rings, aria labels on icon buttons.

## 11. Security and reliability

- JWT validation (resource server), role checks + ownership checks, `SUSPENDED` users blocked.
- Bean Validation on all DTOs; global `@RestControllerAdvice` → Problem Details; no stack traces leaked.
- Rate limiting with Bucket4j (in-memory; single instance at launch) on auth-adjacent, booking, verify, scan, and
  public search endpoints, keyed by user id or IP.
- Webhook: verify `X-Razorpay-Signature` against webhook secret on raw body; dedupe by event id.
- Overselling prevention: conditional atomic inventory updates + CHECK constraint as last line of defense.
- Transactions: `@Transactional` around booking creation, confirmation, refund finalization, check-in.
- Idempotency keys on booking creation; unique constraints on Razorpay ids.
- Audit logs on: role grants, organizer/event approvals, event cancel, refunds, payouts, setting changes, check-ins, user suspension.
- Secrets only via environment variables; `.env.example` files committed, real ones never.
- Security headers, strict CORS, HTTPS only (Render).
- Actuator health endpoint for Render health checks; structured JSON logs in prod.

## 12. Configuration

`application.properties` holds defaults with `${ENV_VAR}` placeholders; typed properties classes:
`StagepassProperties` (`stagepass.*`: web-base-url, cors origins, bootstrap admin emails, hold minutes, qr secret,
payout encryption key), `RazorpayProperties` (`razorpay.key-id`, `key-secret`, `webhook-secret`),
`CloudinaryProperties`, `ResendProperties`, `SupabaseProperties` (url, jwks-uri, issuer).
Frontend: `VITE_API_BASE_URL`, `VITE_SUPABASE_URL`, `VITE_SUPABASE_ANON_KEY`, `VITE_RAZORPAY_KEY_ID` (public), `VITE_GOOGLE_MAPS_KEY`.

## 13. Deployment (Render, Singapore)

- `render.yaml` blueprint: `stagepass-api` web service (Docker image built from a multi-stage Dockerfile:
  Maven build → `eclipse-temurin:17-jre`, runs the jar), health check `/actuator/health`;
  `stagepass-web` static site (`npm ci && npm run build`, publish `dist`, SPA rewrite `/* → /index.html`).
- API should run on a paid instance (free instances sleep, which would delay webhooks and first loads).
- Database: Supabase connection via its pooler (session mode) with Hikari pool sized small (≤10).
- Flyway runs migrations on startup.
- Razorpay webhook URL configured to `https://<api>/api/v1/webhooks/razorpay`.

## 14. Testing

- **Backend:** JUnit 5 + Spring Boot Test + MockMvc. Integration tests on a real Postgres via **embedded Postgres
  (zonky)** because Docker is not available on the dev machine. Razorpay HTTP stubbed with WireMock.
  Must-have tests: fee calculation, inventory concurrency (N threads racing for last tickets ⇒ never oversold),
  idempotent confirmation (verify + webhook + duplicate webhook ⇒ one confirmation), late payment refund,
  webhook signature rejection, QR forgery/duplicate scan, role/ownership authorization on every protected controller.
- **Frontend:** Vitest + Testing Library for components and hooks; Playwright smoke test of browse → checkout
  (Razorpay mocked) → ticket view.
- **CI:** GitHub Actions on push/PR.

## 15. Build order (each milestone works end-to-end before the next)

Before each milestone the builder explains what/why/how, files created, frontend↔backend contract, and how to test
(user's requirement).

1. Scaffolding: monorepo, Spring Boot app, Vite app, design system + theme, CI, `.env.example`s.
2. Schema (Flyway V1…) + Supabase auth integration + roles + `/me` + login/register/forgot pages + route guards.
3. Organizer onboarding + catalog: categories, venues, events, ticket types, images, submit for review; admin approval queue.
4. Discovery: home feeds, explore with search/filters/sort, event details page, maps, share page.
5. Booking + inventory + fees + Razorpay (orders, verify, webhooks, expiry, late payment).
6. Tickets: issuance, QR, ticket page, PDF, email outbox + Resend.
7. Organizer dashboard: KPIs, analytics, bookings, attendees, CSV, refunds.
8. QR check-in scanner.
9. Admin dashboard: users, organizers, transactions, disputes, reports, categories, payouts, settings, analytics, audit log.
10. Attendee profile, saved events, reviews, notifications, reminders.
11. Hardening: rate limits, performance (indexes, query review, lazy routes, image sizes), mobile polish, Render deploy.

## 16. What the user must provide (when we reach that milestone)

Supabase project (Mumbai) URL, anon key, DB connection string; Razorpay test key id/secret + webhook secret;
Cloudinary cloud name/key/secret; Resend API key + verified sending domain; Google Maps API key (Maps Embed + Places);
Render account; GitHub repo.

## 17. Left to the builder

Exact component styling details within the design system, endpoint naming beyond the patterns above, seed data
content, test file organization, specific chart types, scheduler cadence tuning, cache durations.

## 18. Assistant's calls (for the user to check)

- Spring Boot 4.1.2 GA instead of snapshot.
- Organizer accounts require admin approval.
- Fees: 2.5% + ₹10/ticket convenience fee + 18% GST on fee; 0% organizer commission default; all configurable.
- 10-minute seat hold.
- Convenience fee not refunded on attendee-requested refunds; refunded on event cancellation.
- Wallet passes deferred; seat maps deferred; no offline scanning.
- Embedded Postgres for tests (no Docker on this machine).
- Monorepo with both apps.
- Launch = working in Razorpay test mode; going live is a key swap plus Razorpay activation (legal pages included).
- Stagepass absorbs Razorpay's gateway fee; payouts become payable 3 days after event end; negative balances carry forward.
- In-policy attendee refunds are automatic; out-of-policy ones need organizer approval.
- Date/venue change on a published event → email to holders + 7-day full self-refund window; no admin re-review.
- GST tax invoice for the convenience fee; organizer PAN required, GSTIN optional; TDS rate configurable, default 0 —
  company's accountant to confirm.
- Admin "Reports" = user-flagged event reports; financial reporting limited to transactions CSV export at launch.
