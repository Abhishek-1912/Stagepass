# Stagepass Booking & Payments Implementation Plan (Plan 4 of 11)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A signed-in attendee picks tickets, sees the exact price (ticket + convenience fee + GST), pays through Razorpay (test mode) and gets a confirmed booking with ticket records — with seats held during payment, payments verified only on the backend, every confirmation idempotent, and refunds working for cancellations, reschedules and in-policy requests.

**Architecture:** New backend modules `booking` (fees, quotes, holds, bookings, confirmation, expiry, refunds, ledger, invoices) and `payment` (Razorpay client, signature checks, verify endpoint, webhooks, disputes). Inventory changes are single conditional SQL updates; confirmation locks the booking row (`SELECT … FOR UPDATE`) so the browser callback, the webhook and webhook retries converge on one result. Ticket rows are created at confirmation; QR codes, PDFs and emails are Plan 5. Web adds the ticket picker, checkout page with Razorpay Checkout, and My bookings / booking detail.

**Tech Stack:** as Plans 1–3; Razorpay REST API v1 (orders, payments, refunds) over `RestClient` with HTTP Basic auth; Razorpay Checkout JS (`https://checkout.razorpay.com/v1/checkout.js`) loaded on demand.

**Spec:** `docs/superpowers/specs/2026-11-05-stagepass-design.md` — §8.2 fees, §8.3 booking & payment, §8.4 refunds & disputes, §8.4a money rules, §11 reliability.

## Global Constraints

- All Plan 1–3 Global Constraints apply. Money is integer paise end to end; rounding is **half-up to the paisa at each step**, server-side only.
- **Fees** (from `platform_settings.fees`, overridable by `events.convenience_fee_override` `{bps, flatPaise}` and `events/organizers.commission_bps_override`):
  `fee = round(subtotal × bps / 10000) + flatPaise × ticketCount`; `feeGst = round(fee × gstBps / 10000)`; `total = subtotal + fee + feeGst`; `commission = round(subtotal × commissionBps / 10000)`. Free orders (`subtotal = 0`) have fee 0, GST 0, commission 0 and skip Razorpay.
  Worked examples (default 250 bps + ₹10/ticket, GST 1800 bps): 2 × ₹499 → subtotal 99800, fee 4495, GST 809, total **105104**; 3 × ₹123.45 → subtotal 37035, fee 3926, GST 707, total **41668**.
- **Holds:** `stagepass.hold-minutes` (10). Creating a booking takes inventory with `UPDATE ticket_inventory SET held = held + :q WHERE ticket_type_id = :id AND total - sold - held >= :q` per item inside one transaction; any 0-row update → 409 `SOLD_OUT` and full rollback. One live `PENDING_PAYMENT` booking per user per event: creating another expires the previous one first (holds released).
- **Limits:** per type `min_per_order..max_per_order`; per event `max_tickets_per_user` counts tickets in the user's `CONFIRMED`/`PARTIALLY_REFUNDED` bookings + live pending bookings; sale window `sale_starts_at ≤ now < sale_ends_at` (nulls open); only `ACTIVE` types of `PUBLISHED`, not-yet-started events (organizer must be `APPROVED`).
- **Payments:** Razorpay test keys. A booking is confirmed only after (a) the checkout signature `HMAC_SHA256(order_id + "|" + payment_id, key_secret)` verifies **and** the payment fetched from Razorpay is `captured` (or `authorized`, which we capture) for exactly the booking's `total_paise` in `INR` on that order — or (b) a webhook whose `X-Razorpay-Signature` = `HMAC_SHA256(rawBody, webhook_secret)` reports the same. Comparisons are constant-time (`MessageDigest.isEqual`). Browser success alone never confirms.
- **Idempotency:** `POST /bookings` honours `Idempotency-Key` (same user + key → the same booking, 200); webhook events dedupe on `X-Razorpay-Event-Id` (`webhook_events` unique); confirmation is a no-op on an already-`CONFIRMED` booking; `payments.razorpay_payment_id` and `refunds.razorpay_refund_id` are unique.
- **Refund rules** (§8.4, §8.4a): event cancelled → full refund including fee + GST; inside the reschedule window → full refund including fee; attendee request within policy → ticket price only (fee + its GST kept, per `refundFeeOnAttendeeRequest=false`); outside policy → `PENDING_APPROVAL` for the organizer. Partial refunds are per ticket; fee, GST and commission are split pro-rata by ticket price (half-up; last ticket absorbs rounding so totals reconcile exactly).
- **Ledger** (`organizer_ledger_entries`, signed paise): on confirm `SALE +subtotal`, `COMMISSION −commission`; on refund `REFUND −ticketPortion`, `COMMISSION +commissionPortion`. Gateway fee/tax recorded on `payments` (Stagepass absorbs them).
- **GST invoices:** when `stagepass.company.gstin` is set, a paid booking gets an `invoices` row for the convenience fee: number `SP/{FY}/{seq:06d}` with FY `2026-27` (April–March, IST) from `invoice_sequences` (row-locked increment); CGST+SGST (half each, odd paisa to CGST) when `buyer_state` equals `stagepass.company.state`, else IGST. Without a GSTIN, no invoice rows are created (logged once). *Tax specifics to be confirmed by the company's accountant.*
- New config (`application.properties` → env): `razorpay.key-id` (`RAZORPAY_KEY_ID`), `razorpay.key-secret` (`RAZORPAY_KEY_SECRET`), `razorpay.webhook-secret` (`RAZORPAY_WEBHOOK_SECRET`), `razorpay.base-url` (default `https://api.razorpay.com`), `stagepass.company.name` / `gstin` / `state` / `address`. Web: `VITE_RAZORPAY_KEY_ID` is **not** needed — the API returns the public key id with each order.
- Audit: `BOOKING_CONFIRMED`, `BOOKING_EXPIRED`, `PAYMENT_MISMATCH`, `REFUND_REQUESTED`, `REFUND_APPROVED`, `REFUND_DECLINED`, `REFUND_PROCESSED`, `REFUND_FAILED`, `DISPUTE_RECORDED`.

## Review Focus

1. **Two buyers race for the last tickets** → exactly the available number are held; the rest get 409 `SOLD_OUT`; inventory never goes over `total` — tested in Task 3 (16 threads).
2. **Forged success** — a browser posts a wrong signature, or a payment for a different amount/order, or a webhook with a bad signature → booking stays `PENDING_PAYMENT`, mismatch audited, webhook answers 400 — tested in Tasks 5, 6.
3. **The same payment arrives three times** (verify call + `payment.captured` + webhook retry, also concurrently) → one confirmation, one set of tickets, one SALE ledger entry, one invoice — tested in Tasks 5, 6.
4. **Payment lands after the hold expired** and the tickets were sold to someone else → booking not confirmed, automatic full refund, audit — tested in Task 6.
5. **Double-click on Pay / refresh during checkout** → same booking (idempotency key), no second hold, and the checkout page resumes the same Razorpay order until the hold expires — tested in Tasks 3, 9.

---

### Task 1: Fees, settings and configuration

**Files:**
- Create in `com.stagepass.booking`: `service/FeeCalculator.java`, `service/FeeSettings.java`, `service/FeeSettingsRepository.java`, `domain/PriceBreakdown.java`
- Create: `payment/config/RazorpayProperties.java`, `common/config/CompanyProperties.java`
- Modify: `application.properties`, `application-local.properties.example`, test properties, `render.yaml`, README env table
- Test: `booking/FeeCalculatorTest.java`, `booking/FeeSettingsRepositoryTest.java`

**Interfaces:**
- `record FeeSettings(int convenienceFeeBps, long convenienceFeeFlatPaise, int gstBps, int commissionBps, boolean refundFeeOnAttendeeRequest)`.
- `FeeSettingsRepository.current(): FeeSettings` (reads `platform_settings` key `fees`, cached 60 s, `evict()`); `FeeSettingsRepository.forEvent(UUID eventId): FeeSettings` applies `events.convenience_fee_override` (`{"bps":…, "flatPaise":…}`), event `commission_bps_override`, then organizer `commission_bps_override`.
- `record PriceBreakdown(long subtotalPaise, long feePaise, long feeGstPaise, long totalPaise, long commissionPaise, int ticketCount)`.
- `FeeCalculator.price(long subtotalPaise, int ticketCount, FeeSettings s): PriceBreakdown` (pure, static) and `static long halfUp(long numerator, long denominator)`.
- `FeeCalculator.split(PriceBreakdown whole, List<Long> ticketPrices): List<PriceBreakdown>` — pro-rata per ticket; sums of each component equal the whole exactly.
- `record RazorpayProperties(String keyId, String keySecret, String webhookSecret, @DefaultValue("https://api.razorpay.com") String baseUrl)` with `configured()`; `record CompanyProperties(String name, String gstin, String state, String address)` prefix `stagepass.company`.

- [ ] **Step 1: Write failing tests**

```java
@Test void twoTicketsAt499()                    // price(99800, 2, defaults) == (99800, 4495, 809, 105104, 0, 2)
@Test void halfUpRoundingAtEachStep()           // price(37035, 3, defaults) == fee 3926, gst 707, total 41668
@Test void freeOrderHasNoFees()                 // price(0, 4, defaults) all zero
@Test void commissionIsAppliedToSubtotal()      // commissionBps 500, subtotal 99800 → 4990
@Test void splitReconcilesExactly()             // whole(3 tickets 12345) split → sums equal whole for every component
@Test void eventOverrideBeatsPlatformDefault()  // FeeSettingsRepositoryTest: event override {bps:100, flatPaise:0} → forEvent bps 100
@Test void organizerCommissionOverrideApplies()
```

- [ ] **Step 2: Run — expect FAIL.** `cd stagepass-api && ./mvnw -q test -Dtest='FeeCalculatorTest,FeeSettingsRepositoryTest'`
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run — expect PASS**, then full suite.
- [ ] **Step 5: Commit** — `feat(booking): fee calculator and payment configuration`

---

### Task 2: Booking domain model

**Files:**
- Create in `com.stagepass.booking.domain`: `Booking.java`, `BookingStatus.java`, `BookingItem.java`, `Ticket.java`, `TicketStatus.java`, `Refund.java`, `RefundStatus.java`, `LedgerEntry.java`, `LedgerType.java`, `Invoice.java`; in `com.stagepass.payment.domain`: `Payment.java`, `PaymentStatus.java`, `WebhookEvent.java`, `Dispute.java`
- Create repositories for each (`booking.repository.*`, `payment.repository.*`)
- Create: `db/migration/V10__booking_payments.sql`
- Test: `booking/BookingMappingTest.java`

**Interfaces:**
- V10 adds: `bookings.cancel_reason text`, `bookings.refundable_until timestamptz`; `tickets.refunded_at timestamptz`; `refunds.kind text check (kind in ('CANCELLATION','RESCHEDULE','ATTENDEE_REQUEST','ORGANIZER','ADMIN','LATE_PAYMENT'))`, `refunds.fee_paise bigint not null default 0`, `refunds.fee_gst_paise bigint not null default 0`, `refunds.commission_paise bigint not null default 0`, `refunds.ticket_ids uuid[] not null default '{}'`, `refunds.failure_reason text`; `payments.order_amount_paise bigint`; index `bookings (user_id, event_id, status)`; unique partial index `bookings_one_pending_per_user_event on bookings (user_id, event_id) where status = 'PENDING_PAYMENT'`.
- Entities map every column (V3, V4, V6, V10); `Booking` has `@OneToMany items`, `@OneToMany tickets`; repositories include `BookingRepository.findByIdAndUserId`, `findByUserIdAndIdempotencyKey`, `findForUpdate(UUID id)` (`@Lock(PESSIMISTIC_WRITE)`), `findByUserIdOrderByCreatedAtDesc(Pageable)`, `findByEventIdAndStatusIn`, `findExpiredPending(OffsetDateTime now, Pageable)`; `PaymentRepository.findByRazorpayOrderId`, `findByRazorpayPaymentId`, `findByBookingId`; `RefundRepository.findByRazorpayRefundId`, `findByBookingId`.

- [ ] **Step 1: Write failing test** — persist booking + 2 items + 3 tickets + payment + ledger entries; reload; assert relationships, enum mapping, and that a second `PENDING_PAYMENT` for the same user+event violates the partial unique index (`DataIntegrityViolationException`).
- [ ] **Step 2: Run — expect FAIL.** `./mvnw -q test -Dtest=BookingMappingTest`
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run — expect PASS**, then full suite.
- [ ] **Step 5: Commit** — `feat(booking): booking, payment, refund and ledger model`

---

### Task 3: Quotes and booking creation with holds

**Files:**
- Create in `com.stagepass.booking`: `api/QuoteRequest.java`, `api/QuoteResponse.java`, `api/CreateBookingRequest.java`, `api/BookingResponse.java`, `api/BookingController.java`, `api/PublicQuoteController.java`, `service/BookingRules.java`, `service/InventoryService.java`, `service/BookingService.java`, `service/BookingCodes.java`
- Test: `booking/QuoteTest.java`, `booking/CreateBookingTest.java`, `booking/OversellRaceTest.java`

**Interfaces:**
- `POST /api/v1/public/quote` body `QuoteRequest(@NotNull UUID eventId, @NotEmpty @Size(max=20) List<Item(UUID ticketTypeId, @Min(1) int quantity)> items)` → `QuoteResponse(List<Line(UUID ticketTypeId, String name, int quantity, long unitPricePaise, long amountPaise)> lines, PriceBreakdown price, int holdMinutes)`; runs the same `BookingRules` as creation (except the per-user limit when anonymous) and returns 400 `VALIDATION_FAILED` with field `items[i]` messages, 409 `SOLD_OUT` when not enough available.
- `POST /api/v1/bookings` (auth) header `Idempotency-Key` (required, 8–100 chars) body `CreateBookingRequest(@NotNull UUID eventId, @NotEmpty List<Item> items, @NotBlank @Size(max=120) attendeeName, @NotBlank @Email attendeeEmail, @Pattern(mobile) attendeePhone, String buyerState)` → 201 `BookingResponse` (or 200 with the existing booking for a repeated key; 409 `CONFLICT` if the key was used for a different payload).
- `BookingResponse(UUID id, String code, String status, UUID eventId, String eventTitle, String eventSlug, OffsetDateTime eventStartsAt, String venueName, String city, List<ItemLine> items, PriceBreakdown price, OffsetDateTime holdExpiresAt, CheckoutInfo checkout /* null unless PENDING_PAYMENT and paid */, List<TicketLine> tickets, String attendeeName, String attendeeEmail, String invoiceNumber, RefundInfo refund)`; `CheckoutInfo(String razorpayKeyId, String razorpayOrderId, long amountPaise, String currency, String companyName)`.
- `BookingService.create(UUID userId, String idempotencyKey, CreateBookingRequest r): BookingResponse` — validates rules, expires the user's other pending booking for the event, holds inventory, writes booking + items, computes price; for `total = 0` confirms immediately (Task 5's `BookingConfirmationService.confirmFree`). Paid bookings use three steps so no DB transaction waits on Razorpay: (1) transaction — holds + booking + items; (2) no transaction — `RazorpayClient.createOrder` (Task 4); (3) transaction — `payments` row `CREATED` + return `checkout`. If step 2 fails: release holds, booking `EXPIRED`, 503 `SERVICE_UNAVAILABLE`.
- `BookingCodes.next(): String` — `SP-` + 6 chars from `23456789ABCDEFGHJKMNPQRSTUVWXYZ`, retried on unique collision; ticket codes `SP-XXXX-XXXX`.
- `InventoryService.hold(UUID typeId, int qty): boolean`, `release(UUID typeId, int qty)`, `commit(UUID typeId, int qty)` (held → sold), `returnSold(UUID typeId, int qty)` (refunds).

- [ ] **Step 1: Write failing tests**

```java
// QuoteTest
@Test void quotesTwoTicketsWithFees()                 // 2 × ₹499 → total 105104, holdMinutes 10
@Test void rejectsHiddenEndedAndOverLimitTypes()      // HIDDEN type, sale ended, qty > maxPerOrder, qty < minPerOrder → 400 with items[i]
@Test void unpublishedEventCannotBeQuoted()           // DRAFT → 404
// CreateBookingTest
@Test void createsPendingBookingHoldingInventory()    // held += qty, status PENDING_PAYMENT, holdExpiresAt ≈ now+10m, checkout.razorpayOrderId present (fake Razorpay)
@Test void sameIdempotencyKeyReturnsSameBooking()     // second POST → 200 same id; held unchanged
@Test void reusedKeyWithDifferentItemsIs409()
@Test void newBookingExpiresPreviousPendingOne()      // second create (new key) → first EXPIRED, its hold released
@Test void perUserLimitCountsConfirmedAndPending()    // maxTicketsPerUser 4, has 3 confirmed → requesting 2 → 400 field items
@Test void freeEventConfirmsImmediately()             // ₹0 type → CONFIRMED, tickets issued, no Razorpay call, sold += qty
@Test void razorpayOutageReleasesHolds()              // fake returns 500 → 503, booking EXPIRED, held back to 0
@Test void requiresLogin()                            // no token → 401
// OversellRaceTest (no @Transactional; real commits)
@Test void sixteenBuyersForTenTickets()               // 16 users × 1 ticket concurrently, total 10 → exactly 10 PENDING, 6 × 409 SOLD_OUT, held == 10
```

- [ ] **Step 2: Run — expect FAIL.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run — expect PASS**, then full suite.
- [ ] **Step 5: Commit** — `feat(booking): quotes and race-safe booking holds`

---

### Task 4: Razorpay client

**Files:**
- Create in `com.stagepass.payment`: `service/RazorpayClient.java`, `service/RazorpaySignatures.java`, `service/RazorpayException.java`, `api/dto/RazorpayOrder.java`, `api/dto/RazorpayPayment.java`, `api/dto/RazorpayRefund.java`
- Test: `payment/RazorpaySignaturesTest.java`, `payment/RazorpayClientTest.java`; `support/FakeRazorpayServer.java`

**Interfaces:**
- `RazorpayClient.createOrder(long amountPaise, String receipt, Map<String,String> notes): RazorpayOrder` (`POST /v1/orders`, `currency=INR`); `fetchPayment(String paymentId): RazorpayPayment` (`GET /v1/payments/{id}`: `id, orderId, amount, currency, status, method, fee, tax, errorDescription`); `capture(String paymentId, long amountPaise)`; `refund(String paymentId, long amountPaise, String receipt, Map<String,String> notes): RazorpayRefund` (`POST /v1/payments/{id}/refund`, `speed=normal`); Basic auth `keyId:keySecret`; 5 s connect / 15 s read timeouts; 4xx → `RazorpayException(retryable=false)`, 5xx/IO → `retryable=true`; unconfigured → `RazorpayException` → mapped to 503.
- `RazorpaySignatures.checkout(String orderId, String paymentId, String signature): boolean`; `webhook(byte[] rawBody, String signature): boolean` — HMAC-SHA256 hex, constant-time compare, false on null/blank.
- `FakeRazorpayServer` (JDK `HttpServer`): records requests, scriptable responses per path, helpers `payment(id, orderId, amount, status)`, `sign(orderId, paymentId, secret)`, `signWebhook(body, secret)`.

- [ ] **Step 1: Write failing tests**

```java
@Test void checkoutSignatureMatchesRazorpayFormula()  // HMAC_SHA256("order_X|pay_Y", "secret") hex; wrong one false; blank false
@Test void webhookSignatureUsesRawBody()              // whitespace change in body → false
@Test void createsOrderWithBasicAuthAndPaise()        // fake receives Authorization "Basic base64(key:secret)", amount 105104, currency INR, receipt SP-…
@Test void fetchesPaymentFields()
@Test void refundsWithNormalSpeed()
@Test void serverErrorsAreRetryableClientErrorsAreNot()
```

- [ ] **Step 2: Run — expect FAIL.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run — expect PASS**, then full suite.
- [ ] **Step 5: Commit** — `feat(payment): Razorpay client and signature checks`

---

### Task 5: Verify endpoint and idempotent confirmation

**Files:**
- Create: `booking/service/BookingConfirmationService.java`, `booking/service/TicketIssuer.java`, `booking/service/LedgerService.java`, `booking/service/InvoiceService.java`, `payment/api/PaymentVerifyController.java`, `payment/api/VerifyPaymentRequest.java`, `payment/service/PaymentService.java`
- Modify: `catalog/service/EventLifecycleService` — none yet (Task 8)
- Test: `booking/ConfirmationTest.java`, `booking/InvoiceServiceTest.java`

**Interfaces:**
- `POST /api/v1/payments/verify` (auth, booking owner) body `VerifyPaymentRequest(@NotNull UUID bookingId, @NotBlank razorpayOrderId, @NotBlank razorpayPaymentId, @NotBlank razorpaySignature)` → `BookingResponse`. Bad signature or order not belonging to the booking → 400 `VALIDATION_FAILED` (no state change, audit `PAYMENT_MISMATCH`). Valid signature → `PaymentService.settle(paymentId)`.
- `PaymentService.settle(String razorpayPaymentId): SettleResult` — fetches the payment; if `authorized` captures it; requires `captured`, `currency = INR`, `amount = payments.amount_paise`, `order_id` matches; mismatch → payment `FAILED` with reason + audit `PAYMENT_MISMATCH` (booking untouched); success → `BookingConfirmationService.confirmPaid(bookingId, payment)`. Stores `method`, `fee`, `tax` (`gateway_fee_paise`, `gateway_tax_paise`), raw JSON.
- `BookingConfirmationService.confirmPaid(UUID bookingId, CapturedPayment p): Outcome` — `findForUpdate`; `CONFIRMED`/`PARTIALLY_REFUNDED`/`REFUNDED` → `ALREADY_CONFIRMED` no-op; `PENDING_PAYMENT` → commit holds (`held → sold`), status `CONFIRMED`, `confirmed_at`, `TicketIssuer.issue` (one ticket per seat, holder = attendee name, random `qr_nonce`), `LedgerService.recordSale`, `InvoiceService.issueFor` (if GSTIN), popularity `+= quantity`, audit `BOOKING_CONFIRMED`; `EXPIRED`/`CANCELLED` → late-payment path (Task 6). `confirmFree(UUID bookingId)` same without payment.
- `InvoiceService.issueFor(Booking b): Optional<Invoice>` — FY from `confirmed_at` in IST; `insert into invoice_sequences … on conflict (financial_year) do update set last_seq = invoice_sequences.last_seq + 1 returning last_seq`.

- [ ] **Step 1: Write failing tests**

```java
@Test void validPaymentConfirmsAndIssuesTickets()      // 3 tickets, sold += 3, held −= 3, SALE ledger = subtotal, status CONFIRMED
@Test void forgedSignatureChangesNothing()             // 400, booking PENDING_PAYMENT, audit PAYMENT_MISMATCH
@Test void amountMismatchIsNotConfirmed()              // fake payment amount 100 → payment FAILED, booking pending
@Test void otherUsersBookingIs404()
@Test void authorizedPaymentIsCaptured()               // fake status authorized → capture call made → confirmed
@Test void verifyTwiceConfirmsOnce()                   // second verify → same response, 3 tickets total, one SALE entry
@Test void concurrentConfirmationsConvergeToOne()      // 6 threads call settle() → exactly one set of tickets/ledger/invoice
// InvoiceServiceTest
@Test void sameStateSplitsCgstSgst()                   // fee GST 809 → CGST 405, SGST 404, number SP/2026-27/000001
@Test void otherStateUsesIgst()
@Test void noGstinNoInvoice()
@Test void financialYearRollsOverInApril()             // confirmed 2027-03-31T23:00+05:30 → 2026-27; 2027-04-01T00:30+05:30 → 2027-28
```

- [ ] **Step 2: Run — expect FAIL.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run — expect PASS**, then full suite.
- [ ] **Step 5: Commit** — `feat(payment): backend-verified, idempotent booking confirmation`

---

### Task 6: Webhooks, late payments, disputes

**Files:**
- Create: `payment/api/RazorpayWebhookController.java`, `payment/service/WebhookService.java`, `booking/service/LatePaymentHandler.java`
- Test: `payment/WebhookTest.java`, `booking/LatePaymentTest.java`

**Interfaces:**
- `POST /api/v1/webhooks/razorpay` (public; raw body read as `byte[]`) → 200 `{"status":"ok"}`; missing/invalid `X-Razorpay-Signature` → 400 (no processing, warn log); duplicate `X-Razorpay-Event-Id` → 200 without reprocessing. Events: `payment.captured`, `order.paid` → `PaymentService.settle`; `payment.failed` → payment `FAILED` + reason (booking stays pending until expiry so the user can retry); `refund.processed` / `refund.failed` → `RefundService.onProcessed/onFailed` (Task 8); `payment.dispute.created|won|lost|closed` → upsert `disputes`, audit `DISPUTE_RECORDED`; others stored and ignored. Processing errors → 500 so Razorpay retries (event row kept with `processed_at` null; dedupe only skips processed events).
- `LatePaymentHandler.handle(Booking b, CapturedPayment p)` — for `EXPIRED`: try `InventoryService.hold` for every item again; all succeed → confirm normally; otherwise refund the full captured amount (`kind LATE_PAYMENT`), booking `CANCELLED` with `cancel_reason = 'Tickets sold out before payment completed'`, audit; `CANCELLED` booking → always full refund.

- [ ] **Step 1: Write failing tests**

```java
@Test void capturedWebhookConfirmsBooking()
@Test void badSignatureIs400AndIgnored()
@Test void duplicateEventIdIsProcessedOnce()           // same body+event id twice → one confirmation
@Test void verifyThenWebhookThenRetryConfirmOnce()     // Review Focus 3
@Test void failedPaymentKeepsBookingPending()
@Test void disputeIsRecorded()
@Test void processingErrorReturns500ForRetry()          // fake fetchPayment 500 → webhook 500, event not marked processed
// LatePaymentTest
@Test void latePaymentWithStockStillConfirms()          // booking EXPIRED, inventory available → CONFIRMED
@Test void latePaymentAfterSellOutIsRefunded()          // inventory taken by others → refund call for full amount, booking CANCELLED
```

- [ ] **Step 2: Run — expect FAIL.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run — expect PASS**, then full suite.
- [ ] **Step 5: Commit** — `feat(payment): Razorpay webhooks, late payments and disputes`

---

### Task 7: Hold expiry and popularity jobs

**Files:**
- Create: `booking/service/HoldExpiryJob.java`, `discovery/service/PopularityJob.java`
- Test: `booking/HoldExpiryJobTest.java`, `discovery/PopularityJobTest.java`

**Interfaces:**
- `HoldExpiryJob.expireDue(): int` (`@Scheduled(fixedDelay = 60s)`) — `PENDING_PAYMENT` with `hold_expires_at < now()`, processed in batches of 100 with `FOR UPDATE SKIP LOCKED`; releases holds, status `EXPIRED`, audit `BOOKING_EXPIRED`. A booking whose payment is `CAPTURED` but not yet confirmed is skipped (confirmation will handle it).
- `PopularityJob.recompute(): int` (`@Scheduled(cron = "0 5 * * * *")`) — `popularity_score = tickets confirmed in the last 7 days + 0.5 × tickets confirmed 8–30 days ago` for PUBLISHED events (saves added in Plan 9).

- [ ] **Step 1: Write failing tests**

```java
@Test void expiresDueHoldsAndReleasesInventory()
@Test void keepsUnexpiredAndCapturedBookings()
@Test void popularityWeightsRecentSales()
```

- [ ] **Step 2: Run — expect FAIL.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run — expect PASS**, then full suite.
- [ ] **Step 5: Commit** — `feat(booking): hold expiry and popularity jobs`

---

### Task 8: Refunds — cancellation, reschedule, attendee requests, organizer decisions

**Files:**
- Create: `booking/service/RefundService.java`, `booking/service/RefundEligibility.java`, `booking/api/MyBookingsController.java`, `booking/api/RefundRequest.java`, `booking/api/OrganizerRefundController.java`, `booking/api/RefundDecisionRequest.java`
- Modify: `catalog/service/EventLifecycleService.onCancelled` (calls `RefundService.refundEventCancellation`), `discovery` none
- Test: `booking/RefundServiceTest.java`, `booking/EventCancellationRefundTest.java`, `booking/MyBookingsTest.java`

**Interfaces:**
- `RefundEligibility.forBooking(Booking b, OffsetDateTime now): Eligibility(boolean selfServe, String kind /* RESCHEDULE | ATTENDEE_REQUEST | NONE */, boolean includesFee, OffsetDateTime until, String reason)` — reschedule window open → `RESCHEDULE` full; policy `UNTIL_HOURS_BEFORE` and `now < starts_at − cutoff` → `ATTENDEE_REQUEST` (fee per `refundFeeOnAttendeeRequest`); otherwise not self-serve (request goes to organizer).
- `RefundService.request(UUID userId, UUID bookingId, RefundRequest(List<UUID> ticketIds /* empty = all valid */, @Size(max=500) String reason)): BookingResponse` — self-serve → `issue(...)` immediately; otherwise `refunds` row `PENDING_APPROVAL` (no Razorpay call), audit `REFUND_REQUESTED`. Only `VALID` tickets; checked-in tickets can't be refunded.
- `RefundService.issue(Booking b, List<Ticket> tickets, kind, includesFee, actorId)` — computes portions with `FeeCalculator.split`, calls `RazorpayClient.refund` (free bookings skip it and complete immediately), saves `PROCESSING`, tickets `REFUNDED` + `refunded_at`, inventory `returnSold`, booking `PARTIALLY_REFUNDED`/`REFUNDED`, ledger `REFUND`/`COMMISSION` reversal; Razorpay error → refund `FAILED` with reason, tickets untouched, 503 to caller.
- `onProcessed(String razorpayRefundId)` → `PROCESSED`, audit; `onFailed(id, reason)` → `FAILED`, tickets back to `VALID`, inventory re-sold if still available else stays released (flag for admin), audit `REFUND_FAILED`.
- `refundEventCancellation(UUID eventId, UUID actorId)` — every `CONFIRMED`/`PARTIALLY_REFUNDED` booking: refund all valid tickets with fee (`kind CANCELLATION`); pending bookings expired.
- Endpoints: `GET /api/v1/me/bookings?scope=upcoming|past&page&size` → `PageResponse<BookingSummary(UUID id, String code, String status, String eventTitle, String eventSlug, String coverImageUrl, OffsetDateTime eventStartsAt, String city, int ticketCount, long totalPaise)>`; `GET /api/v1/me/bookings/{id}` → `BookingResponse` (+ `refund: RefundInfo(boolean selfServe, String kind, boolean includesFee, OffsetDateTime until, List<RefundLine> history)`); `POST /api/v1/me/bookings/{id}/refunds` `RefundRequest`; organizer: `GET /api/v1/organizer/events/{eventId}/refund-requests`, `POST /api/v1/organizer/refund-requests/{refundId}/approve|decline` (`RefundDecisionRequest(@Size(max=500) note)`), own events only.

- [ ] **Step 1: Write failing tests**

```java
@Test void attendeeRefundInsidePolicyKeepsFee()       // 2 × 499, policy 48h, refund 1 ticket → refund amount 49900, ticket REFUNDED, sold −1, booking PARTIALLY_REFUNDED, ledger REFUND −49900
@Test void rescheduleWindowRefundsFeeToo()            // reschedule_refund_until future → amount includes fee+GST share
@Test void outsidePolicyNeedsOrganizerApproval()      // → PENDING_APPROVAL, no Razorpay call; organizer approve → PROCESSING; decline → DECLINED
@Test void otherOrganizerCannotDecide()               // 404
@Test void refundWebhookMarksProcessed()
@Test void failedRefundRestoresTickets()
@Test void checkedInTicketCannotBeRefunded()
@Test void freeBookingRefundSkipsRazorpay()
// EventCancellationRefundTest
@Test void cancellingEventRefundsEveryBookingInFull()  // 2 confirmed bookings → 2 refunds incl. fee, all tickets REFUNDED, pending booking EXPIRED
// MyBookingsTest
@Test void listsUpcomingAndPastBookings()
@Test void bookingDetailIsOwnerOnly()
@Test void detailShowsRefundEligibility()
```

- [ ] **Step 2: Run — expect FAIL.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run — expect PASS**, then full suite.
- [ ] **Step 5: Commit** — `feat(booking): refunds for cancellations, reschedules and attendee requests`

---

### Task 9: Web — ticket picker and checkout

**Files:**
- Create: `stagepass-web/src/features/checkout/api.ts`, `checkout/types.ts`, `checkout/TicketPicker.tsx`, `checkout/CheckoutPage.tsx`, `checkout/PriceSummary.tsx`, `checkout/HoldCountdown.tsx`, `checkout/razorpay.ts`, `checkout/idempotency.ts`
- Modify: `features/event/TicketList.tsx` (picker replaces the disabled button), `features/event/EventPage.tsx` (mobile bar "Book" scrolls to the picker), `features/event/bookLabel.ts`, `src/app/router.tsx` (`/checkout/:slug` RequireAuth, `/checkout/booking/:bookingId` RequireAuth)
- Test: `checkout/TicketPicker.test.tsx`, `checkout/CheckoutPage.test.tsx`, `checkout/razorpay.test.ts`, `checkout/idempotency.test.ts`

**Interfaces:**
- `TicketPicker({ event })` — steppers per `ON_SALE` type bounded by `minPerOrder/maxPerOrder` (first increment jumps to `minPerOrder`) and `maxTicketsPerUser` across types; live price via `useQuote` (debounced 250 ms); "Continue" → `/checkout/{slug}?items=typeId:qty,…` (signed-out users go through `/login?next=…`).
- `CheckoutPage` — reads items; shows lines + `PriceSummary` (subtotal, convenience fee, GST on fee, total; "Free" when 0); attendee form (name/email/phone prefilled from `useMe`, buyer state select); "Pay ₹X" → `createBooking` with `idempotencyKeyFor(slug, items)` → `openRazorpay(checkout, prefill)` → `verifyPayment` → navigate `/account/bookings/{id}?status=success`; free → straight to booking. `/checkout/booking/:bookingId` resumes a pending booking (reuses its order, shows `HoldCountdown`; at 0 shows "Hold expired — start again").
- `loadRazorpay(): Promise<RazorpayCtor>` (injects the script once; rejects after 15 s); `openRazorpay(info: CheckoutInfo, prefill, handlers): Promise<RazorpaySuccess>` — rejects with `'dismissed'` on modal close and with Razorpay's error description on `payment.failed`.
- `idempotencyKeyFor(slug, items): string` — stable per tab session (`sessionStorage`, random UUID per cart signature) so refresh/double-click reuse the key and a changed cart gets a new one.
- Errors: 409 `SOLD_OUT` → toast + refresh event data; 503 → "Payments are temporarily unavailable"; validation → inline.

- [ ] **Step 1: Write failing tests**

```ts
test('picker respects min/max per order and the per-person limit')
test('picker hides sold-out and not-started types from steppers')
test('checkout shows the server price breakdown')               // mocked quote 105104 → ₹1,051.04 total, fee ₹44.95, GST ₹8.09
test('pay creates booking with idempotency key then verifies')  // mocked createBooking + openRazorpay + verify
test('dismissing Razorpay keeps the booking resumable')          // navigates to /checkout/booking/{id}
test('free order skips Razorpay')
test('sold out on create shows a toast')
test('idempotency key is stable for the same cart and changes with it')
test('loadRazorpay injects the script once')
```

- [ ] **Step 2: Run — expect FAIL.** `cd stagepass-web && npx vitest run`
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run — expect PASS**; lint; build.
- [ ] **Step 5: Commit** — `feat(web): ticket picker and Razorpay checkout`

---

### Task 10: Web — My bookings, booking detail, refund requests; setup docs

**Files:**
- Create: `features/bookings/api.ts`, `bookings/MyBookingsPage.tsx`, `bookings/BookingDetailPage.tsx`, `bookings/RefundDialog.tsx`, `bookings/BookingStatusBadge.tsx`
- Modify: `src/app/router.tsx` (`/account/bookings`, `/account/bookings/:id`), `MobileTabBar` Tickets → `/account/bookings`, `README.md` (Razorpay test setup: keys, webhook URL, local tunnel note, test cards/UPI `success@razorpay`), `docs/superpowers/specs` none
- Test: `bookings/MyBookingsPage.test.tsx`, `bookings/BookingDetailPage.test.tsx`, `bookings/RefundDialog.test.tsx`

**Interfaces:**
- `MyBookingsPage` — tabs Upcoming / Past; cards with cover, date, code, ticket count, total, status badge; empty state → Explore.
- `BookingDetailPage` — success banner when `?status=success`; event summary; ticket list (code, type, holder, status) with a muted note "QR codes for entry are coming soon" (QR images arrive in Plan 5); price summary; invoice number if any; pending → "Complete payment" (resume) with countdown; refund section driven by `refund` info: self-serve → "Refund tickets" (`RefundDialog` selecting tickets, showing amount and whether the fee is refunded); otherwise "Request a refund" (goes to organizer); history list with statuses.
- `RefundDialog` — ticket checkboxes (valid only), computed refund estimate (server returns exact amount after submit), reason field.

- [ ] **Step 1: Write failing tests**

```ts
test('lists upcoming bookings with status and total')
test('success banner after payment')
test('pending booking offers to complete payment')
test('self-serve refund shows the amount and whether the fee is included')
test('outside policy shows request-to-organizer wording')
```

- [ ] **Step 2: Run — expect FAIL.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run — expect PASS**; full backend + web suites; lint; build; browser check end to end in Razorpay test mode (needs the user's test keys): book 2 tickets on the user's event → pay with test UPI `success@razorpay` → booking CONFIRMED with 2 tickets → request a refund.
- [ ] **Step 5: Commit** — `feat(web): my bookings, booking detail and refunds`

---

## Self-review notes

- Spec coverage: §8.2 fees + overrides + free (T1, T3); §8.3 steps 1–10 — create with holds (T3), order (T3/T4), checkout + verify (T5, T9), webhook confirm (T6), idempotent confirm (T5), expiry job (T7), late payment (T6), payment.failed (T6); §8.4 refunds incl. partial per ticket, cancellation, attendee request, organizer decision, refund webhooks, disputes (T6, T8); §8.4a gateway fee absorbed (T5 stores fee/tax), negative balances (ledger signed), refund policy, reschedule window (T8), GST invoices (T5); §11 idempotency, transactions, oversell (T3, T5, T6).
- Deferred by design: QR codes, ticket PDFs, all booking/refund/cancellation **emails** (Plan 5, which adds the templates and hooks into `BookingConfirmationService`/`RefundService`), organizer bookings/attendees/refund UI (Plan 6 — APIs for refund decisions exist here), admin transactions/disputes UI and payouts (Plan 8), saves in popularity (Plan 9), rate limiting on booking/verify (Plan 10).
