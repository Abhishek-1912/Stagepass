# Stagepass Hardening & Render Deploy Implementation Plan (Plan 10 of 11)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Stagepass safe and quick enough to run in public, and ready to deploy on Render. That means:
- rate limits on the endpoints people can abuse;
- security headers;
- a much smaller first download;
- every foreign key indexed;
- the small reliability fixes left over from earlier reviews;
- a browser smoke test of the main journey, including on a phone-sized screen;
- a deploy runbook, so the user can put Stagepass on Render and check it step by step.

**Architecture:**
- **API rate limiting:** a servlet filter (`RateLimitFilter`) with in-memory Bucket4j buckets per (rule, key), where the key is the user id or the client IP. The buckets are held in a Caffeine cache, which fits the single instance the spec describes for launch (§11).
- **API headers:** set through Spring Security's `headers()` DSL.
- **Web:**
  - lazy-loads every area except the public pages (`React.lazy` + `Suspense`);
  - adds a bundle-size check to CI;
  - adds Playwright smoke tests that run against the Vite dev server with the API and Supabase stubbed in the browser, so CI needs no backend or real accounts.
- **Deploy:** a test keeps `render.yaml` in step with the API's required environment variables. A runbook (`docs/deploy.md`) covers what only the user can do: Render account, secrets, and the Supabase and Razorpay dashboards.

**Tech Stack:** as Plans 1–9, plus:
- `com.bucket4j:bucket4j_jdk17-core` **8.14.0** (API);
- `@playwright/test` **1.48.2** (web, dev only; Chromium only).

**Spec:** `docs/superpowers/specs/2026-11-05-stagepass-design.md`:
- §11 security and reliability (rate limits, headers, strict CORS, health check, JSON logs);
- §12 configuration;
- §13 deployment on Render (Singapore) — blueprint, Docker, pooler, Flyway on start, webhook URL;
- §14 testing (Playwright smoke: browse → checkout with Razorpay mocked → ticket view; CI);
- §15 milestone 11 ("Hardening: rate limits, performance, lazy routes, image sizes, mobile polish, Render deploy").

## Global Constraints

- All Plan 1–9 Global Constraints apply.
- **Rate limits** (`stagepass.rate-limits.*`; `enabled` defaults to true, and test properties set it to false):

  | Rule | Requests matched | Key | Limit |
  |---|---|---|---|
  | `booking` | `POST /api/v1/bookings` | user | 10 per minute |
  | `verify` | `POST /api/v1/payments/verify` | user | 20 per minute |
  | `refund` | `POST /api/v1/me/bookings/*/refunds` | user | 10 per minute |
  | `scan` | `POST /api/v1/checkin/scan` | user | 120 per minute |
  | `write` | `POST`/`PUT` to `/api/v1/events/*/reports`, `/api/v1/events/*/review`, `/api/v1/uploads/signature`, `/api/v1/organizer-applications` | user | 20 per minute |
  | `public` | `GET /api/v1/public/**`, `POST /api/v1/public/quote` | client IP | 120 per minute |

  - **Over the limit:** the response is 429 Problem Details with `code` `RATE_LIMITED`, detail "Too many requests. Please wait a moment and try again.", and a `Retry-After` header in whole seconds (at least 1).
  - **User key:** the JWT subject when a bearer token is present; otherwise the client IP.
  - **Client IP:** `request.getRemoteAddr()`. `server.forward-headers-strategy=framework` in prod already makes this the real client behind Render's proxy.
  - Webhooks, `/actuator/**` and admin endpoints are never limited.
  - The filter sits after authentication, so it can read the user id. On an unauthenticated public request it uses the IP.
  - **Bucket storage:** a Caffeine cache, at most 100,000 entries, each expiring after 10 minutes unused.
- **API security headers** (all API responses):
  - `X-Content-Type-Options: nosniff`
  - `X-Frame-Options: DENY`
  - `Referrer-Policy: strict-origin-when-cross-origin`
  - `Permissions-Policy: camera=(), microphone=(), geolocation=()`
  - `Strict-Transport-Security: max-age=31536000; includeSubDomains` (on secure requests)
  - `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'` on `/api/**`
  - The share page `/e/{slug}` gets `default-src 'none'; img-src https:; style-src 'unsafe-inline'; frame-ancestors 'none'`. It must keep working: it uses an `http-equiv` refresh, so it needs no script.
  - **CORS stays strict:** a preflight from an origin not in `CORS_ALLOWED_ORIGINS` gets 403.
- **Web static headers** (`render.yaml`):
  - the existing headers, plus `X-Content-Type-Options: nosniff` and `Permissions-Policy: camera=(self), microphone=(), geolocation=(self)` (the scanner needs the camera; "Near you" needs location);
  - `Content-Security-Policy-Report-Only` with `default-src 'self'`, and:
    - `script-src 'self' https://checkout.razorpay.com` plus the sha256 hash of the inline theme script in `index.html`;
    - `connect-src 'self'`, the API origin, the Supabase origin, `https://api.cloudinary.com`, `https://*.razorpay.com`;
    - `img-src 'self' data: blob: https://res.cloudinary.com https://*.googleusercontent.com`;
    - `frame-src https://api.razorpay.com https://checkout.razorpay.com https://www.google.com`;
    - `style-src 'self' 'unsafe-inline'`;
    - `font-src 'self' data:`.
  - Report-only for launch. Enforcing is a Plan 11 item, once real traffic shows no violations.
- **Bundle size:**
  - The initial JS chunk loaded by `/` must be at most **350 kB gzip**; `npm run check:bundle` fails above that.
  - Lazy-loaded areas: organizer, admin, scanner (`/scan`), checkout, account and bookings, become-organizer, and the auth pages other than login.
  - Each lazy route shows the existing `PageLoader` while it loads. A failed chunk load (after a deploy) shows "A new version of Stagepass is available" with a Reload button.
- **Foreign keys:** every foreign-key column must lead some index, enforced by a schema test. V16 adds whatever indexes are missing.
- **Prod connection pool** (`application-prod.properties`): Hikari `maximum-pool-size=5`, `minimum-idle=1`, `connection-timeout=10000`, `max-lifetime=1700000` (under Supabase's pooler limits).
- **Playwright** (`stagepass-web/e2e`), Chromium only:
  - It runs `vite` on port 5180 with `VITE_API_BASE_URL=http://api.test`, and `page.route` answers every `http://api.test/**` call from fixtures.
  - The signed-in state is a fake Supabase session placed in `localStorage` before the page loads (an access token with a future expiry).
  - Razorpay is stubbed by routing `https://checkout.razorpay.com/v1/checkout.js` to a tiny script that calls the success handler.
  - No real network calls are made. CI installs only Chromium.
- **Runbook:** `docs/deploy.md` is written for the user and gives exact field values, in this order:
  1. Supabase: the pooler (session mode) connection string, and the auth URL settings (Site URL and redirect URLs = the Render web URL).
  2. Render: create the account, then **New → Blueprint** pointed at the GitHub repo.
  3. Each secret, and where to find it.
  4. Razorpay webhook to `https://<api>/api/v1/webhooks/razorpay`, with the events list.
  5. Resend sender.
  6. A smoke checklist.
- **The user does every account step.** Claude never creates Render, Supabase, Razorpay or Resend accounts, never enters secrets, and never applies the blueprint.

## Review Focus

1. **Many people behind one office or campus Wi-Fi browsing the public site** share an IP. 120 requests a minute must still cover normal browsing by a group (home, explore and an event page make about 4–6 calls). Signed-in actions are keyed by user, not IP. Test in Task 1: two users on one IP each get their own booking limit.
2. **A user who hits a limit sees a clear message, not a broken page.** The web client turns 429 into a toast with the wait time. Test in Task 3.
3. **A deploy happens while someone has the app open.** Their next click loads a chunk that no longer exists; they get the "new version" message and a working reload, not a blank screen. Test in Task 3.
4. **The share page `/e/{slug}` still unfurls on WhatsApp and still redirects** after the CSP change. Test in Task 2: the share page's response has the og tags, a meta refresh, and its own CSP.
5. **Startup in prod with a required variable missing** fails fast, naming the variable, and `render.yaml` lists every required variable. Test in Task 5.

---

### Task 1: Rate limiting

**Files:**
- Modify: `stagepass-api/pom.xml` (+ `com.bucket4j:bucket4j_jdk17-core:8.14.0`), `common/config/StagepassProperties.java` (+ `RateLimits(boolean enabled)` record; the limits table is fixed in code), `src/test/resources/application-test.properties` `stagepass.rate-limits.enabled=false`
- Create: `common/ratelimit/RateLimitRule.java` (enum with matcher, key kind, capacity, period), `common/ratelimit/RateLimitFilter.java`, `common/ratelimit/RateLimiter.java` (Caffeine-backed buckets; `ConsumptionProbe tryConsume(RateLimitRule rule, String key)`)
- Modify: `common/security/SecurityConfig.java` (add the filter after the bearer-token filter)
- Test: `common/RateLimitTest.java` (with `@TestPropertySource(properties = "stagepass.rate-limits.enabled=true")`)

**Interfaces:**
- `RateLimitRule.match(HttpServletRequest): Optional<RateLimitRule>`.
- `RateLimiter.tryConsume(RateLimitRule, String key)`.
- Problem body through the existing `ProblemWriter` with `ErrorCode.RATE_LIMITED`.

- [ ] **Step 1: Write failing tests**

```java
@Test void eleventhBookingInAMinuteIs429WithRetryAfter()   // same user, 11 × POST /bookings (bodies may fail validation — the limiter runs first) → 11th 429, code RATE_LIMITED, Retry-After ≥ 1
@Test void usersOnOneIpHaveSeparateLimits()                // user A 10 bookings, user B's first → not 429
@Test void publicGetsAreLimitedByIp()                      // 121 × GET /public/categories from one remoteAddr → last 429; another remoteAddr → 200
@Test void webhooksHealthAndAdminAreNeverLimited()         // 200 × /actuator/health and 30 × POST /webhooks/razorpay (bad signature → 400, never 429)
@Test void scanAllowsABusyDoor()                           // 120 scans in a minute OK, 121st 429
```

- [ ] **Step 2: Run — expect FAIL.** `cd stagepass-api && ./mvnw -q test -Dtest=RateLimitTest`
- [ ] **Step 3: Implement.**
  - Buckets use `Bandwidth.builder().capacity(n).refillGreedy(n, period)`.
  - The filter skips everything when disabled.
  - For tests, the remote address is set with `request.setRemoteAddr` through a MockMvc `RequestPostProcessor`.
- [ ] **Step 4: Run — expect PASS**, then the full suite (still green with limits disabled).
- [ ] **Step 5: Commit** — `feat(api): rate limits on booking, payment, scan, writes and public endpoints`

### Task 2: Security headers and CORS

**Files:**
- Modify: `common/security/SecurityConfig.java` (`headers()` DSL; a path-specific CSP for `/e/**`, via a small `HeaderWriter` that picks the CSP by path), `discovery/service/SharePageRenderer.java` only if it currently uses an inline script (switch it to `<meta http-equiv="refresh">`)
- Test: `common/SecurityHeadersTest.java`

- [ ] **Step 1: Write failing tests**

```java
@Test void apiResponsesCarryHeaders()           // GET /api/v1/public/categories → nosniff, DENY, referrer policy, permissions policy, CSP "default-src 'none'; frame-ancestors 'none'"
@Test void hstsOnHttps()                        // secure request (request.setSecure(true)) → Strict-Transport-Security max-age=31536000; includeSubDomains
@Test void sharePageHasItsOwnCspAndStillWorks() // GET /e/{slug} → CSP with img-src https:, og:title present, meta refresh to WEB_BASE_URL/events/{slug}, no <script
@Test void unknownOriginPreflightIs403()        // OPTIONS with Origin https://evil.example → 403; allowed origin → 200 with Access-Control-Allow-Origin
```

- [ ] **Step 2: Run — expect FAIL.** `./mvnw -q test -Dtest=SecurityHeadersTest`
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run — expect PASS**, then the full suite.
- [ ] **Step 5: Commit** — `feat(api): security headers and share-page CSP`

### Task 3: Web — lazy routes, bundle budget, 429 and stale-chunk handling

**Files:**
- Modify: `stagepass-web/src/app/router.tsx` (`lazy()` for the areas in Global Constraints; public pages stay eager), `src/lib/api/client.ts` (a 429 becomes an `ApiError` whose detail includes the wait: "Too many requests. Try again in N seconds."), the app's query/mutation error handling (a global `QueryCache`/`MutationCache` `onError` toast for 429 only)
- Create: `src/app/LazyBoundary.tsx` (error boundary: a chunk-load error shows the "new version" panel with Reload; other errors rethrow to the existing error UI), `scripts/check-bundle.mjs` (reads `dist/.vite/manifest.json`, sums gzip sizes of the entry chunk and its static imports, fails above 350 kB), and `"check:bundle"` in `package.json`; `vite.config.ts` (`build.manifest: true`)
- Modify: `.github/workflows/ci.yml` (web job runs `npm run build && npm run check:bundle`)
- Test: `src/app/LazyBoundary.test.tsx`, `src/lib/api/client.test.ts` (429 case)

- [ ] **Step 1: Write failing tests**

```tsx
test('a failed chunk load offers a reload')            // child throws TypeError('Failed to fetch dynamically imported module') → "A new version of Stagepass is available" + Reload calls location.reload
test('other errors are not swallowed')                 // child throws Error('boom') → rethrown
test('429 becomes a friendly ApiError with the wait')  // fetch 429 with Retry-After: 12 → detail "Too many requests. Try again in 12 seconds."
```

- [ ] **Step 2: Run — expect FAIL.** `cd stagepass-web && npx vitest run src/app src/lib/api`
- [ ] **Step 3: Implement**, then `npm run build && npm run check:bundle`. Expect PASS at ≤ 350 kB gzip; record the before/after sizes in the ledger.
- [ ] **Step 4: Run** the full web suite, lint and tsc.
- [ ] **Step 5: Commit** — `perf(web): lazy-load app areas, bundle budget, friendly rate-limit and stale-chunk handling`

### Task 4: Leftover reliability fixes and foreign-key indexes

**Files:**
- Create: `db/migration/V16__fk_indexes.sql` (exactly the indexes the new schema test reports missing; list them in the ledger)
- Modify:
  - `identity/service/RoleLookupService.java` — `snapshot` loads through `cache.get(id, loader)` so a load can't overwrite a newer eviction; unknown users are not cached.
  - `admin/service/AdminPayoutService.java` — reference length checked after trimming (3–100) → 400 `reference`.
  - `booking/service/ReminderJob.java` — the claim re-checks the booking status and that a VALID ticket exists.
  - `engagement/service/SavedEventService.java` — the saved list leaves out events of suspended organizers; `available` uses the same sale-window filter as discovery.
  - `ticketing` `QrImagesTest` — decode with `DecodeHintType.TRY_HARDER` and `PURE_BARCODE`. If a token still fails to decode, raise `QrImages` quiet zone/size until 200 random tokens all decode.
- Test: `schema/SchemaConstraintsTest.java` (+ `everyForeignKeyIsIndexed`), `identity/RoleCacheRaceTest.java`, cases added to `AdminPayoutTest`, `ReminderJobTest`, `SavedEventTest`, and `QrImagesTest` (200 random tokens decode)

- [ ] **Step 1: Write failing tests**

```java
@Test void everyForeignKeyIsIndexed()            // pg_constraint contype='f' → for each, some index whose first column is the FK's first column; lists offenders
@Test void suspensionIsNotUndoneByARacingLoad()  // a load that started before the suspension commits can't re-cache ACTIVE (drive with a latch-controlled loader)
@Test void shortReferenceAfterTrimIs400()        // "  ab  " → 400 reference
@Test void refundedBetweenSelectAndClaimIsSkipped()
@Test void suspendedOrganizersEventsLeaveTheSavedList()
@Test void twoHundredRandomTokensDecode()
```

- [ ] **Step 2: Run — expect FAIL**, except the QR test, which may already pass. If it passes, ledger that and keep it as a guard.
- [ ] **Step 3: Implement**, writing V16 from the offenders list.
- [ ] **Step 4: Run — expect PASS**, then the full suite.
- [ ] **Step 5: Commit** — `fix(api): foreign-key indexes, role-cache race and leftover reliability fixes`

### Task 5: Deploy readiness — render.yaml, prod config, runbook

**Files:**
- Modify:
  - `render.yaml` — the web headers from Global Constraints. For the API: `COMPANY_NAME` and `COMPANY_ADDRESS` (`sync: false`), plus `SUPABASE_JWKS_URI`/`SUPABASE_ISSUER` left out (they're derived). For the web: `VITE_GOOGLE_MAPS_KEY` (`sync: false`, optional).
  - `application-prod.properties` — the Hikari values.
  - `README.md` — a link to the runbook.
- Create: `docs/deploy.md` (the runbook in Global Constraints; it ends with a "First deploy smoke checklist": health URL, home page, sign in by email code, create a test event as organizer, approve as admin, book a free ticket, view the QR, scan it at `/scan`).
- Test: `config/RenderBlueprintTest.java` and `config/ProdStartupTest.java`.

- [ ] **Step 1: Write failing tests**

```java
@Test void everyRequiredVariableIsInTheBlueprint()  // parse ${NAME} without a default in application*.properties (main + prod) → each appears as a key under the stagepass-api service in render.yaml
@Test void blueprintKeepsSecretsOutOfGit()          // every key except SPRING_PROFILES_ACTIVE has sync: false or generateValue: true
@Test void prodProfileFailsFastWithoutDatabaseUrl() // SpringApplication run with profile prod and DB_URL unset → startup fails, message names DB_URL
```

- [ ] **Step 2: Run — expect FAIL**, at least on the new keys.
- [ ] **Step 3: Implement.** Use SnakeYAML (Spring Boot brings it in through spring-boot-starter; check with `./mvnw dependency:tree`) to parse `render.yaml`.
- [ ] **Step 4: Run — expect PASS**, then the full suite.
- [ ] **Step 5: Commit** — `chore(deploy): blueprint, prod pool and deploy runbook`

### Task 6: Playwright smoke tests — journey and phone-size pages

**Files:**
- Modify: `stagepass-web/package.json` (+ `@playwright/test@1.48.2`, a `"test:e2e"` script), `.github/workflows/ci.yml` (web job: `npx playwright install --with-deps chromium` then `npm run test:e2e`), `.gitignore` (`playwright-report/`, `test-results/`)
- Create: `stagepass-web/playwright.config.ts` (webServer `vite --port 5180` with the env in Global Constraints; projects `desktop` 1280×800 and `phone` 375×812), `e2e/fixtures/api.ts` (route handlers + JSON fixtures: home, event detail, quote, booking create, payment verify, booking detail with tickets), `e2e/fixtures/session.ts` (fake Supabase session in localStorage), `e2e/journey.spec.ts`, `e2e/mobile.spec.ts`

- [ ] **Step 1: Write the specs**

```ts
test('browse → event → checkout → ticket')   // home shows event card → open event → pick 1 ticket → Checkout → (signed in) attendee details → Pay (stubbed Razorpay success) → booking confirmed page → open ticket → QR canvas visible
test('pages fit a phone screen')             // phone project: '/', '/explore', '/events/{slug}', '/account', '/account/bookings' → document.documentElement.scrollWidth <= innerWidth; mobile tab bar visible
test('signed-out checkout asks to sign in')  // Checkout → /login?next=…
```

- [ ] **Step 2: Run — expect FAIL**, then fix fixtures and any real bug found (each real bug gets a unit test where possible). `npx playwright install chromium && npm run test:e2e`
- [ ] **Step 3: Run — expect PASS** on both projects. Also run the full unit suite, lint, tsc, build and check:bundle.
- [ ] **Step 4: Commit** — `test(web): Playwright smoke tests for the booking journey and phone layouts`

---

## Rulings to confirm with the user (assistant's calls)

1. **Rate limits:** the numbers in the table. Public browsing is 120 requests a minute per IP; booking is 10 a minute per user; the scanner is 120 a minute per door person. They can be turned off with one setting, and the numbers are easy to change.
2. **Web CSP:** it starts in **report-only** mode, so a mistake can't break checkout on launch day. Enforcing it moves to Plan 11.
3. **Playwright:** it runs with the API and sign-in faked in the browser, so CI needs no secrets or live services. Real end-to-end checks against the deployed site are the runbook's smoke checklist, which you do once Render is up.
4. **You create the accounts and enter the secrets** (Render, Supabase settings, Razorpay webhook, Resend). I write the exact steps and check the result with you afterwards.
