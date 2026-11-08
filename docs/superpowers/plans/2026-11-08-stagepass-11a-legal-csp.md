# Stagepass Legal Pages & Enforced CSP Implementation Plan (Plan 11a — the no-cost part of Plan 11)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the live site ready for Razorpay's activation review and safer to run, at no cost:
- the footer's About, Terms, Privacy and Refunds links lead to real pages (today they 404 on the live site), showing the company's details;
- the website's Content Security Policy becomes **enforced**, with violation reports sent to the API's logs, after a browser test proves the production build works under it;
- leftover cleanups.

**Architecture:**
- **API:** a small public endpoint gives the company's name, address, GSTIN, state, email and phone from `CompanyProperties`, so the legal pages show the same identity as GST invoices (one place to change). A public CSP-report endpoint logs browser violation reports.
- **Web:** legal pages are TSX content in a new `features/legal` area, lazy-loaded like other non-core pages.
- **CSP:** the enforced policy lives in `render.yaml`. A Playwright test serves the **production build** (`vite preview`), adds that exact header to each page, and fails on any CSP violation — so the policy is checked against real built code before it reaches users.

**Tech Stack:** as Plans 1–10; no new libraries.

**Spec:** `docs/superpowers/specs/2026-11-05-stagepass-design.md`:
- §9 Public pages: "legal pages required for Razorpay live activation: Terms, Privacy, Refund & Cancellation, Contact/About (placeholder copy for the company to finalize)";
- §8.4 and §8.4a refund rules (the Refunds page must state these exactly);
- §11 security headers;
- §18 "Launch = working in Razorpay test mode; going live is a key swap plus Razorpay activation (legal pages included)".
- Plan 10's ruling: the web CSP started report-only; enforce it in Plan 11 with a reporting endpoint.

## Global Constraints

- All Plan 1–10 Global Constraints apply.
- **Company endpoint:** `GET /api/v1/public/company` returns `CompanyInfo(name, address, gstin, state, email, phone)`.
  - Values come from `CompanyProperties`, which gains `email` and `phone` (env `COMPANY_EMAIL`, `COMPANY_PHONE`, both optional).
  - Blank values are returned as `null`. `name` defaults to "Stagepass".
  - Public, cacheable: `Cache-Control: public, max-age=300`.
- **Legal pages** (public routes, inside `PublicLayout`, lazy-loaded):
  - `/about` — "About & contact": what Stagepass is, and a contact card (company name, address, email, phone, GSTIN) from the endpoint; missing fields are left out, never shown as "null".
  - `/legal/terms` — Terms of use.
  - `/legal/privacy` — Privacy policy.
  - `/legal/refunds` — Refund & cancellation policy.
  - Each legal page has an `<h1>`, a "Last updated: 8 November 2026" line, numbered sections with `<h2>` headings, and the company name from the endpoint (falling back to "Stagepass" while loading or on error).
  - **No "draft" banner on the pages** (Razorpay reviews the live site). The repo's `docs/legal-review.md` tells the company the text must be checked by their legal advisor before live payments.
- **Refunds page must state the app's real rules** (spec §8.4, §8.4a):
  1. If an event is cancelled, every ticket is refunded in full, convenience fee and its GST included, automatically.
  2. If the date, time or venue changes, ticket holders can cancel for a full refund (fee included) for 7 days after being told.
  3. Otherwise the event's own refund policy applies: no refunds, or refunds until a set number of hours before start. The event page shows which.
  4. Within the policy, refunds are automatic and return the ticket price; the convenience fee and its GST are kept.
  5. Outside the policy, the request goes to the organizer, who may approve or decline.
  6. Refunds reach the original payment method, usually within 5–7 working days.
  7. Used (checked-in) tickets can't be refunded.
- **Privacy page must state what the app actually stores:** account (name, email, phone, city, profile photo), bookings and tickets, payments (handled by Razorpay; Stagepass never sees card or UPI details), organizer applications (PAN, GSTIN, bank account number — stored encrypted), check-in records, emails sent; processors: Supabase (database and sign-in), Razorpay (payments), Cloudinary (images), Resend (email), Render (hosting); contact for privacy requests = company email.
- **CSP reports:** `POST /api/v1/csp-reports` accepts `application/csp-report` and `application/reports+json` (and `application/json`), body capped at 8 KB (larger → 413), always answers 204, and logs one WARN line per report with `blocked-uri`, `violated-directive` (or `effectiveDirective`) and `document-uri`, each cut to 200 characters. It is public, never 401, and rate-limited by the existing `PUBLIC`-style IP rule (add rule `CSP_REPORT`: 30/min per IP, user and anonymous alike).
- **Enforced web CSP** (`render.yaml`, header `Content-Security-Policy`, replacing the report-only header):
  `default-src 'self'; script-src 'self' https://checkout.razorpay.com 'sha256-…'` (the existing hash);
  `connect-src 'self' https://*.onrender.com https://*.supabase.co wss://*.supabase.co https://api.cloudinary.com https://*.razorpay.com`;
  `img-src 'self' data: blob: https://res.cloudinary.com https://images.unsplash.com https://*.googleusercontent.com https://*.razorpay.com`;
  `frame-src https://api.razorpay.com https://checkout.razorpay.com https://www.google.com`;
  `style-src 'self' 'unsafe-inline'; font-src 'self' data:; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'`;
  `report-uri https://stagepass-api-4mbc.onrender.com/api/v1/csp-reports`.
  The web build must contain no `eval(` or `new Function(` (checked in CI).
- **Payments note:** Razorpay Checkout is not active yet. `docs/deploy.md` gets a line: before turning on live payments, run one test payment on the live site and check the API logs for CSP reports.

## Review Focus

1. **A visitor opens a legal page when the API is asleep (free plan) or down.** The page still shows its full text immediately, with "Stagepass" as the name, and fills in the company details when they arrive. Test in Task 2.
2. **Company settings partly filled in** (no phone, no GSTIN). The contact card shows only what exists, with no empty labels or "null". Test in Task 2.
3. **A browser sends a huge or malformed CSP report** (or a bot floods the endpoint). Answers 413 or 204, never 500; floods hit 429; logs stay one short line each. Tests in Task 1.
4. **The enforced CSP blocks something real** (a home-page banner image, the theme script, Supabase sign-in, an event image, the QR on a ticket). The production-build browser test visits home, explore, an event page, the legal pages and a ticket under the exact enforced header and fails on any violation. Test in Task 3.
5. **Someone edits the inline theme script in `index.html` and forgets the CSP hash.** CI fails, naming the hash to use. Test in Task 3.

---

### Task 1: API — company details and CSP reports

**Files:**
- Modify: `common/config/CompanyProperties.java` (+ `email`, `phone`), `application.properties` (`stagepass.company.email=${COMPANY_EMAIL:}`, `stagepass.company.phone=${COMPANY_PHONE:}`), `common/security/SecurityConfig.java` (permit `POST /api/v1/csp-reports`), `common/ratelimit/RateLimitRule.java` (+ `CSP_REPORT`), `render.yaml` (API: `COMPANY_EMAIL`, `COMPANY_PHONE` with `sync: false`)
- Create: `discovery/api/PublicCompanyController.java`, `discovery/api/CompanyInfo.java`, `common/csp/CspReportController.java`
- Test: `discovery/PublicCompanyTest.java`, `common/CspReportTest.java`; update `config/RenderBlueprintTest` expectations if needed

**Interfaces:**
- `CompanyInfo(String name, String address, String gstin, String state, String email, String phone)`.
- `CspReportController.receive(HttpServletRequest)`: reads at most 8 KB of the body as text, extracts fields with Jackson tree parsing (both the `csp-report` object and the Reporting API array form), logs, returns 204.

- [ ] **Step 1: Write failing tests**

```java
// PublicCompanyTest
@Test void companyDetailsArePublicAndBlanksAreNull()   // no auth → 200; name/gstin/state from test properties; email/phone null when unset; Cache-Control "public, max-age=300"
// CspReportTest
@Test void legacyReportIsLoggedAndAnswered204()        // application/csp-report {"csp-report":{"blocked-uri":"https://evil.example/x.js","violated-directive":"script-src",...}} → 204; log line contains both values (OutputCaptureExtension)
@Test void reportingApiArrayIsAccepted()               // application/reports+json [{"type":"csp-violation","body":{"blockedURL":"…","effectiveDirective":"img-src"}}] → 204
@Test void garbageAndHugeBodies()                      // "not json" → 204; 9 KB body → 413; never 500
@Test void floodsAreRateLimited()                      // @TestPropertySource rate limits on: 31st report in a minute from one IP → 429
```

- [ ] **Step 2: Run — expect FAIL.** `cd stagepass-api && ./mvnw -q test -Dmaven.compiler.useIncrementalCompilation=false -Dtest='PublicCompanyTest,CspReportTest'`
- [ ] **Step 3: Implement.** Long fields are cut to 200 characters before logging; newlines are replaced so one report stays one log line.
- [ ] **Step 4: Run — expect PASS**, then the full suite.
- [ ] **Step 5: Commit** — `feat(api): public company details and CSP report endpoint`

### Task 2: Web — About, Terms, Privacy and Refunds pages

**Files:**
- Create in `stagepass-web/src/features/legal/`: `api.ts` (`useCompany()` → `CompanyInfo`, `staleTime` 1 hour, `retry: 1`), `LegalPage.tsx` (shared layout: title, last-updated line, a table of contents built from the sections, readable prose width), `AboutPage.tsx`, `TermsPage.tsx`, `PrivacyPage.tsx`, `RefundsPage.tsx`, `CompanyContact.tsx`
- Modify: `src/app/router.tsx` (the four routes, lazy, under `PublicLayout`), `docs/legal-review.md` (new: what each page says, what the company's advisor must confirm — especially GST, TDS, grievance officer details and jurisdiction)
- Test: `features/legal/LegalPages.test.tsx`

**Interfaces:**
- `LegalPage({ title: string, updated: string, sections: { id: string; heading: string; body: ReactNode }[] })`.
- `CompanyContact({ company: CompanyInfo | undefined })`.

- [ ] **Step 1: Write failing tests**

```tsx
test('each footer legal link opens a page with a heading')   // /about, /legal/terms, /legal/privacy, /legal/refunds → h1 "About & contact" / "Terms of use" / "Privacy policy" / "Refund & cancellation policy"; "Last updated: 8 November 2026"
test('pages show their text before company details load')    // useCompany pending → full text visible, name "Stagepass"
test('contact card shows only filled-in details')            // company {name:'Acme Events Pvt Ltd', address:'…', gstin:null, email:'help@acme.test', phone:null} → name, address, email shown; no "GSTIN", no "Phone", no "null"
test('refund page states the real rules')                    // contains "refunded in full", "7 days", "convenience fee", "5–7 working days", "checked-in" (or "used") tickets can't be refunded
test('privacy page names every processor')                   // Supabase, Razorpay, Cloudinary, Resend, Render; "encrypted" for bank details
```

- [ ] **Step 2: Run — expect FAIL.** `cd stagepass-web && npx vitest run src/features/legal`
- [ ] **Step 3: Implement.** Plain, short sentences; Indian English; ₹ amounts only where the rules need them.
- [ ] **Step 4: Run — expect PASS**; full web suite, lint, tsc, build and `check:bundle`.
- [ ] **Step 5: Commit** — `feat(web): about, terms, privacy and refund pages`

### Task 3: Enforce the website CSP, proven against the production build

**Files:**
- Modify: `render.yaml` (replace the report-only header with the enforced header in Global Constraints), `stagepass-web/playwright.config.ts` (second `webServer`: `npm run build && npx vite preview --port 5181 --strictPort`, and a `csp` project with `baseURL` `http://localhost:5181`), `docs/deploy.md` (CSP section: where reports appear, the test-payment check before live payments), `.github/workflows/ci.yml` (the web job's existing build step keeps working)
- Create: `stagepass-web/e2e/csp.spec.ts`, `stagepass-web/e2e/fixtures/csp.ts` (reads the `Content-Security-Policy` value for `stagepass-web` from `../render.yaml`; drops `report-uri` for the test), `stagepass-web/scripts/check-csp.mjs` (fails when `dist/**/*.js` contains `eval(` or `new Function(`, or when the sha256 of `index.html`'s inline script isn't in `render.yaml`'s CSP — printing the right hash) and `"check:csp"` in `package.json`, run in CI after `check:bundle`

- [ ] **Step 1: Write the failing checks**

```ts
// csp.spec.ts (project "csp" only): fakeApi from e2e/fixtures/api.ts; page.route on the preview origin's documents adds the enforced CSP header
test('the production build runs under the enforced CSP')   // visit '/', '/explore', '/events/{slug}', '/about', '/legal/refunds', and a ticket page (signed in) → collect 'securitypolicyviolation' events and console "Refused to" messages → expect none; the theme script ran: with localStorage `stagepass-theme` = `dark` set before load, `<html>` has class `dark`
```

```bash
# check:csp — before the render.yaml change: fails, because the header still says Report-Only or images.unsplash.com is missing
```

- [ ] **Step 2: Run — expect FAIL.** `npm run build && npm run check:csp` and, in CI, the new Playwright project. (Playwright can't launch a browser on this machine; push the branch and read CI annotations, as in Plan 10.)
- [ ] **Step 3: Implement** the enforced header and the scripts.
- [ ] **Step 4: Run — expect PASS:** `check:csp` locally; all Playwright projects green in CI.
- [ ] **Step 5: Commit** — `feat(web): enforce the content security policy, checked against the production build`

### Task 4: Cleanups

**Files:**
- Modify: `stagepass-web/vite.config.ts` (remove the triple-slash `vitest/config` reference; the `import` already provides the types), `.claude/launch.json` (commit the Windows-friendly server commands), `README.md` (one line: legal pages and `docs/legal-review.md`)

- [ ] **Step 1:** `npm run lint` shows the triple-slash warning.
- [ ] **Step 2:** Make the changes.
- [ ] **Step 3:** `npm run lint` clean; `npx tsc -b`; `npx vitest run`; `npm run build`.
- [ ] **Step 4: Commit** — `chore: tidy vite config, commit local launch settings`

---

## Rulings to confirm with the user (assistant's calls)

1. **Legal text is a sensible starting point, not legal advice.** It describes how Stagepass actually works; `docs/legal-review.md` lists what your legal advisor must confirm before live payments. The pages carry no "draft" label, because Razorpay reviews the live site.
2. **Company contact details come from settings** (`COMPANY_*` on Render), so changing an address or email needs no code change. Add `COMPANY_EMAIL` and `COMPANY_PHONE` on Render when you're ready; until then those lines are hidden.
3. **The CSP is switched to enforced now**, but only after a browser test proves the built site runs under it, and with reports going to the API logs so anything missed shows up. Razorpay Checkout gets checked with one live test payment before payments go on.
