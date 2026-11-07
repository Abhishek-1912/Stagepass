# Stagepass Admin Dashboard Implementation Plan (Plan 8 of 11)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A Stagepass admin can run the platform from one place. That means:
- see GMV, platform revenue and what needs attention today;
- find and suspend users, and grant or revoke admin;
- manage organizers, including suspending them, fixing their legal details and seeing what is owed;
- cancel any event;
- look up payments, refunds and disputes, refund a booking and export transactions;
- record payouts;
- change fees;
- handle events reported by attendees;
- read the audit log.

**Architecture:**
- **Backend:** all new endpoints live under `/api/v1/admin/**` (already `hasRole("ADMIN")`) in the existing `admin` module. Each area gets one small service:
  - `AdminUserService`
  - `AdminOrganizerService` (extended)
  - `AdminTransactionQueries`
  - `AdminRefundService`
  - `AdminPayoutService`
  - `PlatformSettingsService`
  - `EventReportService`
  - `AuditLogQueries`
  - `PlatformAnalytics`
- **Reuse:** the services reuse what earlier plans built rather than copying it: `PayoutBalances`, `RefundService`, `EventLifecycleService`, `FeeSettingsRepository`, `CsvWriter`, `DailySeries`, `AuditLogService`, `RoleLookupService.evictAfterCommit`.
- **Reads:** read-heavy screens use `NamedParameterJdbcTemplate` queries that return page records, following the organizer module's pattern.
- **Web:** fills in the admin area's placeholder pages. It also adds the attendee's "Report this event" dialog, because admin Reports needs a way for reports to come in.

**Tech Stack:** as Plans 1–7; no new libraries.

**Spec:** `docs/superpowers/specs/2026-11-05-stagepass-design.md` — the sections this plan implements:
- §2 Goals (admin control)
- §6 roles
- §7 tables `users`, `organizers`, `payouts`, `platform_settings`, `audit_logs`, `event_reports`, `disputes`
- §8.1 cancel by admin
- §8.2 fees
- §8.4 refunds and disputes
- §8.4a payout eligibility and negative balances
- §9 Admin pages
- §11 audit list
- §18 "Admin Reports = user-flagged event reports; financial reporting limited to transactions CSV export"

## Global Constraints

- All Plan 1–7 Global Constraints apply. Money is in integer paise. Shown times are in IST. Errors are RFC 9457 with `code`; field errors come from `FieldErrors`.
- **Lists:** every admin list is paged with `PageRequests.of(page, size, …)` (default 20, max 100) and returns `PageResponse<T>`.
  - **Search `q`:** trimmed and case-insensitive. It matches through `LikePatterns`, so `%` and `_` are literal characters.
  - **Unknown filter value:** 400 with the field error on that parameter.
- **Users:** `GET /admin/users?q&role&status` returns `AdminUserRow(id, email, fullName, roles[], status, createdAt, suspendedAt, suspensionReason)`.
  - `POST /admin/users/{id}/suspend {reason 3–500}`:
    - sets `SUSPENDED`, `suspended_at` and `suspension_reason`;
    - 409 `INVALID_STATE` "You can't suspend your own account" for self, and "Remove admin from this account first" for another admin.
  - `POST /admin/users/{id}/reactivate` sets `ACTIVE` and clears both fields.
  - `PUT /admin/users/{id}/roles/ADMIN` grants admin. `DELETE …/roles/ADMIN` revokes it, with 409 "You can't remove your own admin access" for self.
  - Only `ADMIN` can be granted or revoked here. `ORGANIZER` comes only from approving an organizer application (400 field `role`).
  - Every change calls `RoleLookupService.evictAfterCommit(userId)`. Suspension therefore takes effect within one request, not after 60 s.
  - Audit actions: `USER_SUSPENDED`, `USER_REACTIVATED`, `ROLE_GRANTED`, `ROLE_REVOKED`.
- **Organizers:** `GET /admin/organizers?q&status` (q matches org name, contact email or slug) returns `AdminOrganizerRow(id, orgName, slug, contactEmail, status, eventsCount, payablePaise, negative, appliedAt)`.
  - `GET /admin/organizers/{id}` returns `AdminOrganizerDetail`: the application fields, `pan`, `gstin`, masked account, `commissionBpsOverride`, `Balances` from `PayoutBalances.forOrganizer`, and the last 20 payouts.
  - `POST …/{id}/suspend {reason}` works only from `APPROVED`; `POST …/{id}/reinstate` only from `SUSPENDED`. Either way the user's ORGANIZER role is kept.
  - `PATCH …/{id} {orgName?, pan?, gstin?, commissionBpsOverride?}`:
    - PAN must match `[A-Z]{5}[0-9]{4}[A-Z]`;
    - GSTIN uses the same `Formats` check as the organizer application, and an empty string clears it;
    - `commissionBpsOverride` is 0–5000 or null (null means use the platform default);
    - audit action `ORGANIZER_UPDATED`, with before and after holding only the changed fields.
  - Audit actions for suspend and reinstate: `ORGANIZER_SUSPENDED` and `ORGANIZER_REINSTATED`.
- **What suspension of an organizer means (ruling for the deferred Plan 7 item):**
  - Their dashboard is blocked (already: `OrganizerGuard` needs APPROVED).
  - Their events are hidden from discovery and cannot be booked (already: `PublicEventService` and `BookingRules`).
  - Refunds and webhooks keep working.
  - **Check-in keeps working for the owner and their door staff**, so attendees who already paid still get in. `ScanAccess` treats the owner as `status in ('APPROVED','SUSPENDED')`.
  - To stop an event, the admin cancels it, which refunds every ticket.
- **Admin cancel:** `POST /admin/events/{id}/cancel {reason 3–500}` goes through `EventLifecycleService.adminCancel(adminId, eventId, reason)`.
  - It runs the same transition, handlers (refunds and emails) and audit `EVENT_CANCELLED` as the organizer's cancel, except that it adds `"byAdmin": true` to the audit.
  - It works from `DRAFT`, `PENDING_REVIEW` or `PUBLISHED`; anything else is 409.
  - `GET /admin/events` also accepts `q` (title or organizer name).
- **Transactions:** times are in IST, and `from`/`to` are inclusive dates; `to < from` or a span over 366 days is 400.
  - `GET /admin/payments?q&status&from&to`:
    - q matches the booking code, attendee email, `razorpay_payment_id` or `razorpay_order_id`;
    - returns `AdminPaymentRow(id, bookingId, bookingCode, eventTitle, attendeeEmail, amountPaise, status, method, razorpayPaymentId, gatewayFeePaise, createdAt)`.
  - `GET /admin/refunds?status&q` returns `AdminRefundRow(id, bookingCode, eventTitle, kind, status, amountPaise, attempts, needsRetry, failureReason, createdAt)`.
  - `GET /admin/disputes?status` returns `AdminDisputeRow(id, razorpayDisputeId, bookingCode, eventTitle, amountPaise, reason, status, createdAt)`.
  - `GET /admin/payments/export?from&to&status` returns `text/csv`:
    - UTF-8 BOM, `CsvWriter.cell` on every cell, fetch size 500;
    - audit `TRANSACTIONS_EXPORTED` (with from, to and row count) is written in REQUIRES_NEW before streaming;
    - filename `stagepass-payments-{from}-to-{to}.csv`;
    - columns: `Date (IST)`, `Booking code`, `Event`, `Attendee email`, `Amount (Rs.)`, `Status`, `Method`, `Razorpay payment id`, `Razorpay order id`, `Gateway fee (Rs.)`, `Gateway tax (Rs.)`, `Refunded (Rs.)`.
    - **Amounts** are rupees with exactly two decimals and no symbol, e.g. `1234.50`.
- **Admin refund:** `POST /admin/bookings/{id}/refund {ticketIds?, includeFee, reason 3–500}`.
  - It refunds the chosen live, unclaimed tickets, or all of them when `ticketIds` is empty. It uses kind `ADMIN`, includes the fee as `includeFee` says, and refunds no matter the event's policy.
  - It goes through `RefundService.adminRefund`, which uses the same `apply`/`submit` path as organizer approval.
  - Errors come from the existing `pick`. When nothing is refundable: 409. When a ticket is not on this booking, or is checked in, refunded or claimed: 400 field `ticketIds[i]`.
  - `POST /admin/refunds/{id}/retry` works only for `FAILED` with `needs_retry`, otherwise 409. It reuses `RefundService.retry`.
  - Audit actions: `REFUND_ISSUED` (with `byAdmin`) and `REFUND_RETRIED`.
- **Payouts:** `GET /admin/payouts?q&onlyPayable` lists organizers with `Balances` (pending, payable, negative), payable first. `onlyPayable` means payable > 0.
  - `POST /admin/organizers/{id}/payouts {amountPaise, reference 3–100, paidOn (date), note? ≤500}`:
    - `amountPaise` must be > 0 and ≤ the payable balance at that moment, otherwise 400 `amountPaise` "is more than the payable balance (Rs. X)";
    - `paidOn` must not be in the future (IST);
    - the reference must be unique per organizer, case-insensitive; otherwise 400 `reference` "was already recorded";
    - one transaction locks the organizer row (`select … for update`), re-reads the balance, and inserts `payouts` (`paid_at` = `paidOn` 12:00 IST) plus a ledger `PAYOUT` entry of `-amountPaise` with note `Payout {reference}`;
    - it queues email `PAYOUT_RECORDED` to the contact email (dedupe `payout-recorded:{payoutId}`), with subject `Stagepass payout of Rs. {amount} sent` and the reference;
    - audit `PAYOUT_RECORDED`.
- **Fee settings:** `GET /admin/settings/fees` and `PUT /admin/settings/fees {convenienceFeeBps 0–2000, convenienceFeeFlatPaise 0–100000, gstBps 0–2800, commissionBps 0–5000, refundFeeOnAttendeeRequest}`.
  - `PUT` writes `platform_settings['fees']` and `updated_by`, then calls `FeeSettingsRepository.evict()` after commit.
  - Audit `SETTINGS_UPDATED` with before and after.
  - The change applies to new quotes and bookings only; stored bookings never change.
- **Reports:**
  - **Attendee:** `POST /api/v1/events/{id}/reports {reason, details? ≤1000}`, where reason is `SCAM | MISLEADING | OFFENSIVE | DUPLICATE | OTHER` and `OTHER` requires details.
    - It needs sign-in and an event visible in discovery; otherwise 404.
    - One `OPEN` report per user per event: a repeat returns the existing one with 200, a new one 201.
  - **Admin:** `GET /admin/reports?status=OPEN` returns `AdminReportRow(id, eventId, eventTitle, organizerName, reason, details, reporterEmail, status, resolutionNote, createdAt, openReportsForEvent)`.
    - `POST /admin/reports/{id}/resolve {note 3–500}` and `/dismiss {note}` work only from OPEN.
    - Audit actions: `REPORT_RESOLVED` and `REPORT_DISMISSED`.
- **Audit log:** `GET /admin/audit?actor&action&entityType&entityId&from&to` (actor = email, case-insensitive exact) returns `AuditRow(id, at, actorEmail, action, entityType, entityId, before (JSON), after (JSON))`, newest first. It is read-only.
- **Overview:** `GET /admin/overview?days=7|30|90` returns `AdminOverview(gmvPaise, platformRevenuePaise, refundsPaise, bookings, ticketsSold, newUsers, totalUsers, liveEvents, pendingApplications, pendingEvents, openReports, failedRefunds, openDisputes, daily: List<DayPoint>)`.
  - Money and count figures cover the range, in IST days. The pending and open counts are current.
  - **GMV:** `sum(bookings.total_paise)` for bookings confirmed in the range with status `CONFIRMED`, `PARTIALLY_REFUNDED` or `REFUNDED`.
  - **Platform revenue:** `fee_paise + commission_paise − (gateway_fee_paise + gateway_tax_paise)` over those bookings. The fee's GST is a tax collected, not revenue.
  - **Refunds:** the sum of `PROCESSED` refunds in the range.
  - **Daily:** the same GMV per day, with tickets.
- **Web:**
  - The admin sidebar becomes: Overview, Users, Organizers, Event review, Transactions, Payouts, Reports, Categories, Settings, Audit log. No "(soon)" labels remain.
  - Destructive actions (suspend, cancel event, refund, record payout) use a confirm dialog that restates the effect, with a required reason where the API needs one.
  - Money is shown with `formatPaise`; inputs take rupees and send paise.

## Review Focus

1. **Two admins record the same payout at the same moment**, or one admin double-clicks. Exactly one payout lands. The other gets 400 `reference`, or `amountPaise` if the balance is now too low, and the balance never goes below what was payable. Test in Task 4 (threads).
2. **An admin suspends a user who is signed in right now.** Their next API call gets 403 `ACCOUNT_SUSPENDED`, not after the 60 s cache. Test in Task 1.
3. **Refunding a booking where some tickets are checked in or already refunded or claimed.** With no ids, only live, unclaimed tickets are refunded. Asking for a checked-in ticket by id gives 400 `ticketIds[i]` "was already used to enter the event", never a partial surprise. Test in Task 3.
4. **Search text with `%`, `_`, quotes or a Razorpay id with spaces around it** finds the right rows and never raises a 500. Test in Task 3, payments `q`; also used in Task 1 users `q`.
5. **A fee change while bookings exist.** The next quote uses the new fee at once, with no 60 s stale cache, and existing bookings' totals and invoices are unchanged. Test in Task 4.

---

### Task 1: Users — search, suspend, admin role

**Files:**
- Create: `db/migration/V14__admin.sql`, holding all Plan 8 schema:
  - `users.suspended_at timestamptz` and `users.suspension_reason text`;
  - a unique index `payouts_org_reference_uq on payouts (organizer_id, lower(reference))`;
  - an index `event_reports_open_uq` — unique `(event_id, reporter_id) where status = 'OPEN'`;
  - an index `bookings_confirmed_at_idx on bookings (confirmed_at)`, which the overview groups by (`confirmed_at` exists since V3).
- Create in `com.stagepass.admin`: `service/AdminUserService.java`, `api/AdminUserController.java`, `api/AdminUserRow.java`, `api/SuspendRequest.java` (`@NotBlank @Size(min=3,max=500) String reason`, reused by Tasks 2, 3 and 5)
- Modify: `identity/domain/User.java` (the two columns)
- Test: `admin/AdminUserTest.java`

**Interfaces:**
- `AdminUserService`:
  - `list(String q, String role, String status, Integer page, Integer size): PageResponse<AdminUserRow>`
  - `suspend(UUID adminId, UUID userId, String reason): AdminUserRow`
  - `reactivate(UUID adminId, UUID userId): AdminUserRow`
  - `grantAdmin(UUID adminId, UUID userId): AdminUserRow`
  - `revokeAdmin(UUID adminId, UUID userId): AdminUserRow`
- Endpoints as in Global Constraints. All return 404 for an unknown user.

- [ ] **Step 1: Write failing tests**

```java
@Test void searchMatchesEmailAndNameWithLiteralWildcards() // users "asha_rao@stagepass.test", "Asharao"; q "asha_" → only the first; q "%" → none; role=ADMIN filter; status=SUSPENDED filter; bad role → 400 field role
@Test void suspendedUserIsBlockedOnTheNextRequest()      // attendee calls /me (200, now cached) → admin suspends → same attendee /me → 403 ACCOUNT_SUSPENDED; row has reason + suspended_at; audit USER_SUSPENDED
@Test void reactivateRestoresAccess()                    // → 200 on /me; reason and suspended_at cleared; audit USER_REACTIVATED
@Test void cannotSuspendSelfOrAnotherAdmin()             // 409 "You can't suspend your own account" / "Remove admin from this account first"
@Test void grantAndRevokeAdmin()                         // grant → user can GET /admin/users; revoke → 403; audit ROLE_GRANTED/ROLE_REVOKED; revoking own → 409
@Test void organizerRoleIsNotGrantableHere()             // PUT /admin/users/{id}/roles/ORGANIZER → 400 field role
@Test void nonAdminGets403()                             // attendee and organizer → 403 on every /admin/users endpoint
```

- [ ] **Step 2: Run — expect FAIL.** `cd stagepass-api && ./mvnw -q test -Dtest=AdminUserTest`
- [ ] **Step 3: Implement.** Use `users.grantRole` for granting and add a matching `revokeRole`. Suspend, reactivate and both role changes call `roleLookup.evictAfterCommit(userId)`. Before is checked against after so a repeat call is a no-op and writes no audit.
- [ ] **Step 4: Run — expect PASS**, then the full suite.
- [ ] **Step 5: Commit** — `feat(admin): users search, suspension and admin role`

### Task 2: Organizers and events — suspend, edit, cancel

**Files:**
- Modify: `admin/service/AdminOrganizerService.java` (list, detail, suspend, reinstate, update), `catalog/service/EventLifecycleService.java` (`adminCancel`, `adminList` with `q`), `admin/api/AdminEventController.java`, `checkin/service/ScanAccess.java` (owner when APPROVED or SUSPENDED)
- Create: `admin/api/AdminOrganizersController.java` (`/api/v1/admin/organizers`; the applications controller stays), `admin/api/AdminOrganizerRow.java`, `admin/api/AdminOrganizerDetail.java`, `admin/api/OrganizerUpdateRequest.java`
- Test: `admin/AdminOrganizerManagementTest.java`, `admin/AdminEventCancelTest.java`

**Interfaces:**
- Consumes: `PayoutBalances.forOrganizer(UUID): Balances`; `SuspendRequest` (Task 1).
- `AdminOrganizerService`:
  - `organizers(String q, OrganizerStatus status, Integer page, Integer size): PageResponse<AdminOrganizerRow>`
  - `detail(UUID id): AdminOrganizerDetail`
  - `suspend(UUID adminId, UUID id, String reason)`
  - `reinstate(UUID adminId, UUID id)`
  - `update(UUID adminId, UUID id, OrganizerUpdateRequest r): AdminOrganizerDetail`
- `EventLifecycleService.adminCancel(UUID adminId, UUID eventId, String reason): EventResponse`.
- `OrganizerUpdateRequest`: the `orgName` field is optional — a null (absent) field is left unchanged, and the `gstin` field cleared with `""`.

- [ ] **Step 1: Write failing tests**

```java
// AdminOrganizerManagementTest
@Test void listFiltersAndShowsPayable()              // approved org with a finished event's sale → payablePaise > 0; q by contact email; status=SUSPENDED filter
@Test void suspendHidesEventsBlocksDashboardKeepsDoor() // suspend → public GET event 404; POST /bookings 404; organizer GET /organizer/overview 403 ORGANIZER_NOT_APPROVED; owner scan of a ticket → VALID; staff scan → VALID; audit ORGANIZER_SUSPENDED
@Test void reinstateRestores()                       // public event visible again; dashboard 200
@Test void suspendOnlyFromApproved()                 // PENDING org → 409
@Test void updateValidatesAndAuditsChangedFieldsOnly() // pan "abcde1234f" → 400 pan; valid pan + gstin "" → saved, gstin null; commission 6000 → 400; audit after has only changed keys
@Test void commissionOverrideAffectsNextQuote()      // set 500 bps → quote/booking commission uses it
// AdminEventCancelTest
@Test void adminCancelsPublishedEventAndRefundsEveryone() // 2 confirmed bookings → both refunded via fake Razorpay; event CANCELLED; audit byAdmin true; EVENT_CANCELLED emails queued
@Test void adminCancelOfCompletedIs409()
@Test void adminEventListSearch()                    // status=PUBLISHED&q=<organizer name> → that org's events only
```

- [ ] **Step 2: Run — expect FAIL.** `./mvnw -q test -Dtest='AdminOrganizerManagementTest,AdminEventCancelTest'`
- [ ] **Step 3: Implement.** `cancel` and `adminCancel` share one private `doCancel(Event, actorId, reason, boolean byAdmin)`. `AdminOrganizerRow.payablePaise` and `negative` come from `PayoutBalances` for each row of the page (page size ≤ 100). The account number in the detail stays masked, the same way `OrganizerApplicationResponse` masks it.
- [ ] **Step 4: Run — expect PASS**, then the full suite (the Plan 7 `ScanAccess` tests must still pass).
- [ ] **Step 5: Commit** — `feat(admin): organizer management, suspension rules and admin event cancel`

### Task 3: Transactions — payments, refunds, disputes, admin refund, CSV

**Files:**
- Create in `admin`: `service/AdminTransactionQueries.java`, `api/AdminTransactionController.java`, `api/AdminPaymentRow.java`, `api/AdminRefundRow.java`, `api/AdminDisputeRow.java`, `api/AdminRefundRequest.java` (`List<UUID> ticketIds`, `boolean includeFee`, `@NotBlank @Size(min=3,max=500) String reason`)
- Modify: `booking/service/RefundService.java` (`adminRefund`)
- Test: `admin/AdminTransactionsTest.java`, `admin/AdminRefundTest.java`

**Interfaces:**
- `AdminTransactionQueries`:
  - `payments(String q, String status, LocalDate from, LocalDate to, Integer page, Integer size)`
  - `refunds(String status, String q, …)`
  - `disputes(String status, …)`
  - `writePaymentsCsv(UUID adminId, LocalDate from, LocalDate to, String status, Writer out): int`
  - `csvFilename(LocalDate from, LocalDate to): String`
- `RefundService.adminRefund(UUID adminId, UUID bookingId, List<UUID> ticketIds, boolean includeFee, String reason): BookingResponse`. It locks the booking and picks tickets with the existing `pick`, which rejects checked-in or claimed tickets. It then calls `apply(b, chosen, RefundKind.ADMIN, includeFee, null, adminId, reason, null)` and `submitOrFail`.
- Routes:
  - `GET /admin/payments`, `/admin/refunds`, `/admin/disputes`
  - `GET /admin/payments/export`
  - `POST /admin/bookings/{id}/refund`
  - `POST /admin/refunds/{id}/retry`

- [ ] **Step 1: Write failing tests**

```java
// AdminTransactionsTest
@Test void paymentSearchByCodeEmailAndRazorpayIds() // q " pay_TEST123 " (spaces) → that payment; q booking code lower-case → found; q "%_'" → empty, 200
@Test void dateRangeIsIstInclusiveAndBounded()      // payment at 2026-12-01 23:30 IST in from=to=2026-12-01; to<from → 400 to; 400 days → 400 to
@Test void refundsListShowsRetryState()             // FAILED needs_retry row with attempts and failureReason; status=FAILED filter
@Test void disputesList()                           // dispute recorded via webhook test helper → row with booking code
@Test void csvExportHasBomHeaderGuardAndAudit()     // BOM; header exactly as Global Constraints; amount "1234.50"; attendee email "=cmd@x.test" cell guarded; audit TRANSACTIONS_EXPORTED with rows
// AdminRefundTest
@Test void adminRefundsWholeBookingIgnoringPolicy()  // event refund_policy NONE → admin refund 200; kind ADMIN; includeFee true → fee+GST refunded; audit REFUND_ISSUED byAdmin
@Test void adminRefundSkipsNothingSilently()        // booking with 1 checked-in + 1 valid: ticketIds=[checkedIn] → 400 field ticketIds[0]; ticketIds=[] → refunds only the valid one
@Test void foreignTicketIdIs400()                   // ticket of another booking → 400 field ticketIds[0]
@Test void retryOnlyForFailedNeedingRetry()         // PROCESSED → 409; FAILED needs_retry → REQUESTED then submitted; audit REFUND_RETRIED
```

- [ ] **Step 2: Run — expect FAIL.** `./mvnw -q test -Dtest='AdminTransactionsTest,AdminRefundTest'`
- [ ] **Step 3: Implement.**
  - The CSV follows `OrganizerBookingQueries.writeAttendeesCsv`: the audit goes in REQUIRES_NEW before streaming, and the stream uses `setFetchSize(500)`.
  - Rupee formatting is a small `Rupees.plain(long paise)` in `admin/service`, giving `1234.50`.
  - "Refunded" sums the booking's `PROCESSED` refunds.
- [ ] **Step 4: Run — expect PASS**, then the full suite.
- [ ] **Step 5: Commit** — `feat(admin): transactions, admin refunds and payments CSV`

### Task 4: Payouts and fee settings

**Files:**
- Create in `admin`: `service/AdminPayoutService.java`, `service/PlatformSettingsService.java`, `api/AdminPayoutController.java`, `api/AdminSettingsController.java`, `api/RecordPayoutRequest.java`, `api/FeeSettingsRequest.java`, `api/PayoutRow.java`, `api/OrganizerPayoutSummary.java`
- Modify: `notification/service/EmailTemplate.java` and `EmailRenderer.java` (`PAYOUT_RECORDED`)
- Test: `admin/AdminPayoutTest.java`, `admin/ConcurrentPayoutTest.java`, `admin/FeeSettingsAdminTest.java`

**Interfaces:**
- Consumes: `PayoutBalances.forOrganizer`; `FeeSettingsRepository.current()` / `evict()`; `EmailOutbox.enqueue(to, template, payload, dedupeKey)` (the Plan 5 overload).
- `AdminPayoutService`:
  - `summaries(String q, boolean onlyPayable, Integer page, Integer size): PageResponse<OrganizerPayoutSummary(organizerId, orgName, pendingPaise, payablePaise, paidOutPaise, negative, lastPayoutAt)>`
  - `record(UUID adminId, UUID organizerId, RecordPayoutRequest r): PayoutRow(id, amountPaise, reference, paidAt, recordedByEmail, note)`
- `PlatformSettingsService`:
  - `fees(): FeeSettings`
  - `updateFees(UUID adminId, FeeSettingsRequest r): FeeSettings`
- Email `PAYOUT_RECORDED` payload: `orgName`, `amountPaise`, `reference`, `paidOn`.

- [ ] **Step 1: Write failing tests**

```java
// AdminPayoutTest
@Test void recordPayoutReducesPayableAndWritesLedger()  // payable 50_000 → record 30_000 ref "UTR123" → payable 20_000, paidOut 30_000; ledger PAYOUT -30_000 "Payout UTR123"; organizer GET /organizer/payouts shows it; email queued once; audit PAYOUT_RECORDED
@Test void cannotPayMoreThanPayableOrPendingMoney()     // 60_000 → 400 amountPaise mentions "Rs. 500.00"; event not yet past hold → payable 0 → 400
@Test void referenceUniquePerOrganizerCaseInsensitive() // "utr123" again → 400 reference; same ref for another organizer → OK
@Test void futurePaidOnRejected()                       // tomorrow (IST) → 400 paidOn
@Test void summariesPayableFirstAndFilter()             // onlyPayable=true hides zero-payable orgs; negative org flagged
// ConcurrentPayoutTest
@Test void parallelPayoutsNeverOverpay()                // payable 50_000; 6 threads each record 30_000 with distinct refs → exactly 1 succeeds; payable 20_000
// FeeSettingsAdminTest
@Test void updateAppliesToNextQuoteImmediately()        // quote before (250 bps) → PUT 500 bps → quote again (same second) shows new fee; existing booking total unchanged; audit SETTINGS_UPDATED before/after
@Test void boundsValidated()                            // convenienceFeeBps 2001, gstBps -1, flat 100001 → 400 with each field
```

- [ ] **Step 2: Run — expect FAIL.** `./mvnw -q test -Dtest='AdminPayoutTest,ConcurrentPayoutTest,FeeSettingsAdminTest'`
- [ ] **Step 3: Implement.**
  - `record` runs in one transaction. It runs `select id from organizers where id = :id for update` first, then `PayoutBalances.forOrganizer`, the checks, and the two inserts.
  - A unique-index violation on reference becomes 400 `reference`.
  - The fee cache is evicted with `TransactionSynchronization.afterCommit`.
- [ ] **Step 4: Run — expect PASS**, then the full suite.
- [ ] **Step 5: Commit** — `feat(admin): record payouts and edit platform fees`

### Task 5: Reports, audit log and platform overview

**Files:**
- Create: `discovery/api/EventReportController.java` (attendee POST); `admin/service/EventReportService.java`, `admin/service/AuditLogQueries.java`, `admin/service/PlatformAnalytics.java`; `admin/api/AdminReportController.java`, `admin/api/AdminAuditController.java`, `admin/api/AdminOverviewController.java`, `admin/api/AdminReportRow.java`, `admin/api/AuditRow.java`, `admin/api/AdminOverview.java`, `discovery/api/EventReportRequest.java`
- Test: `admin/EventReportTest.java`, `admin/AuditLogQueriesTest.java`, `admin/PlatformAnalyticsTest.java`

**Interfaces:**
- `EventReportService`:
  - `report(UUID userId, UUID eventId, EventReportRequest r): Reported(EventReportView report, boolean created)`
  - `list(String status, Integer page, Integer size): PageResponse<AdminReportRow>`
  - `resolve(UUID adminId, UUID id, String note)`
  - `dismiss(UUID adminId, UUID id, String note)`
- `AuditLogQueries.search(String actorEmail, String action, String entityType, String entityId, LocalDate from, LocalDate to, Integer page, Integer size): PageResponse<AuditRow>`. `before` and `after` are returned as `JsonNode`.
- `PlatformAnalytics.overview(Integer days): AdminOverview`. It uses `DailySeries.range` and `DailySeries.fill` with IST dates.

- [ ] **Step 1: Write failing tests**

```java
// EventReportTest
@Test void attendeeReportsOnceWhileOpen()          // 201 then 200 same id; OTHER without details → 400 details; draft/unknown event → 404
@Test void adminResolvesAndDismisses()             // list OPEN shows openReportsForEvent 2; resolve note → RESOLVED, resolved_by; again → 409; audit REPORT_RESOLVED
@Test void afterResolutionUserCanReportAgain()     // new OPEN row allowed
// AuditLogQueriesTest
@Test void filtersAndOrder()                       // actor email case-insensitive; action; entityType+entityId; from/to IST; newest first; before/after come back as JSON objects
// PlatformAnalyticsTest
@Test void overviewMoneyAndCounts()                // 2 paid bookings (₹1,000 + ₹500 subtotal) with gateway fees set, 1 refunded → gmv, platformRevenue = fees + commission − gateway fee − gateway tax, refunds; pendingApplications/pendingEvents/openReports/failedRefunds counts
@Test void dailyBucketsInIst()                     // booking confirmed 2026-12-01 23:45 IST lands on 2026-12-01; series length = days; days=45 → 400
```

- [ ] **Step 2: Run — expect FAIL.** `./mvnw -q test -Dtest='EventReportTest,AuditLogQueriesTest,PlatformAnalyticsTest'`
- [ ] **Step 3: Implement.** Report visibility is decided by the rule `PublicEventService` uses: `VISIBLE` = PUBLISHED, CANCELLED or COMPLETED, with the organizer not suspended. The partial unique index from V14 backs "one open report", and a race on insert answers with the existing row.
- [ ] **Step 4: Run — expect PASS**, then the full suite.
- [ ] **Step 5: Commit** — `feat(admin): event reports, audit log and platform overview`

### Task 6: Web — overview, users, organizers, event cancel

**Files:**
- Create in `stagepass-web/src/features/admin/`: `types.ts` (all Plan 8 admin types), `ConfirmAction.tsx` (dialog with headline, effect text, optional required reason 3–500, and a confirm button showing a pending state), `OverviewPage.tsx` (replaces `AdminHomePage`), `UsersPage.tsx`, `OrganizersPage.tsx`, `OrganizerDetailPage.tsx`
- Modify: `features/admin/api.ts`, `AdminLayout.tsx` (final nav), `EventReviewPage.tsx` (status tabs including Published, a search box, and "Cancel event" through `ConfirmAction`), `app/router.tsx` (`/admin/users`, `/admin/organizers` (the list, with applications as a tab), `/admin/organizers/:id`)
- Reuse: `KpiCard`, `SalesChart`, `RangePicker` from `features/organizer/dashboard`; `ListControls` from `organizer/manage`
- Test: `OverviewPage.test.tsx`, `UsersPage.test.tsx`, `OrganizerDetailPage.test.tsx`, `ConfirmAction.test.tsx`

**Interfaces:**
- Hooks in `api.ts`:
  - `useAdminOverview(days)`
  - `useAdminUsers(params)`, `useSuspendUser()`, `useReactivateUser()`, `useSetAdmin()`
  - `useAdminOrganizers(params)`, `useAdminOrganizer(id)`, `useSuspendOrganizer()`, `useReinstateOrganizer()`, `useUpdateOrganizer()`
  - `useCancelEvent()`
  
  Mutations invalidate `['admin', …]` keys.
- Overview content:
  - KPI cards: GMV, Platform revenue, Refunds, Tickets sold, New users.
  - A "Needs attention" row of links, each with its count: applications, events to review, open reports, failed refunds, open disputes.
  - A GMV `SalesChart`.

- [ ] **Step 1: Write failing tests**

```tsx
test('overview shows money and attention counts')        // formatPaise values; "3 open reports" links to /admin/reports
test('suspending a user needs a reason and shows the new status') // reason < 3 chars keeps button disabled; submit → API called with reason; row shows "Suspended"
test('own row has no suspend or remove-admin actions')   // current user id from useMe
test('organizer detail edits PAN and shows payable')     // invalid PAN shows field error from 400; payable/pending/negative badge
test('confirm dialog restates the effect and blocks double submit') // pending state disables the button
```

- [ ] **Step 2: Run — expect FAIL.** `cd stagepass-web && npx vitest run src/features/admin`
- [ ] **Step 3: Implement** to the Global Constraints. Every page has loading, empty and error states. The API's field errors map onto the form fields.
- [ ] **Step 4: Run — expect PASS**; also `npm run lint`, `npx tsc -b` and `npm run build`.
- [ ] **Step 5: Commit** — `feat(web): admin overview, users, organizers and event cancel`

### Task 7: Web — transactions, payouts, settings, reports, audit log

**Files:**
- Create in `features/admin/`: `TransactionsPage.tsx` (tabs Payments, Refunds and Disputes; search, status and date range; "Export CSV" through `downloadFile` from `features/bookings/downloads.ts`; per-payment "Refund" through `ConfirmAction` with an include-fee checkbox; a Retry button on retryable refunds), `PayoutsPage.tsx` (summary table, "Only payable" toggle, "Record payout" dialog: amount in rupees prefilled with the payable amount, reference, paid-on date defaulting to today in IST, note), `SettingsPage.tsx` (fee form in percent and rupees, converted to bps and paise, with a live example "On a ₹1,000 × 2 order the attendee pays…" using the same formula as `FeeCalculator`), `ReportsPage.tsx`, `AuditLogPage.tsx` (filters; before and after shown as a key-by-key diff)
- Create: `features/event/ReportEventDialog.tsx`, and add a "Report this event" link to the event page's footer (signed-in users only; signed-out users go to `/login?next=`)
- Modify: `app/router.tsx`, `features/admin/api.ts`
- Test: `TransactionsPage.test.tsx`, `PayoutsPage.test.tsx`, `SettingsPage.test.tsx`, `ReportsPage.test.tsx`, `features/event/ReportEventDialog.test.tsx`

- [ ] **Step 1: Write failing tests**

```tsx
test('record payout sends paise and shows the over-balance error')  // ₹200.50 → amountPaise 20050; 400 amountPaise message shown under the field
test('settings converts percent to bps and previews the fee')       // 2.5% → 250; preview equals FeeCalculator for ₹1,000 × 2
test('refund dialog sends includeFee and reason')                   // checkbox default on for admin refunds; API body matches
test('csv export downloads with the date range')                    // downloadFile called with a path carrying from/to
test('report dialog requires details for Other')                    // and shows "Thanks — our team will review it" on 201/200
test('resolve report needs a note')
```

- [ ] **Step 2: Run — expect FAIL.** `npx vitest run src/features/admin src/features/event`
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run — expect PASS**; lint, tsc and build clean; full web suite.
- [ ] **Step 5: Commit** — `feat(web): admin transactions, payouts, fee settings, reports and audit log`

---

## Rulings to confirm with the user (assistant's calls)

1. **A suspended organizer's events still check in at the door**, both for the owner and for staff. Suspension stops sales and hides the events. To stop an event, the admin cancels it, which refunds everyone.
2. **A payout can't be more than the payable balance**, and its reference (UTR) must be unique per organizer.
3. **Only ADMIN can be granted from the Users page.** ORGANIZER still comes only from an approved application. An admin must remove another admin's role before suspending them, and can't suspend or demote themselves.
4. **Admin refunds ignore the event's refund policy.** The admin chooses whether the convenience fee is returned; the dialog defaults to yes.
5. **Platform revenue is the fee plus commission, minus Razorpay's fee and its tax.** The fee's GST is excluded, since it's tax collected.
