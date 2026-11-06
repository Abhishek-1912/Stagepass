# Stagepass Tickets, QR, PDFs & Emails Implementation Plan (Plan 5 of 11)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every confirmed ticket carries a signed QR the door can trust; attendees see it on screen, download a ticket PDF and a GST invoice PDF, and get emails for booking confirmed (PDF attached), checkout expired, refund processed or declined, event cancelled, and event date/venue changed.

**Architecture:** New backend module `ticketing` (QR tokens, QR images, ticket/invoice PDFs, download endpoints). The existing `notification` outbox gains a dedupe key, attachments and six templates; an `EmailAttachmentProvider` interface (in `notification`) is implemented by `ticketing` so the PDF is built at send time from current ticket state. Emails are enqueued inside the business transactions of Plan 4 (`BookingConfirmationService`, `BookingExpiryService`, `RefundGateway`, `RefundService`) and through a new catalog hook for date/venue changes. Web renders the QR client-side from the token.

**Tech Stack:** as Plans 1–4; ZXing `com.google.zxing:core` + `javase` 3.5.3 (QR PNG); OpenPDF `com.github.librepdf:openpdf` 2.0.3 (PDF; package `com.lowagie.text`); web `qrcode` 1.5.x + `@types/qrcode`.

**Spec:** `docs/superpowers/specs/2026-11-05-stagepass-design.md` — §8.5 tickets & QR, §8.9 notifications & email, §8.4a (reschedule emails, invoice PDF), §8.3 step 6 (confirmation email).

## Global Constraints

- All Plan 1–4 Global Constraints apply.
- **QR token** (spec §8.5, verbatim): `base64url(ticketId|eventId|qrNonce) + "." + HMAC-SHA256(secret)`. Here: payload = UTF-8 `ticketId + "|" + eventId + "|" + qrNonce`, base64url without padding; signature = base64url (no padding) of the full 32-byte HMAC-SHA256 over the **encoded payload string**, key `stagepass.qr-secret` (`QR_SECRET`). No PII in the token. Verification is constant-time (`MessageDigest.isEqual`) and never throws on malformed input.
- `stagepass.qr-secret` must be at least 32 characters; startup fails with `IllegalStateException("stagepass.qr-secret must be set to at least 32 characters")` otherwise.
- QR tokens are exposed only for `VALID` and `CHECKED_IN` tickets of `CONFIRMED`/`PARTIALLY_REFUNDED` bookings, and only to the booking owner.
- PDFs use OpenPDF built-in Helvetica; money is printed as `Rs. 1,051.04` (Helvetica has no ₹ glyph); dates/times in IST (`Asia/Kolkata`), e.g. `Sat, 14 Nov 2026 · 6:30 PM`. Characters Helvetica cannot encode must not fail rendering.
- Ticket PDF: one A6-portrait page per live ticket (`VALID`/`CHECKED_IN`) with event title, date/time, venue (or "Online"), holder name, ticket type, ticket code, booking code, QR (≥ 45 mm square), event terms (first 600 chars). Filename `stagepass-{bookingCode}.pdf`.
- Invoice PDF (only when an `invoices` row exists): "Tax Invoice", company name/GSTIN/state/address from `stagepass.company.*`, invoice number and date, buyer name + state, line "Convenience fee — booking {code}", taxable value, CGST/SGST or IGST, total. Filename `stagepass-invoice-{number with / replaced by -}.pdf`. *Tax wording to be confirmed by the company's accountant.*
- **Emails** go to `bookings.attendee_email`, are enqueued in the same transaction as the state change, and carry a **dedupe key** (`email_outbox.dedupe_key` unique; enqueue is `insert … on conflict (dedupe_key) do nothing`):
  | Template | When | Dedupe key |
  |---|---|---|
  | `BOOKING_CONFIRMED` (ticket PDF attached) | booking becomes CONFIRMED (paid, free, late) | `booking-confirmed:{bookingId}` |
  | `BOOKING_EXPIRED` | expiry with reason `HOLD_EXPIRED` only | `booking-expired:{bookingId}` |
  | `REFUND_PROCESSED` | refund reaches PROCESSED | `refund-processed:{refundId}` |
  | `REFUND_DECLINED` | organizer declines | `refund-declined:{refundId}` |
  | `EVENT_CANCELLED` | a booking of a cancelled event is refunded or its checkout expired by cancellation | `event-cancelled:{bookingId}` |
  | `EVENT_CHANGED` | date/time/venue change opens the reschedule window | `event-changed:{bookingId}:{rescheduleRefundUntil epoch seconds}` |
- Email copy: subject lines exactly — `You're going to {title}` / `Your checkout for {title} expired` / `Refund of Rs. {amount} processed` (`Your free tickets were cancelled` when 0) / `Update on your refund request` / `{title} has been cancelled` / `{title} has changed`. Every email ends with a button; booking emails link to `{webBaseUrl}/account/bookings/{bookingId}`.
- Resend attachments: `attachments: [{filename, content: base64}]` in the existing `POST /emails` body. If the provider returns no attachment (e.g. all tickets refunded before sending), the email is sent without it.
- Web: QR rendered with `qrcode` (`QRCode.toDataURL(token, { errorCorrectionLevel: 'M', margin: 1, width: 320 })`); downloads fetch with the Supabase token via `api` helpers and save as a Blob.

## Review Focus

1. **The same confirmation arrives three times** (verify + `payment.captured` + webhook retry, or late confirm after a sweep) → exactly one `BOOKING_CONFIRMED` email — test in Task 4.
2. **Ticket refunded before the PDF/email is generated** → refunded tickets have no QR token, are absent from the PDF, and a booking with no live tickets gets 409 on PDF download — tests in Tasks 1, 2.
3. **Holder names and titles outside Latin-1** (e.g. `Asha राव`, emoji in a title) → PDF still renders (characters dropped), no 500 — test in Task 2.
4. **A booking with 20 tickets** → 20 PDF pages, email lists all 20 codes — tests in Tasks 2, 3.
5. **Altered or foreign QR** (changed ticket id, token signed with another secret, random string, empty) → `verify` returns empty, never throws — test in Task 1.

---

### Task 1: QR tokens and QR images

**Files:**
- Create in `com.stagepass.ticketing`: `service/QrTokens.java`, `service/QrClaims.java`, `service/QrImages.java`
- Modify: `pom.xml` (ZXing core + javase 3.5.3), `booking/api/BookingResponse.java` (`TicketLine` + `String qrToken`), `booking/service/BookingViews.java`, README env table (`QR_SECRET`: "32+ random characters; changing it invalidates every issued QR")
- Test: `ticketing/QrTokensTest.java`, `ticketing/QrImagesTest.java`, `booking/MyBookingsTest.java` (add)

**Interfaces:**
- Produces: `record QrClaims(UUID ticketId, UUID eventId, String nonce)`; `@Component QrTokens(StagepassProperties)` with `String sign(UUID ticketId, UUID eventId, String nonce)` and `Optional<QrClaims> verify(String token)` (Plan 7 check-in uses `verify`, then compares `nonce` with `tickets.qr_nonce`); `@Component QrImages` with `byte[] png(String text, int sizePx)` (ZXing `QRCodeWriter`, error correction M, margin 1).
- `BookingResponse.TicketLine(UUID id, String code, UUID ticketTypeId, String ticketTypeName, String holderName, String status, String qrToken /* null unless live ticket of a confirmed booking */)`.

- [ ] **Step 1: Write failing tests**

```java
// QrTokensTest (plain unit test; new QrTokens(props with secret "test-qr-secret-test-qr-secret-0000"))
@Test void roundTrips()                  // verify(sign(t, e, "N0NCE")) == QrClaims(t, e, "N0NCE")
@Test void tokenHasNoPii()               // decoded payload == t + "|" + e + "|N0NCE"; token matches ^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]{43}$
@Test void alteredPayloadFails()         // swap ticket id inside payload, keep signature → empty
@Test void otherSecretFails()            // token from a QrTokens with a different secret → empty
@Test void malformedNeverThrows()        // "", "abc", "a.b", "....", null, 10_000 chars → empty
@Test void shortSecretFailsAtStartup()   // secret "short" → IllegalStateException("stagepass.qr-secret must be set to at least 32 characters")
// QrImagesTest
@Test void pngDecodesBackToTheToken()    // ZXing MultiFormatReader on ImageIO.read(png(token, 320)) == token; image is 320×320
// MyBookingsTest (add)
@Test void liveTicketsCarryQrTokens()    // confirmed booking detail: tickets[*].qrToken verifies to that ticket; after refunding one ticket its qrToken is null; pending booking has no tickets
```

- [ ] **Step 2: Run — expect FAIL.** `cd stagepass-api && ./mvnw -q test -Dtest='QrTokensTest,QrImagesTest,MyBookingsTest'`
- [ ] **Step 3: Implement** `QrTokens`, `QrClaims`, `QrImages`; `BookingViews` fills `qrToken` via `QrTokens.sign(t.getId(), t.getEventId(), t.getQrNonce())` per the Global Constraints rule.
- [ ] **Step 4: Run — expect PASS**, then full suite.
- [ ] **Step 5: Commit** — `feat(ticketing): signed QR tokens and QR images`

---

### Task 2: Ticket and invoice PDFs

**Files:**
- Create in `com.stagepass.ticketing`: `service/TicketPdfRenderer.java`, `service/InvoicePdfRenderer.java`, `service/PdfText.java` (IST date format, `Rs.` money, safe-text filter), `service/TicketDocuments.java`, `api/TicketDocumentController.java`
- Modify: `pom.xml` (OpenPDF 2.0.3)
- Test: `ticketing/TicketPdfRendererTest.java`, `ticketing/TicketDocumentControllerTest.java`

**Interfaces:**
- Consumes: `QrTokens.sign`, `QrImages.png` (Task 1); `BookingRepository.findByIdAndUserId`, `InvoiceRepository.findByBookingId`, `EventRepository`, `TicketTypeRepository`, `CompanyProperties`.
- Produces: `TicketDocuments.ticketPdf(UUID bookingId): Optional<Pdf>` (empty when no live tickets; no ownership check — used by the email provider in Task 3) and `TicketDocuments.invoicePdf(UUID bookingId): Optional<Pdf>`; `record Pdf(String filename, byte[] bytes)`. `PdfText.money(long paise)` → `"Rs. 1,051.04"` (`"Free"` for 0), `PdfText.dateTime(OffsetDateTime)` → `"Sat, 14 Nov 2026 · 6:30 PM"`, `PdfText.safe(String)` → string with characters outside WinAnsi removed and whitespace collapsed.
- Endpoints (auth, owner only, else 404): `GET /api/v1/me/bookings/{id}/tickets.pdf` → 200 `application/pdf`, `Content-Disposition: attachment; filename="stagepass-SP-ABC234.pdf"`, `Cache-Control: no-store`; 409 `INVALID_STATE` when the booking has no live tickets. `GET /api/v1/me/bookings/{id}/invoice.pdf` → same headers with the invoice filename; 404 when there is no invoice.

- [ ] **Step 1: Write failing tests**

```java
// TicketPdfRendererTest (use PdfReader + PdfTextExtractor from OpenPDF)
@Test void onePagePerLiveTicket()              // booking of 3, one REFUNDED → 2 pages; text has booking code, both ticket codes, holder, "Rs. 499", venue name
@Test void twentyTicketsTwentyPages()
@Test void nonLatinTextDoesNotBreakRendering() // holder "Asha राव", title "Diwali 🎆 Night" → valid PDF, text contains "Asha" and "Diwali"
@Test void qrOnPageDecodesToTheTicketToken()   // PdfImageObject of page 1 → ZXing decode == QrTokens.sign(ticket)
@Test void invoiceSplitsTaxes()                // same-state booking: text has "Tax Invoice", GSTIN 36ABCDE1234F1Z5, "SP/2026-27/", "CGST", "Rs. 4.05", "SGST", "Rs. 4.04", "Rs. 53.04"
// TicketDocumentControllerTest
@Test void ownerDownloadsTicketPdf()           // 200, content type application/pdf, filename stagepass-{code}.pdf, body starts with "%PDF"
@Test void otherUserGets404()
@Test void fullyRefundedBookingIs409()
@Test void invoiceDownloadAnd404WithoutInvoice() // paid booking → 200 invoice filename; free booking → 404
```

- [ ] **Step 2: Run — expect FAIL.**
- [ ] **Step 3: Implement.** A6 portrait (105 × 148 mm), brand colour `#6C4CF1` for the header band; QR from `QrImages.png(token, 600)` scaled to 50 mm.
- [ ] **Step 4: Run — expect PASS**, then full suite.
- [ ] **Step 5: Commit** — `feat(ticketing): ticket and GST invoice PDFs`

---

### Task 3: Outbox dedupe, attachments and the six templates

**Files:**
- Create: `db/migration/V12__email_dedupe.sql` (`alter table email_outbox add column dedupe_key text unique`), `notification/service/EmailAttachment.java`, `notification/service/EmailAttachmentProvider.java`, `ticketing/service/TicketPdfAttachments.java`
- Modify: `notification/service/EmailTemplate.java` (+ `BOOKING_CONFIRMED`, `BOOKING_EXPIRED`, `REFUND_PROCESSED`, `REFUND_DECLINED`, `EVENT_CANCELLED`, `EVENT_CHANGED`), `EmailOutbox.java`, `EmailRenderer.java`, `RenderedEmail.java`, `EmailDispatcher.java`, `ResendClient.java`
- Test: `notification/EmailOutboxTest.java` (add), `notification/EmailRendererTest.java` (add), `notification/ResendClientTest.java` (add), `notification/EmailDispatcherTest.java` (add)

**Interfaces:**
- Produces: `EmailOutbox.enqueue(String to, EmailTemplate t, Map<String, ?> data, String dedupeKey): Optional<UUID>` (empty when the key already exists; the old 3-arg method stays, dedupe key null); `record EmailAttachment(String filename, byte[] content)`; `record RenderedEmail(String subject, String html, String text, List<EmailAttachment> attachments)` (existing callers pass `List.of()`); `interface EmailAttachmentProvider { boolean supports(EmailTemplate t); List<EmailAttachment> attachmentsFor(EmailTemplate t, Map<String, ?> data); }` — the dispatcher asks every provider at send time; `TicketPdfAttachments` supports `BOOKING_CONFIRMED` and returns `TicketDocuments.ticketPdf(bookingId)` (payload key `bookingId`).
- Template payload keys (all strings or numbers, set by Task 4): booking emails `bookingId, bookingCode, title, slug, startsAt (ISO), venue, attendeeName`; `BOOKING_CONFIRMED` + `tickets` (list of `{code, type}`), `totalPaise`, `invoiceNumber?`; `REFUND_PROCESSED` + `amountPaise, includesFee (boolean), ticketCount`; `REFUND_DECLINED` + `note?`; `EVENT_CANCELLED` + `refunded (boolean), amountPaise`; `EVENT_CHANGED` + `refundUntil (ISO)`.

- [ ] **Step 1: Write failing tests**

```java
// EmailOutboxTest
@Test void sameDedupeKeyQueuesOnce()           // enqueue twice with "booking-confirmed:X" → second returns empty, one row
// EmailRendererTest
@Test void bookingConfirmedListsEveryTicket()  // 20 tickets → html and text contain all 20 codes; subject "You're going to Sunburn Goa"; link /account/bookings/{id}; date "Sat, 14 Nov 2026 · 6:30 PM"
@Test void refundProcessedSaysWhetherFeeCameBack() // amount 49900 includesFee false → subject "Refund of Rs. 499.00 processed", text mentions convenience fee kept; amount 0 → "Your free tickets were cancelled"
@Test void eventChangedShowsRefundDeadline()   // text contains the refundUntil date and "full refund"
@Test void templatesEscapeHtml()               // title "<b>x</b>" appears escaped in html
// ResendClientTest
@Test void sendsAttachmentsAsBase64()          // fake Resend receives attachments[0].filename and base64 content
// EmailDispatcherTest
@Test void providerAttachmentIsAddedAtSendTime()
@Test void missingAttachmentStillSends()       // provider returns empty list → email SENT without attachments
```

- [ ] **Step 2: Run — expect FAIL.**
- [ ] **Step 3: Implement.** Renderer keeps the existing `build(...)` layout; booking emails add a details table (event, date, venue, booking code) and, for `BOOKING_CONFIRMED`, the ticket list and total.
- [ ] **Step 4: Run — expect PASS**, then full suite.
- [ ] **Step 5: Commit** — `feat(notification): email dedupe, attachments and booking templates`

---

### Task 4: Send the emails from the booking lifecycle

**Files:**
- Create: `booking/service/BookingEmails.java`, `catalog/service/EventChangeHandler.java`, `booking/service/EventChangeEmails.java`
- Modify: `booking/service/BookingConfirmationService.java`, `BookingExpiryService.java`, `RefundGateway.java`, `RefundService.java` (decline + cancellation), `catalog/service/RescheduleWindow.java` (calls `List<EventChangeHandler>` after opening the window; remove the stale "Plan 4 adds the emails" comment in `EventService`)
- Test: `booking/BookingEmailsTest.java`

**Interfaces:**
- Consumes: `EmailOutbox.enqueue(to, template, data, dedupeKey)` (Task 3).
- Produces: `BookingEmails` (`@Component`, call inside the business transaction) — `confirmed(Booking)`, `expired(Booking)`, `refundProcessed(Refund)`, `refundDeclined(Refund)`, `eventCancelled(Booking, long refundedPaise)`, `eventChanged(Event)` (one email per `CONFIRMED`/`PARTIALLY_REFUNDED` booking); `interface EventChangeHandler { void onEventChanged(Event event); }`.
- Hook points: `confirm(...)` → `confirmed`; `BookingExpiryService.expire` when reason `HOLD_EXPIRED` → `expired`, when reason `EVENT_CANCELLED` → `eventCancelled(b, 0)`; `RefundService.refundEventCancellation` right after `apply(...)` for a booking → `eventCancelled(b, refund amount)` (sent at cancellation, not when Razorpay finishes — the email says the refund reaches the buyer in 5–7 working days); `RefundGateway.finish` on `PROCESSED` → `refundProcessed` for every kind except `CANCELLATION`; `RefundService.decline` → `refundDeclined`.

- [ ] **Step 1: Write failing tests**

```java
@Test void verifyWebhookAndRetrySendOneConfirmation()   // settle ×3 → one BOOKING_CONFIRMED row, payload has 2 ticket codes and totalPaise 105104
@Test void freeBookingGetsConfirmation()
@Test void onlyRealHoldExpiryEmails()                    // HOLD_EXPIRED → BOOKING_EXPIRED; REPLACED and PAYMENT_GATEWAY_UNAVAILABLE → none
@Test void processedRefundEmailsOnce()                   // refund.processed webhook twice → one REFUND_PROCESSED with amountPaise 49900, includesFee false
@Test void declinedRequestEmails()                       // decline with note → REFUND_DECLINED payload note
@Test void cancellingEventEmailsEachBookingOnce()        // 2 confirmed + 1 pending; cancel → 3 EVENT_CANCELLED rows at once (amountPaise = each booking's total_paise; 0 for the pending one); then refund.processed for both and a sweep → still 3, no REFUND_PROCESSED rows
@Test void dateChangeEmailsHolders()                     // PATCH startsAt on a live event with 2 confirmed bookings → 2 EVENT_CHANGED rows with refundUntil ≈ now+7d; a second change the same day → 2 more (new window end)
@Test void emailFailureNeverBreaksBooking()              // Resend fake returning 500 → booking still CONFIRMED, outbox row PENDING with attempts 1
```

- [ ] **Step 2: Run — expect FAIL.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run — expect PASS**, then full suite.
- [ ] **Step 5: Commit** — `feat(booking): booking, refund and event emails`

---

### Task 5: Web — QR tickets, ticket view and downloads

**Files:**
- Create: `stagepass-web/src/features/bookings/TicketQr.tsx`, `bookings/TicketViewPage.tsx`, `bookings/downloads.ts`
- Modify: `features/checkout/types.ts` (`TicketLine.qrToken?: string`), `bookings/BookingDetailPage.tsx` (QR thumbnail per live ticket linking to the ticket view, "Download tickets (PDF)", "Download invoice" when `invoiceNumber`; remove the "QR codes coming soon" note), `src/app/router.tsx` (`/account/bookings/:id/tickets/:ticketId`, RequireAuth), `package.json` (`qrcode`, `@types/qrcode`), README (one line: tickets, PDFs and emails)
- Test: `bookings/TicketQr.test.tsx`, `bookings/TicketViewPage.test.tsx`, `bookings/downloads.test.ts`, `bookings/BookingDetailPage.test.tsx` (update)

**Interfaces:**
- Produces: `TicketQr({ token, size, label })` — `<img alt={label}>` from `QRCode.toDataURL`; `downloadFile(path: string, fallbackName: string): Promise<void>` — `fetch` with the Supabase bearer token (same base URL and refresh behaviour as `api`), filename from `Content-Disposition`, saved through an object URL; errors surface as `ApiError`.
- `TicketViewPage` — one ticket full width: event title, date, venue, holder, type, code, large QR, status badge; refunded/cancelled tickets show the status and no QR; prev/next between the booking's tickets; text "Turn your screen brightness up at the entrance".

- [ ] **Step 1: Write failing tests**

```ts
test('renders a QR image for the token')                        // mocked qrcode.toDataURL called with token and { errorCorrectionLevel: 'M' }; img alt "QR code for SP-AAAA-2222"
test('ticket view shows a live ticket large and hides QR for refunded ones')
test('booking detail links each live ticket and offers PDF and invoice downloads')
test('downloadFile saves with the server filename')             // fetch mock with content-disposition → anchor download "stagepass-SP-ABC234.pdf"
test('downloadFile turns problem responses into ApiError')      // 409 problem → rejects ApiError code INVALID_STATE
```

- [ ] **Step 2: Run — expect FAIL.** `cd stagepass-web && npx vitest run`
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run — expect PASS**; lint; build; full backend suite; browser check: confirmed booking (free event is enough) → QR visible, ticket view, PDF download opens, booking email row present in `email_outbox` (delivered only to the Resend account owner until a domain is verified).
- [ ] **Step 5: Commit** — `feat(web): QR tickets, ticket view and PDF downloads`

---

## Self-review notes

- Spec coverage: §8.5 one row per seat (Plan 4), human codes (Plan 4), signed QR (T1), ZXing PNG used in PDF (T1, T2), client-side QR (T5), OpenPDF ticket with event/venue/time/holder/type/code/QR/terms (T2), email PDF attachment (T3), ticket page status badges (T5); §8.9 booking confirmed / expired / refund processed / event cancelled emails written in the business transaction (T4) with the existing retrying worker; §8.4a reschedule emails (T4) and invoice PDF linked from booking (T2, T5).
- Deferred by design: event reminder 24 h before and in-app notifications (Plan 9, with notifications UI); organizer/admin approval emails already exist (Plan 2); QR nonce rotation and "Add to wallet" (spec: designed, shipped disabled); check-in scanner (Plan 7, consumes `QrTokens.verify`).
- The QR PNG is not exposed as a public URL (a ticket credential in a URL leaks into logs and proxies); emails carry the PDF attachment instead, and the screen renders from the token.
