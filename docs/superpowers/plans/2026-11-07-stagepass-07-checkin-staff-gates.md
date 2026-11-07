# Stagepass Check-in, Door Staff & Gates Implementation Plan (Plan 7 of 11)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** At the door, an organizer or the door staff they invited opens the Stagepass scanner on a phone, picks the event and gate, scans ticket QR codes (or types the ticket code) and gets an instant, unambiguous answer — Valid, Already used (when, where, by whom), Wrong gate, Wrong event or Invalid — with exactly one entry per ticket across all gates, every scan logged, and live counts for the organizer.

**Architecture:** New backend module `checkin` (scan service, access rules, gates, staff, stats). Access is decided per request from the database — the event's organizer, or an `ACTIVE` `event_staff` row for that event (or for all of the organizer's events) matching the user id or, before first use, the user's verified email — so removing staff takes effect on the next scan. "Scanner" is a per-organizer assignment stored on `event_staff.role`, not a global account role. Entry is decided by `insert … on conflict (ticket_id) do nothing` on `check_ins`. Web adds a standalone scanner app (`/scan`) using `html5-qrcode`, plus organizer screens for staff, gates and live check-in.

**Tech Stack:** as Plans 1–6; web `html5-qrcode` 2.3.8 (camera QR scanning; needs HTTPS or localhost).

**Spec:** `docs/superpowers/specs/2026-11-05-stagepass-design.md` — §8.6 Check-in and its "Door staff and gates" addendum (2026-11-06), §8.5 QR token, §7 `check_ins` / `scan_logs`.

## Global Constraints

- All Plan 1–6 Global Constraints apply.
- **Who can scan event E:** the organizer who owns E (approved organizer account), or a user with an `ACTIVE` `event_staff` row of E's organizer whose `event_id` is E or null (= all events), matched by `user_id`, or by `lower(email)` = the user's email while `user_id` is null (the first match links `user_id` and sets `accepted_at`). Anyone else gets **404** for E (no existence leak). Checked on every request, never cached.
- **Scan result** (`POST /api/v1/checkin/scan {eventId, gateId?, token?, code?}` — exactly one of `token`/`code`): decided in this order —
  1. token fails `QrTokens.verify`, or no ticket with that id/code, or `qr_nonce` differs → `INVALID` "Not a valid Stagepass ticket";
  2. ticket's event ≠ `eventId` → `WRONG_EVENT` naming the ticket's event title;
  3. ticket `REFUNDED` → `INVALID` "This ticket was refunded"; `CANCELLED` → `INVALID` "This ticket was cancelled"; booking not `CONFIRMED`/`PARTIALLY_REFUNDED` → `INVALID`;
  4. gate given and its ticket-type list is non-empty and excludes the ticket's type → `WRONG_GATE` with the names of gates that allow it (not checked in);
  5. `insert into check_ins … on conflict (ticket_id) do nothing returning id` → inserted: `VALID` (ticket → `CHECKED_IN`); otherwise `ALREADY_USED` with the first check-in's time, gate name and scanner name (`byYou: true` when the same user).
  Every scan writes one `scan_logs` row (result, gate, scanner, ticket if known, `manual` = code entry). Responses are always 200 with a result; only access problems are 4xx.
- `ScanResponse(String result, String message, TicketInfo ticket /* code, holderName, ticketType */, FirstScan firstScan /* at, gateName, scannerName, byYou */, List<String> allowedGates, Counts counts /* checkedIn, total */)`; `total` = tickets `VALID` + `CHECKED_IN` for the event.
- Events scannable when `PUBLISHED`, or `COMPLETED` with `ends_at` within the last 12 hours; otherwise scan → 409 `INVALID_STATE` "Check-in is closed for this event".
- Manual codes are matched case-insensitively after trimming (`sp-aaaa-2222` works).
- **Staff invites:** organizer adds an email with `eventId` (one event) or none (all events); duplicates of an active invite answer the existing row; removing sets `REMOVED` + `removed_at`. Each invite queues email `STAFF_INVITED` (dedupe `staff-invited:{staffId}`) with subject `You're on the door team for {organizer name}` and a button to `{webBaseUrl}/scan`. Staff never see sales, payouts, attendee lists or settings — scanner endpoints return only ticket code, holder name and type for the scanned ticket.
- **Gates:** name 1–40 chars, unique per event (case-insensitive), optional list of the event's ticket type ids; deleting a gate keeps history (`gate_id` set null).
- Audit: `STAFF_INVITED`, `STAFF_REMOVED`, `GATE_CREATED/UPDATED/DELETED`. Check-ins themselves are recorded in `check_ins`/`scan_logs`, not the audit log.
- Web scanner: full-screen result (green / amber / red) with a large headline, holder name and type, vibration (`navigator.vibrate`: 80 ms valid, 3×120 ms otherwise), auto-ready for the next scan after 2.5 s or on tap; a camera-permission explainer; manual entry always available; counts update after each scan.

## Review Focus

1. **Two doormen scan the same ticket at different gates at the same moment** → exactly one `VALID`; the other `ALREADY_USED` naming the gate and scanner — test in Task 1 (threads).
2. **A removed staff member's scanner is still open** → their next scan is refused (404) — test in Task 2.
3. **Screenshot of a ticket that was later refunded**, or a random QR (UPI, URL), or a forged/edited token → `INVALID`, never a 500 — tests in Task 1.
4. **Staff of organizer A try organizer B's event** (by id) → 404, and B's event is absent from their event list — test in Task 2.
5. **Network retry of the same scan by the same doorman** → second answer is `ALREADY_USED` with `byYou: true` and the first scan's gate, so the UI can say "Checked in by you just now" — test in Task 1, UI copy in Task 5.

---

### Task 1: Scan core — schema, scan service, results

**Files:**
- Create: `db/migration/V13__checkin_staff_gates.sql` (all Plan 7 schema: `event_staff`, `gates`, `gate_ticket_types`; `check_ins.gate_id`, `scan_logs.gate_id`, `scan_logs.manual`; `scan_logs` result check adds `WRONG_GATE`)
- Create in `com.stagepass.checkin`: `service/CheckInService.java`, `service/ScanAccess.java` (owner-only in this task), `api/CheckInController.java`, `api/ScanRequest.java`, `api/ScanResponse.java`
- Test: `checkin/CheckInScanTest.java`, `checkin/ConcurrentScanTest.java`

**Interfaces:**
- Schema: `event_staff (id, organizer_id → organizers, event_id → events null, email text not null, user_id → users null, role text not null default 'SCANNER' check (role in ('SCANNER')), status text not null default 'ACTIVE' check (status in ('ACTIVE','REMOVED')), invited_by → users, created_at, accepted_at, removed_at)` with a unique index on `(organizer_id, lower(email), coalesce(event_id, '00000000-0000-0000-0000-000000000000'))` where `status = 'ACTIVE'`; `gates (id, event_id → events, name, sort_order, created_at)` unique `(event_id, lower(name))`; `gate_ticket_types (gate_id → gates on delete cascade, ticket_type_id → ticket_types, primary key both)`; `check_ins.gate_id` and `scan_logs.gate_id` → gates `on delete set null`.
- `ScanAccess.requireScannable(UUID userId, UUID eventId): ScannableEvent(UUID eventId, UUID organizerId, String title, boolean owner)` — 404 when not allowed (Task 2 extends to staff).
- `CheckInService.scan(UUID userId, ScanRequest r): ScanResponse` per Global Constraints.
- `POST /api/v1/checkin/scan` (authenticated, any role) → 200 `ScanResponse`; 400 when neither/both of token and code; 404 per access rule; 409 when check-in is closed.

- [ ] **Step 1: Write failing tests**

```java
// CheckInScanTest (organizer scans own event; tickets via BookingTestSupport.confirmed)
@Test void validTicketChecksInOnce()            // VALID with holder "Asha Rao", type, counts 1/2; ticket status CHECKED_IN; scan_logs row VALID
@Test void secondScanIsAlreadyUsed()            // same token again → ALREADY_USED, firstScan.byYou true, firstScan.at = first check-in time
@Test void manualCodeWorksCaseInsensitively()   // code " sp-xxxx-xxxx " (lower-cased real code) → VALID, scan_logs.manual true
@Test void refundedTicketIsInvalid()            // ticket REFUNDED → INVALID "This ticket was refunded"; not checked in
@Test void forgedRandomAndRotatedTokensAreInvalid() // edited token, token signed with another secret, "upi://pay?pa=x@y", "", 10k chars, nonce changed in DB → INVALID each, no 500
@Test void ticketForAnotherEventIsWrongEvent()  // message contains the other event's title; scan_logs WRONG_EVENT
@Test void needsExactlyOneOfTokenAndCode()      // 400 VALIDATION_FAILED
@Test void strangerGets404()                    // another approved organizer and a plain attendee → 404
@Test void closedEventRefusesScans()            // COMPLETED with ends_at 2 days ago → 409 INVALID_STATE
// ConcurrentScanTest
@Test void twoScannersOneWinner()               // 8 threads scan the same token → exactly 1 VALID, 7 ALREADY_USED; one check_ins row; 8 scan_logs rows
```

- [ ] **Step 2: Run — expect FAIL.** `cd stagepass-api && ./mvnw -q test -Dtest='CheckInScanTest,ConcurrentScanTest'`
- [ ] **Step 3: Implement.** One transaction per scan; the ticket row is read with its booking status and type name; `ALREADY_USED` reads the winning `check_ins` row joined to gate and user names.
- [ ] **Step 4: Run — expect PASS**, then full suite.
- [ ] **Step 5: Commit** — `feat(checkin): QR and code scanning with one entry per ticket`

---

### Task 2: Door staff — invites, access, scannable events

**Files:**
- Create in `com.stagepass.checkin`: `service/StaffService.java`, `api/OrganizerStaffController.java`, `api/StaffRequest.java`, `api/StaffResponse.java`, `api/ScannerEventsController.java`
- Modify: `checkin/service/ScanAccess.java` (staff rule), `notification/service/EmailTemplate.java` + `EmailRenderer.java` (`STAFF_INVITED`)
- Test: `checkin/StaffAccessTest.java`

**Interfaces:**
- `GET /api/v1/organizer/staff` → `List<StaffResponse(UUID id, String email, String name /* user's name once they signed in, else null */, UUID eventId, String eventTitle /* null = all events */, String status, OffsetDateTime createdAt, OffsetDateTime acceptedAt)>` (active first); `POST /api/v1/organizer/staff` `StaffRequest(@NotBlank @Email String email, UUID eventId)` → 201 (200 when the same active invite exists); `DELETE /api/v1/organizer/staff/{id}` → 204. `eventId` must be the organizer's event (else 404).
- `GET /api/v1/checkin/events` → `List<ScannerEvent(UUID id, String title, OffsetDateTime startsAt, OffsetDateTime endsAt, String venue, String organizerName, boolean owner, List<GateOption> gates)>` with `record GateOption(UUID id, String name, List<String> ticketTypeNames /* empty = all types */)` — events the user may scan that are scannable now or start within the next 7 days, soonest first (`gates` filled in Task 3; empty list here).
- `ScanAccess` resolves owner first, then staff (Global Constraints), linking `user_id`/`accepted_at` on first email match.

- [ ] **Step 1: Write failing tests**

```java
@Test void invitedEmailCanScanAfterSigningIn()   // invite "door1@test.in" for event E before that user exists; provision user with that email → GET /checkin/events lists E; scan → VALID; staff row now has user_id and accepted_at
@Test void allEventsInviteCoversEveryEvent()     // eventId null → both of the organizer's events listed and scannable
@Test void removedStaffIsRefusedOnNextScan()     // scan VALID, DELETE staff, next scan → 404
@Test void staffCannotReachOtherOrganizersEvents() // staff of A: B's event absent from list, scan of B's ticket → 404
@Test void staffCannotSeeOrganizerData()         // staff user → GET /organizer/overview 403, /organizer/events/{E}/attendees 403
@Test void inviteIsIdempotentAndEmailed()        // same email+event twice → one row; one STAFF_INVITED outbox row with dedupe key
@Test void inviteForForeignEventIs404()
```

- [ ] **Step 2: Run — expect FAIL.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run — expect PASS**, then full suite.
- [ ] **Step 5: Commit** — `feat(checkin): door staff invites and access`

---

### Task 3: Gates, gate rules, live stats and scan log

**Files:**
- Create in `com.stagepass.checkin`: `service/GateService.java`, `service/CheckInStats.java`, `api/OrganizerGateController.java`, `api/GateRequest.java`, `api/GateResponse.java`, `api/CheckInStatsResponse.java`, `api/ScanLogRow.java`
- Modify: `CheckInService.java` (`WRONG_GATE`, gate name in `ALREADY_USED`), `ScannerEventsController.java` (gates in the list)
- Test: `checkin/GatesTest.java`, `checkin/CheckInStatsTest.java`

**Interfaces:**
- Organizer: `GET /api/v1/organizer/events/{eventId}/gates` → `List<GateResponse(UUID id, String name, List<UUID> ticketTypeIds, int checkedIn)>`; `POST` / `PATCH /{gateId}` with `GateRequest(@NotBlank @Size(max = 40) String name, List<UUID> ticketTypeIds)`; `DELETE /{gateId}` → 204. Ticket types must belong to the event (400 field `ticketTypeIds`); duplicate name → 400 field `name`.
- `GET /api/v1/checkin/events/{eventId}/stats` (owner or staff) → `CheckInStatsResponse(int checkedIn, int total, List<GateCount(UUID gateId, String name, int checkedIn)> byGate /* plus a "No gate" row when any */, List<ScannerCount(String name, int checkedIn)> byScanner, OffsetDateTime lastScanAt)`.
- `GET /api/v1/organizer/events/{eventId}/scan-log?result=&page=&size=` (owner only) → `PageResponse<ScanLogRow(OffsetDateTime at, String result, String ticketCode, String holderName, String gateName, String scannerName, boolean manual)>`, newest first.

- [ ] **Step 1: Write failing tests**

```java
// GatesTest
@Test void gateLimitedToTypesSaysWrongGate()     // VIP gate allows only VIP type; General ticket at VIP gate → WRONG_GATE, allowedGates ["Main Gate"] (gates with no list or listing General); not checked in; then at Main Gate → VALID
@Test void alreadyUsedNamesTheFirstGate()        // VALID at Gate 1, rescan at Gate 2 → ALREADY_USED firstScan.gateName "Gate 1"
@Test void gateFromAnotherEventIsRejected()      // scan with a gate of another event → 400 field gateId
@Test void gateCrudValidates()                   // duplicate name (case-insensitive) → 400 name; foreign ticket type → 400 ticketTypeIds; delete keeps check_ins (gate_id null)
@Test void staffCannotEditGates()                // staff user → POST gates 403
// CheckInStatsTest
@Test void countsByGateAndScanner()              // 3 check-ins: 2 at Gate 1 by organizer, 1 at Gate 2 by staff → byGate [2,1], byScanner [2,1], total = live tickets
@Test void scanLogNewestFirstAndFilterable()     // result=INVALID filter; organizer only (staff → 403)
```

- [ ] **Step 2: Run — expect FAIL.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run — expect PASS**, then full suite.
- [ ] **Step 5: Commit** — `feat(checkin): gates, gate rules, live stats and scan log`

---

### Task 4: Web — organizer staff, gates and live check-in

**Files:**
- Create: `stagepass-web/src/features/organizer/staff/StaffPage.tsx`, `staff/api.ts`, `features/organizer/manage/GatesEditor.tsx`, `manage/ScanLog.tsx`, `features/checkin/api.ts`, `features/checkin/types.ts`
- Modify: `features/organizer/manage/CheckInTab.tsx` (live counts by gate and staff with 10 s polling, gates editor, scan log, "Open scanner" → `/scan/{eventId}`), `organizer/OrganizerLayout.tsx` (nav: "Door staff"; "Scanner" → `/scan`), `src/app/router.tsx` (`/organizer/staff`)
- Test: `staff/StaffPage.test.tsx`, `manage/GatesEditor.test.tsx`, `manage/CheckInTab.test.tsx` (update)

**Interfaces:**
- Staff page: invite form (email + "All my events" or a specific upcoming event), list with status chips (Invited — not signed in yet / Active), Remove with confirm; copy explaining staff can only scan.
- Gates editor: add/rename/delete gates; per gate a checkbox list of the event's ticket types ("All ticket types" when none ticked); delete confirm says history is kept.
- Check-in tab: big checked-in / total with progress, per-gate and per-staff bars, last scan time, scan log table with result filter.

- [ ] **Step 1: Write failing tests**

```ts
test('invites door staff for one event or all events')   // POST body { email, eventId } / { email, eventId: null }; list shows "Invited"
test('removing staff asks to confirm')
test('gate can be limited to ticket types')               // tick VIP → POST { name: 'VIP Gate', ticketTypeIds: ['t-vip'] }
test('check-in tab shows per-gate counts and polls')     // mocked stats → "Gate 1 · 2"; useCheckInStats called with refetchInterval 10000
test('open scanner goes to the scanner for this event')   // link href /scan/e1
```

- [ ] **Step 2: Run — expect FAIL.** `cd stagepass-web && npx vitest run`
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run — expect PASS**; lint; build.
- [ ] **Step 5: Commit** — `feat(web): organizer door staff, gates and live check-in`

---

### Task 5: Web — the scanner app

**Files:**
- Create: `stagepass-web/src/features/checkin/ScannerHomePage.tsx` (`/scan`: events I can scan), `checkin/ScannerPage.tsx` (`/scan/:eventId`: gate picker → camera + manual entry → result), `checkin/CameraScanner.tsx` (wraps `Html5Qrcode`), `checkin/ResultCard.tsx`, `checkin/useScan.ts`
- Modify: `package.json` (`html5-qrcode` 2.3.8), `src/app/router.tsx` (`/scan`, `/scan/:eventId`, RequireAuth, minimal full-screen layout without the site chrome), README (scanner needs HTTPS or localhost; staff sign in with the invited email)
- Test: `checkin/ScannerPage.test.tsx`, `checkin/ResultCard.test.tsx`, `checkin/ScannerHomePage.test.tsx`

**Interfaces:**
- `CameraScanner({ onCode, paused })` — starts the back camera (`facingMode: 'environment'`, 10 fps, qrbox 70% of the shorter side), ignores the same text repeated within 3 s, stops on unmount; permission denied → explainer with "Use ticket code instead".
- `useScan(eventId, gateId)` → `scan({ token } | { code })` returning `ScanResponse`; a request in flight blocks new codes.
- `ResultCard({ result })` — VALID green "Welcome in" + holder + type; ALREADY_USED amber "Already used" + "Checked in by you just now" when `byYou`, else "at {gate} by {scanner}, {time}"; WRONG_GATE red "Wrong gate — use {gates}"; WRONG_EVENT red with the event name; INVALID red with the message.
- Scanner home: list of `GET /checkin/events` (title, date, venue, organizer, "Your event" / "Door staff"); empty state "You're not on the door team for any upcoming event".

- [ ] **Step 1: Write failing tests**

```ts
test('scanner home lists events I can scan')
test('picking a gate then scanning shows the result')     // mocked CameraScanner fires onCode('tok') → scan called with { eventId, gateId, token: 'tok' }; ResultCard VALID with holder
test('manual code entry scans by code')                   // type "sp-aaaa-2222" → scan({ code: 'sp-aaaa-2222' })
test('result clears after 2.5 s and on tap')
test('already used by me reads "Checked in by you just now"')
test('wrong gate names the allowed gates')
test('access lost shows a clear message')                 // scan rejects 404 → "You no longer have access to this event's door"
```

- [ ] **Step 2: Run — expect FAIL.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run — expect PASS**; lint; build; full backend suite; browser check on localhost: organizer opens `/scan`, picks the event and a gate, scans a ticket QR shown on another screen (or types its code) → Valid, rescan → Already used.
- [ ] **Step 5: Commit** — `feat(web): door scanner with camera, manual entry and gates`

---

## Self-review notes

- Spec coverage: §8.6 scanner for organizer and assigned staff (T1, T2, T5), camera via `html5-qrcode` with manual fallback (T5), signature + event + status checks (T1), unique-constraint concurrency (T1), VALID / ALREADY_USED (when, where, who) / INVALID / WRONG_EVENT / WRONG_GATE (T1, T3), every scan in `scan_logs` (T1), live counters overall, per gate, per staff (T3, T4), staff invited by email with held invites applied at first sign-in (T2), removal effective next scan (T2), staff cannot see sales/payouts/attendees/settings (T2), gates with ticket-type limits and kept history (T3).
- Deferred by design: offline scanning (spec), undoing a check-in and staff roles other than `SCANNER` (not in spec; the `role` column allows them later), QR nonce rotation for transfers (later), staff push notifications (Plan 9).
- Access lives in data, not JWT roles, so a door staff member needs no ORGANIZER role and gains nothing outside the organizer that invited them.
