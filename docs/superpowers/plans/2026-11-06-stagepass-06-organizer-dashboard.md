# Stagepass Organizer Dashboard Implementation Plan (Plan 6 of 11)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An approved organizer sees how their events are selling (KPIs, daily sales chart, recent bookings), manages each event's bookings, attendees (with CSV export) and refund requests, checks what Stagepass owes them (pending vs payable balance, ledger, payouts), and edits their organization profile and payout details.

**Architecture:** New backend module `organizer` with read-only query services over Plan 2–5 tables (`NamedParameterJdbcTemplate` SQL, like `discovery`), a CSV writer, a reusable `PayoutBalances` calculator (Plan 8's admin payouts screen uses it too) and an organization-profile service. Every endpoint resolves the organizer through `OrganizerGuard.requireApprovedOrganizerId()` and scopes each query by `organizer_id`. Web adds a real Overview, an event "Manage" page with tabs, a Payouts page and Organization settings; charts are hand-rolled SVG (no chart dependency).

**Tech Stack:** as Plans 1–5. No new dependencies.

**Spec:** `docs/superpowers/specs/2026-11-05-stagepass-design.md` — §9 Organizer pages, §8.4/8.4a (refunds, payout eligibility, negative balances), §5 `organizer` module, §7 data model (`organizer_ledger_entries`, `payouts`, `organizers`).

## Global Constraints

- All Plan 1–5 Global Constraints apply. All amounts are integer paise in the API; the web formats them.
- **Scope:** every organizer endpoint is under `/api/v1/organizer/**` (role `ORGANIZER`), resolves the organizer with `OrganizerGuard.requireApprovedOrganizerId()`, and answers **404** for an event that is not the organizer's (never 403, never another organizer's data).
- **Definitions** (used by every number on screen):
  - *Tickets sold* = tickets with status `VALID` or `CHECKED_IN` (refunded/cancelled excluded; pending checkouts never count).
  - *Gross sales* = Σ ledger `SALE`; *Refunded* = −Σ ledger `REFUND`; *Net sales* = Gross − Refunded; *Commission* = −Σ ledger `COMMISSION`; *Earnings* = Σ of all ledger entries except `PAYOUT`.
  - *Daily sales* = tickets and booking subtotals of bookings confirmed that day, bucketed by the **IST calendar date** of `bookings.confirmed_at`. A day shows what was sold that day; later refunds are not subtracted from past days (they show in Net sales). Zero-filled for days without sales; `days` ∈ {7, 30, 90}, default 30.
  - *Checked in* = tickets with status `CHECKED_IN` (populated by Plan 7's scanner).
- **Payout balances** (spec §8.4a): per event, *earned* = Σ ledger entries of that event except `PAYOUT`. An event's earnings are **payable** once `now ≥ events.ends_at + stagepass.payout.hold-days` (3) — and immediately for `CANCELLED` events; otherwise **pending**. `payable` = Σ payable earnings + Σ `PAYOUT` entries (negative) + Σ entries with no event; `pending` = Σ pending earnings; `paidOut` = −Σ `PAYOUT`; `balance` = payable + pending. A negative `payable` is returned as is (carried forward) and flagged `negative: true`.
- **Attendee CSV:** `text/csv; charset=UTF-8` with a UTF-8 BOM, CRLF line ends, RFC 4180 quoting; header row exactly `Ticket code,Ticket type,Holder name,Booking code,Email,Phone,Status,Booked at (IST),Checked in at (IST)`; one row per ticket of `CONFIRMED`/`PARTIALLY_REFUNDED`/`REFUNDED` bookings, ordered by booking time then ticket code; dates `dd-MM-yyyy HH:mm`; any cell starting with `=`, `+`, `-`, `@`, tab or CR is prefixed with `'` (formula-injection guard). Filename `attendees-{slug}-{yyyyMMdd}.csv` (IST date). Each export is audited `ATTENDEES_EXPORTED` with the row count.
- **Organization settings:** editable `bio` (≤ 1000), `logoUrl` (must pass the existing Cloudinary folder policy `stagepass/organizers/{userId}`), `website` (https URL), `contactEmail`, `contactPhone` (Indian mobile), `payoutAccountName`, `payoutAccountNumber` (write-only; stored with `PayoutCrypto.encrypt`, returned only as `PayoutCrypto.mask`), `payoutIfsc`. `orgName`, `slug`, `pan`, `gstin` are read-only here (changes go through admin, Plan 8). A payout-detail change is audited `PAYOUT_DETAILS_CHANGED` (masked values only).
- Web: amounts via `formatPaiseExact`; dates IST via `lib/datetime`; empty states for every list; the existing `ReasonDialog` for declines.

## Review Focus

1. **Another organizer's event id** in any URL (stats, bookings, attendees, CSV, refund list) → 404 with no data — tests in Tasks 1, 2.
2. **Hostile or messy attendee data in the CSV** (`=HYPERLINK(...)` as a name, commas, quotes, newlines, `Asha राव`) → one cell each, guarded, readable in Excel (BOM) — test in Task 2.
3. **Refunds and abandoned checkouts in the numbers** → partial refund lowers tickets sold and net sales; pending/expired bookings never count — test in Task 1.
4. **IST day boundary** → a sale confirmed at 23:30 IST lands on that IST date, not the UTC date — test in Task 1.
5. **Refund after payout** → payable goes negative, is shown as owed back (flag), never hidden — test in Task 3.

---

### Task 1: Overview KPIs and event sales stats

**Files:**
- Create in `com.stagepass.organizer`: `service/OrganizerAnalytics.java`, `service/DailySeries.java`, `api/OrganizerDashboardController.java`, `api/OverviewResponse.java`, `api/EventStatsResponse.java`
- Test: `organizer/OrganizerAnalyticsTest.java`

**Interfaces:**
- `GET /api/v1/organizer/overview?days=30` → `OverviewResponse(int liveEvents, int upcomingEvents, int ticketsSold, long grossSalesPaise, long netSalesPaise, long earningsPaise, List<RecentBooking> recentBookings /* 10 newest CONFIRMED/PARTIALLY_REFUNDED: id, code, eventTitle, attendeeName, ticketCount, totalPaise, confirmedAt */, List<DayPoint> dailySales)`; `record DayPoint(LocalDate date, int tickets, long amountPaise)` (amount = Σ booking subtotal).
- `GET /api/v1/organizer/events/{eventId}/stats?days=30` → `EventStatsResponse(int capacity, int ticketsSold, int held, int checkedIn, long grossSalesPaise, long refundedPaise, long netSalesPaise, long commissionPaise, List<TypeStats> byType /* ticketTypeId, name, pricePaise, total, sold, held, netSalesPaise */, List<DayPoint> dailySales)`.
- `DailySeries.fill(Map<LocalDate, DayPoint> found, LocalDate today, int days): List<DayPoint>` — oldest first, exactly `days` entries ending today (IST). `days` other than 7/30/90 → 400 `VALIDATION_FAILED` field `days`.

- [ ] **Step 1: Write failing tests**

```java
@Test void overviewCountsOnlyLiveTickets()       // 2 confirmed bookings (2 + 1 tickets) + 1 pending + 1 expired; refund 1 ticket → ticketsSold 2, grossSales 149700, netSales 99800
@Test void eventStatsByType()                    // two types; sold/held/total per type; capacity = Σ total; checkedIn counts CHECKED_IN tickets
@Test void dailySalesUseIstDates()               // confirmed_at 2026-11-10T18:00Z (23:30 IST) → point 2026-11-10; 2026-11-10T19:00Z → 2026-11-11; days=7 gives 7 points, zero-filled
@Test void otherOrganizersEventIs404()
@Test void unknownDaysValueIs400()
@Test void pendingOrganizerIs403()               // ORGANIZER_NOT_APPROVED
```

- [ ] **Step 2: Run — expect FAIL.** `cd stagepass-api && ./mvnw -q test -Dtest=OrganizerAnalyticsTest`
- [ ] **Step 3: Implement.** Inject `Clock` for "today"; SQL groups by `(b.confirmed_at at time zone 'Asia/Kolkata')::date`.
- [ ] **Step 4: Run — expect PASS**, then full suite.
- [ ] **Step 5: Commit** — `feat(organizer): overview KPIs and event sales stats`

---

### Task 2: Bookings, attendees and CSV export

**Files:**
- Create in `com.stagepass.organizer`: `service/OrganizerBookingQueries.java`, `service/CsvWriter.java`, `api/OrganizerEventDataController.java`, `api/OrganizerBookingRow.java`, `api/AttendeeRow.java`
- Test: `organizer/OrganizerEventDataTest.java`, `organizer/CsvWriterTest.java`

**Interfaces:**
- `GET /api/v1/organizer/events/{eventId}/bookings?status=&q=&page=&size=` → `PageResponse<OrganizerBookingRow(UUID id, String code, String status, String attendeeName, String attendeeEmail, String attendeePhone, int ticketCount, long totalPaise, long subtotalPaise, OffsetDateTime createdAt, OffsetDateTime confirmedAt)>`; default excludes `EXPIRED`; `status` one of the booking statuses; `q` matches code, name or email (escaped `ILIKE`, as in discovery); newest first.
- `GET /api/v1/organizer/events/{eventId}/attendees?q=&status=&page=&size=` → `PageResponse<AttendeeRow(UUID ticketId, String ticketCode, String ticketType, String holderName, String bookingCode, String attendeeEmail, String attendeePhone, String status, OffsetDateTime bookedAt, OffsetDateTime checkedInAt)>`; default statuses `VALID`, `CHECKED_IN`; `q` matches ticket code, booking code, holder or email.
- `GET /api/v1/organizer/events/{eventId}/attendees.csv` → per Global Constraints, written row by row to the response (`StreamingResponseBody`, fetch size 500), no pagination.
- `CsvWriter` (pure): `static String cell(String raw)` (guard + quoting), `static String row(List<String> cells)` (joined with `,`, ends `\r\n`).

- [ ] **Step 1: Write failing tests**

```java
// CsvWriterTest
@Test void quotesCommasQuotesAndNewlines()       // cell("Rao, Asha") == "\"Rao, Asha\""; cell("say \"hi\"") == "\"say \"\"hi\"\"\""; cell("a\nb") == "\"a\nb\""
@Test void guardsFormulaInjection()              // cell("=HYPERLINK(\"x\")") starts with "\"'="; cell("+91") == "'+91"; cell("-5") == "'-5"; cell("@x") == "'@x"
@Test void keepsUnicode()                        // cell("Asha राव") == "Asha राव"
// OrganizerEventDataTest
@Test void bookingsSearchAndFilter()             // q by code, by email (case-insensitive); status=PARTIALLY_REFUNDED; EXPIRED hidden by default
@Test void attendeesOneRowPerTicket()            // booking of 3 → 3 rows; refunded ticket hidden by default, shown with status=REFUNDED
@Test void csvHasBomHeaderAndGuardedCells()      // body starts with "﻿Ticket code,Ticket type,…"; holder "=1+1" exported as "'=1+1"; Content-Disposition attachment; filename attendees-{slug}-{yyyyMMdd}.csv; audit ATTENDEES_EXPORTED rows=3
@Test void otherOrganizersEventIs404()           // bookings, attendees and attendees.csv
@Test void exportsTwoThousandRows()              // 2000 tickets seeded via SQL → 2001 lines
```

- [ ] **Step 2: Run — expect FAIL.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run — expect PASS**, then full suite.
- [ ] **Step 5: Commit** — `feat(organizer): bookings, attendees and CSV export`

---

### Task 3: Payout balances, ledger and organization settings

**Files:**
- Create in `com.stagepass.organizer`: `service/PayoutBalances.java`, `service/OrganizationSettingsService.java`, `api/OrganizerLedgerController.java`, `api/LedgerResponse.java`, `api/OrganizationSettingsController.java`, `api/OrganizationSettingsRequest.java`, `api/OrganizationSettingsResponse.java`
- Test: `organizer/PayoutBalancesTest.java`, `organizer/OrganizationSettingsTest.java`

**Interfaces:**
- `PayoutBalances.forOrganizer(UUID organizerId): Balances` with `record Balances(long pendingPaise, long payablePaise, long paidOutPaise, long balancePaise, boolean negative, List<EventBalance> events /* eventId, title, endsAt, earnedPaise, payableFrom, payable */)` — rule in Global Constraints; uses `Clock` and `StagepassProperties.payout().holdDays()`. (Plan 8 admin payouts reuses this.)
- `GET /api/v1/organizer/ledger?page=&size=` → `LedgerResponse(Balances balances, PageResponse<LedgerLine(UUID id, String type, long amountPaise, UUID eventId, String eventTitle, String bookingCode, String note, OffsetDateTime createdAt)> entries, List<PayoutLine(UUID id, long amountPaise, String reference, OffsetDateTime paidAt, String note)> payouts)`; entries newest first.
- `GET /api/v1/organizer/profile` → `OrganizationSettingsResponse(String orgName, String slug, String bio, String logoUrl, String website, String contactEmail, String contactPhone, String pan, String gstin, String payoutAccountName, String payoutAccountMasked, String payoutIfsc)`; `PATCH /api/v1/organizer/profile` with `OrganizationSettingsRequest` (all fields optional; validation per Global Constraints; `payoutAccountNumber` replaces the stored one).

- [ ] **Step 1: Write failing tests**

```java
// PayoutBalancesTest (Clock fixed via a test bean or ledger rows inserted with chosen event end dates)
@Test void earningsBecomePayableThreeDaysAfterEventEnds()   // event ended 4 days ago: SALE 99800 → payable 99800; event ended 1 day ago: SALE 49900 → pending 49900; balance 149700
@Test void cancelledEventIsPayableAtOnce()                  // cancelled event with SALE + REFUND nets to 0, counted as payable
@Test void payoutsReducePayable()                          // payout −99800 → payable 0, paidOut 99800
@Test void refundAfterPayoutGoesNegative()                 // then REFUND −49900 on the paid event → payable −49900, negative true
@Test void ledgerListsEntriesNewestFirstAndOnlyOwn()       // GET /organizer/ledger: other organizer's entries absent
// OrganizationSettingsTest
@Test void updatesProfileAndMasksAccount()                 // PATCH bio/website/account → GET shows masked "XXXXXX1234", DB holds ciphertext, audit PAYOUT_DETAILS_CHANGED without the full number
@Test void rejectsForeignLogoAndBadValues()                // logo from another folder → 400 field logoUrl; website "ftp://x" → 400; phone "12345" → 400
@Test void readOnlyFieldsIgnored()                         // orgName/slug in body don't change
```

- [ ] **Step 2: Run — expect FAIL.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run — expect PASS**, then full suite.
- [ ] **Step 5: Commit** — `feat(organizer): payout balances, ledger and organization settings`

---

### Task 4: Web — Overview dashboard

**Files:**
- Create: `stagepass-web/src/features/organizer/dashboard/api.ts`, `dashboard/types.ts`, `dashboard/SalesChart.tsx`, `dashboard/KpiCard.tsx`, `dashboard/RangePicker.tsx`
- Modify: `features/organizer/OrganizerHomePage.tsx` (replace the placeholder stats)
- Test: `dashboard/SalesChart.test.tsx`, `organizer/OrganizerHomePage.test.tsx`

**Interfaces:**
- `useOverview(days)`, `useEventStats(eventId, days)` (TanStack Query keys `['organizer','overview',days]`, `['organizer','event-stats',eventId,days]`).
- `SalesChart({ points: DayPoint[], metric: 'amount' | 'tickets' })` — accessible SVG bar chart: `role="img"`, `aria-label` summarising the range total; each bar has a `<title>` "14 Nov: 12 tickets · ₹5,988"; y-axis max rounded up to a nice number; empty range shows "No sales in this period".
- `RangePicker` — segmented 7 / 30 / 90 days.
- Overview: KPI cards (Live events, Tickets sold, Net sales, Earnings), chart with metric toggle, recent bookings list (link to the event's Manage → Bookings tab), "Create event" button kept.

- [ ] **Step 1: Write failing tests**

```ts
test('chart labels each day and the range total')   // 3 points → 3 bars; aria-label contains "₹1,051.04 over 3 days"
test('chart shows an empty state')
test('overview shows KPIs and recent bookings')     // mocked useOverview → "Net sales ₹998", booking code links to /organizer/events/e1/manage?tab=bookings
test('range picker refetches with the new range')  // click 7 → useOverview called with 7
```

- [ ] **Step 2: Run — expect FAIL.** `cd stagepass-web && npx vitest run`
- [ ] **Step 3: Implement** (read the `dataviz` skill before writing the chart).
- [ ] **Step 4: Run — expect PASS**; lint; build.
- [ ] **Step 5: Commit** — `feat(web): organizer overview dashboard`

---

### Task 5: Web — Event Manage page (sales, bookings, attendees, refunds, check-in)

**Files:**
- Create: `stagepass-web/src/features/organizer/manage/EventManagePage.tsx`, `manage/SalesTab.tsx`, `manage/BookingsTab.tsx`, `manage/AttendeesTab.tsx`, `manage/RefundsTab.tsx`, `manage/CheckInTab.tsx`, `manage/api.ts`
- Modify: `src/app/router.tsx` (`/organizer/events/:id/manage`, declared before `events/:id/:step?`), `organizer/events/EventsListPage.tsx` ("Manage" link on published/completed/cancelled events), `bookings/downloads.ts` reused for the CSV
- Test: `manage/EventManagePage.test.tsx`, `manage/RefundsTab.test.tsx`, `manage/AttendeesTab.test.tsx`

**Interfaces:**
- Tabs driven by `?tab=sales|bookings|attendees|refunds|checkin` (default `sales`).
- Sales: KPI row from `useEventStats` (sold / capacity, net sales, refunded, commission), `SalesChart`, per-type table with a sold/total bar.
- Bookings / Attendees: search box (debounced 300 ms), status filter, paged table (20 per page), empty states; Attendees has "Export CSV" → `downloadFile('/organizer/events/{id}/attendees.csv', 'attendees.csv')`.
- Refunds: list from the Plan 4 endpoint `GET /organizer/events/{id}/refund-requests`; `PENDING_APPROVAL` rows have Approve (confirm dialog showing amount) and Decline (`ReasonDialog`, optional note); others show status and note.
- Check-in: checked in / tickets sold with a progress bar, "Open scanner" disabled with "Coming with the scanner" until Plan 7.

- [ ] **Step 1: Write failing tests**

```ts
test('tabs follow the URL')                            // ?tab=attendees renders AttendeesTab; clicking Refunds updates ?tab=refunds
test('attendee export downloads the CSV')              // downloadFile called with /organizer/events/e1/attendees.csv
test('approving a refund confirms the amount first')   // dialog shows ₹499.00; confirm → approve mutation with refund id; toast
test('declining sends the note')                       // ReasonDialog → decline({ id, note: 'Too close to the show' })
test('search is debounced and resets to page 0')
```

- [ ] **Step 2: Run — expect FAIL.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run — expect PASS**; lint; build.
- [ ] **Step 5: Commit** — `feat(web): event manage page with bookings, attendees and refunds`

---

### Task 6: Web — Payouts and Organization settings

**Files:**
- Create: `stagepass-web/src/features/organizer/payouts/PayoutsPage.tsx`, `payouts/api.ts`, `organizer/settings/OrganizationSettingsPage.tsx`, `settings/api.ts`, `settings/settingsSchema.ts`
- Modify: `organizer/OrganizerLayout.tsx` (nav: Overview, Events, Venues, Payouts, Settings, Scanner (soon)), `src/app/router.tsx` (`/organizer/payouts`, `/organizer/settings`)
- Test: `payouts/PayoutsPage.test.tsx`, `settings/OrganizationSettingsPage.test.tsx`

**Interfaces:**
- Payouts: three balance cards (Payable now, Pending, Paid out) with a coral "You owe Stagepass ₹X — it will be netted against your next payout" banner when `negative`; per-event table (earned, payable from date, status); ledger entries paged; payouts history. Copy: "Earnings become payable 3 days after each event ends. Payouts are sent by the Stagepass team."
- Settings: react-hook-form + zod (`settingsSchema` mirrors server rules); logo upload with the existing Cloudinary signed-upload component (folder `stagepass/organizers/{userId}`); payout account shows the masked number with "Change" revealing account number + IFSC fields; read-only org name, slug (with public profile link), PAN, GSTIN with "Contact support to change".

- [ ] **Step 1: Write failing tests**

```ts
test('shows balances and the negative-balance banner')
test('lists per-event payable dates and ledger entries')
test('settings saves only changed fields')            // edit bio → PATCH body { bio }
test('payout account change requires both fields')    // reveal Change, enter account without IFSC → inline error, no request
test('server field errors show inline')               // 400 field logoUrl → message under the logo field
```

- [ ] **Step 2: Run — expect FAIL.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run — expect PASS**; lint; build; full backend suite; browser check as the organizer (user signs in): Overview numbers match the event Manage page; CSV opens in Excel with correct columns; Payouts shows pending for an upcoming event.
- [ ] **Step 5: Commit** — `feat(web): organizer payouts and organization settings`

---

## Self-review notes

- Spec coverage (§9 Organizer): Overview with KPIs, upcoming, recent bookings, sales chart (T1, T4); Event detail tabs — sales analytics, bookings, attendees + CSV, refunds, check-in stats (T1, T2, T5); Payouts/ledger view (T3, T6); Organization settings (T3, T6). §8.4a payout eligibility and negative balances (T3). Events list, editor, venues already exist (Plan 2).
- Deferred by design: QR scanner, door staff and gates (Plan 7 — the Check-in tab only shows counts); admin payouts screen and recording payouts (Plan 8, reusing `PayoutBalances`); changing org name/PAN/GSTIN (admin, Plan 8); event cancellation UI already exists (Plan 2).
- Payout account changes are audited but not yet emailed to the organizer; the email arrives with Plan 9's notifications.
