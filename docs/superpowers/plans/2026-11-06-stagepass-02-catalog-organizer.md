# Stagepass Catalog & Organizer Onboarding Implementation Plan (Plan 2 of 11)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An attendee can apply to become an organizer and an admin can approve them; an approved organizer can create venues and events with multiple ticket types, upload images to Cloudinary, and submit events; an admin reviews, approves, rejects and features events and manages categories.

**Architecture:** New backend modules `catalog` (categories, venues, events, ticket types, images) and organizer onboarding inside `identity`, each with `api` / `service` / `domain` / `repository`. Organizer endpoints under `/api/v1/organizer/**` (role `ORGANIZER` + approved organizer row), admin under `/api/v1/admin/**` (role `ADMIN`), read-only reference data under `/api/v1/public/**`. Web gets an organizer area and an admin area on `DashboardLayout`. Public discovery of published events is Plan 3.

**Tech Stack:** as Plan 1, plus Cloudinary signed direct uploads (no SDK: SHA-1 signature computed server-side, browser posts to `https://api.cloudinary.com/v1_1/{cloud}/image/upload`).

**Spec:** `docs/superpowers/specs/2026-11-05-stagepass-design.md` (§6 organizer onboarding, §7 tables, §8.1 lifecycle, §8.4a editing published events, §8.8 images, §9 pages).

## Global Constraints

- All Plan 1 Global Constraints apply (Java 17, Boot 4.1.2, paise `bigint`, Problem Details with `code`, roles only from DB, `/api/v1`, `@stagepass.test` identities).
- Pagination response: `record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages)`; query `?page=0&size=20` (size max 100).
- Ownership failures return **404 `NOT_FOUND`** (never reveal other organizers' resources).
- Organizer endpoints require role `ORGANIZER` **and** `organizers.status = 'APPROVED'`; otherwise 403 `ORGANIZER_NOT_APPROVED`.
- Event lifecycle exactly as spec §8.1: `DRAFT → PENDING_REVIEW → PUBLISHED | REJECTED`, `REJECTED → PENDING_REVIEW` on resubmit, `PUBLISHED → CANCELLED`; delete only in `DRAFT`/`REJECTED` with no bookings.
- Times: API takes/returns ISO-8601 with offset (`OffsetDateTime`); web edits in IST (`Asia/Kolkata`) via `datetime-local`.
- Money input in rupees on the web, sent as paise integers; API only accepts paise.
- Validation formats: PAN `^[A-Z]{5}[0-9]{4}[A-Z]$`; GSTIN `^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$`; IFSC `^[A-Z]{4}0[A-Z0-9]{6}$`; bank account `^[0-9]{9,18}$`; pincode `^[1-9][0-9]{5}$`; Indian mobile `^[6-9]\d{9}$`.
- Payout account numbers encrypted with AES-256-GCM (`stagepass.payout-encryption-key`, base64 32 bytes); API responses show only `••••1234`.
- Images: jpg/png/webp, ≤ 5 MB (client-enforced; Cloudinary `allowed_formats` signed), folder `stagepass/events/{eventId}` or `stagepass/organizers/{userId}`.
- Audit-log: `ORGANIZER_APPLIED`, `ORGANIZER_APPROVED`, `ORGANIZER_REJECTED`, `ROLE_GRANTED`, `EVENT_SUBMITTED`, `EVENT_APPROVED`, `EVENT_REJECTED`, `EVENT_FEATURED`, `EVENT_CANCELLED`, `EVENT_RESCHEDULED`, `CATEGORY_CHANGED`.
- New emails (outbox): `ORGANIZER_APPROVED`, `ORGANIZER_REJECTED`, `EVENT_APPROVED`, `EVENT_REJECTED`.

## Review Focus

1. **Organizer guesses another organizer's event / ticket type / venue id** → 404, never data or a 403 — tested in Tasks 5, 6, 7.
2. **Organizer lowers a ticket type's quantity below tickets already sold or held** → 409 `CONFLICT` with the minimum allowed, inventory unchanged — tested in Task 7.
3. **Submitting an incomplete event** (no ticket types, no cover image, start in the past, no venue for an in-person event) → 400 `VALIDATION_FAILED` listing every problem at once — tested in Task 8.
4. **Applying to be an organizer twice** → 409 while `PENDING`/`APPROVED`; after `REJECTED` the same application is updated and returns to `PENDING` — tested in Task 3.
5. **Cover image URL from another Cloudinary account or another event's folder** → 400, never stored — tested in Task 9.

---

### Task 1: Shared building blocks

**Files:**
- Create: `stagepass-api/src/main/java/com/stagepass/common/api/PageResponse.java`, `common/api/PageRequests.java`
- Create: `common/crypto/PayoutCrypto.java`
- Create: `common/text/Slugs.java`
- Modify: `common/error/ErrorCode.java` (add `ORGANIZER_NOT_APPROVED(403)`, `INVALID_STATE(409)`)
- Test: `common/crypto/PayoutCryptoTest.java`, `common/text/SlugsTest.java`, `common/api/PageRequestsTest.java`

**Interfaces:**
- Produces: `record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages)` with `static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper)`.
- Produces: `PageRequests.of(Integer page, Integer size, Sort sort): Pageable` — defaults page 0 / size 20, clamps size to 1..100, negative page → 0.
- Produces: `@Component PayoutCrypto` — `String encrypt(String plain)` → base64(iv‖ciphertext‖tag), random 12-byte IV; `String decrypt(String stored)`; `static String mask(String accountNumber)` → `"••••" + last4`. Constructor fails fast if the key is not 32 bytes.
- Produces: `Slugs.slugify(String title): String` — lowercase ASCII, `[^a-z0-9]+` → `-`, trimmed, max 60 chars, empty → `"event"`; `Slugs.unique(String base): String` → `base + "-" + 6 random [a-z0-9]`.

- [ ] **Step 1: Write failing tests**

```java
@Test void roundTripsAndUsesRandomIv()        // encrypt("123456789012") twice → different ciphertexts; decrypt → original
@Test void tamperedCiphertextIsRejected()     // flip one byte → IllegalStateException
@Test void masksToLastFour()                  // mask("123456789012") == "••••9012"
@Test void rejectsShortKey()                  // new PayoutCrypto(props with 16-byte key) → IllegalStateException
@Test void slugifiesUnicodeAndPunctuation()   // "Sunburn Goa 2026 — Day 1!" → "sunburn-goa-2026-day-1"
@Test void emptySlugFallsBack()               // "!!!" → "event"
@Test void uniqueAppendsSuffix()              // unique("gig") matches "^gig-[a-z0-9]{6}$"
@Test void clampsPageSize()                   // of(null, 500, unsorted).getPageSize() == 100; of(-3, null,…).getPageNumber() == 0
```

- [ ] **Step 2: Run — expect FAIL.** `cd stagepass-api && ./mvnw -q test -Dtest='PayoutCryptoTest,SlugsTest,PageRequestsTest'`
- [ ] **Step 3: Implement.** Use `javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")`, 128-bit tag; `java.text.Normalizer` NFKD + strip diacritics for slugs.
- [ ] **Step 4: Run — expect PASS**, then full suite.
- [ ] **Step 5: Commit** — `feat(common): pagination, slug and payout encryption helpers`

---

### Task 2: Schema additions and catalog entities

**Files:**
- Create: `stagepass-api/src/main/resources/db/migration/V8__catalog_review.sql`
- Create in `com.stagepass.catalog.domain`: `Category.java`, `Venue.java`, `Event.java`, `EventStatus.java`, `RefundPolicy.java`, `TicketType.java`, `TicketTypeStatus.java`, `TicketInventory.java`
- Create in `com.stagepass.identity.domain`: `Organizer.java`, `OrganizerStatus.java`
- Create repositories: `catalog.repository.{CategoryRepository, VenueRepository, EventRepository, TicketTypeRepository, TicketInventoryRepository}`, `identity.repository.OrganizerRepository`
- Test: `stagepass-api/src/test/java/com/stagepass/catalog/CatalogMappingTest.java`

**Interfaces:**
- V8 adds: `events.is_online boolean not null default false`, `events.online_url text`, `events.submitted_at timestamptz`, `events.reviewed_by uuid references users(id)` (+ index), `organizers.reviewed_by uuid references users(id)` (+ index), `organizers.reviewed_at timestamptz`, `organizers.applied_at timestamptz not null default now()`, and `check (not is_online or online_url is not null)` deferred to submit validation (no DB check).
- Entities map every column of their table (Plan 1 V1–V8); `@Version` where the table has `version`; enums `@Enumerated(STRING)`; `Event.gallery` as `@JdbcTypeCode(SqlTypes.JSON) List<String>`; `Event.convenienceFeeOverride` as `@JdbcTypeCode(SqlTypes.JSON) Map<String,Object>`; `search_vector` and `created_at` not mapped / `insertable=false, updatable=false`.
- `TicketType` has `@OneToOne(mappedBy="ticketType", cascade=ALL) TicketInventory inventory`; `TicketInventory` uses `@MapsId`.
- Repositories: `EventRepository.findByIdAndOrganizerId(UUID, UUID): Optional<Event>`, `findByOrganizerId(UUID, Pageable)`, `findByOrganizerIdAndStatus(UUID, EventStatus, Pageable)`, `findByStatus(EventStatus, Pageable)`; `VenueRepository.findByIdAndOrganizerId`, `findByOrganizerIdOrderByNameAsc`; `TicketTypeRepository.findByIdAndEventId`, `findByEventIdOrderBySortOrderAscCreatedAtAsc`, `countByEventId`; `OrganizerRepository.findByUserId(UUID)`, `findByStatus(OrganizerStatus, Pageable)`; `CategoryRepository.findByActiveTrueOrderBySortOrderAsc()`, `existsBySlug`.

- [ ] **Step 1: Write failing test** (`@Transactional`, uses `SchemaFixtures` from Plan 1 for parents)

```java
@Test void persistsEventWithTicketTypeAndInventory()   // save Event + TicketType(price 49900) + inventory total 100 → reload: inventory.total == 100, status DRAFT, gallery == []
@Test void eventGalleryRoundTripsAsJson()             // gallery ["https://res.cloudinary.com/x/a.jpg"] → reload equal
@Test void organizerStatusDefaultsToPending()
@Test void hibernateValidatesSchema()                 // context starts with ddl-auto=validate (implicit: any mismatch fails the test class)
```

- [ ] **Step 2: Run — expect FAIL.** `./mvnw -q test -Dtest=CatalogMappingTest`
- [ ] **Step 3: Write V8 and the entities/repositories.**
- [ ] **Step 4: Run — expect PASS**, then full suite.
- [ ] **Step 5: Commit** — `feat(catalog): domain model and repositories`

---

### Task 3: Organizer applications (attendee side)

**Files:**
- Create in `com.stagepass.identity`: `api/OrganizerApplicationController.java`, `api/OrganizerApplicationRequest.java`, `api/OrganizerApplicationResponse.java`, `service/OrganizerApplicationService.java`
- Create: `common/security/OrganizerGuard.java`
- Test: `identity/OrganizerApplicationControllerTest.java`; `support/TestUsers.java`

**Interfaces:**
- Consumes: `PayoutCrypto`, `AuditLogService`, `CurrentUser`, `RoleLookupService.evict`.
- Produces endpoints:
  - `POST /api/v1/organizer-applications` body `OrganizerApplicationRequest(@NotBlank @Size(max=120) orgName, @Size(max=2000) bio, @URL logoUrl, @NotBlank @Email contactEmail, @Pattern(mobile) contactPhone, @URL website, @NotBlank @Pattern(PAN) pan, @Pattern(GSTIN) gstin, @NotBlank state, @NotBlank payoutAccountName, @NotBlank @Pattern(account) payoutAccountNumber, @NotBlank @Pattern(IFSC) payoutIfsc)` → `201` `OrganizerApplicationResponse`. PAN/GSTIN/IFSC upper-cased before validation (`@JsonSetter`-free: normalize in a compact constructor).
  - `GET /api/v1/organizer-applications/me` → `200` response or `404` if none.
- Produces: `record OrganizerApplicationResponse(UUID id, String orgName, String slug, String status, String rejectionReason, String contactEmail, String contactPhone, String website, String bio, String logoUrl, String pan, String gstin, String state, String payoutAccountName, String payoutAccountMasked, String payoutIfsc, OffsetDateTime appliedAt, OffsetDateTime reviewedAt)`.
- Rules: existing `PENDING` or `APPROVED` → `409 CONFLICT`; existing `REJECTED`/`SUSPENDED`→ only `REJECTED` may reapply (updates fields, status → `PENDING`, clears `rejection_reason`, `applied_at = now()`); `SUSPENDED` → 403 `FORBIDDEN`. Slug = `Slugs.unique(Slugs.slugify(orgName))`, kept on reapply. Audit `ORGANIZER_APPLIED`.
- Produces: `@Component OrganizerGuard` — `UUID requireApprovedOrganizerId()` (current user must have role `ORGANIZER` and organizer row `APPROVED`, else 403 `ORGANIZER_NOT_APPROVED`); returns `organizers.id`.
- Test support `TestUsers` (Spring bean in test sources): `String attendeeToken()`, `String adminToken()` (grants ADMIN via jdbc + evict), `String approvedOrganizerToken()` / `UUID organizerIdOf(String token)`; each call creates a fresh random user.

- [ ] **Step 1: Write failing tests**

```java
@Test void attendeeAppliesAndSeesPendingApplication()   // POST valid body → 201 status PENDING, payoutAccountMasked "••••9012"; GET /me → same id
@Test void accountNumberIsEncryptedAtRest()             // organizers.payout_account_number_enc != "123456789012" and decrypts to it
@Test void lowercasePanIsNormalised()                    // pan "abcde1234f" → stored "ABCDE1234F"
@Test void invalidPanIfscAndPhoneAreReported()          // pan "123", payoutIfsc "SBIN123", contactPhone "12345" → 400, errors fields ⊇ {pan, payoutIfsc, contactPhone}
@Test void secondApplicationWhilePendingIs409()
@Test void rejectedApplicantCanReapply()                // jdbc set REJECTED+reason → POST again → 200/201 PENDING, rejectionReason null, same id and slug
@Test void myApplicationIs404WhenNone()
@Test void attendeeCannotUseOrganizerEndpoints()        // attendee token → GET /api/v1/organizer/events → 403 code FORBIDDEN (role) 
```

- [ ] **Step 2: Run — expect FAIL.** `./mvnw -q test -Dtest=OrganizerApplicationControllerTest`
- [ ] **Step 3: Implement.** Organizer routes are protected in `SecurityConfig` with `.requestMatchers("/api/v1/organizer/**").hasRole("ORGANIZER")` and `.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")`.
- [ ] **Step 4: Run — expect PASS**, then full suite.
- [ ] **Step 5: Commit** — `feat(identity): organizer applications with encrypted payout details`

---

### Task 4: Admin organizer review + new email templates

**Files:**
- Create in `com.stagepass.admin`: `api/AdminOrganizerController.java`, `api/ReviewDecisionRequest.java`, `service/AdminOrganizerService.java`
- Modify: `notification/service/EmailTemplate.java` (+ `ORGANIZER_APPROVED`, `ORGANIZER_REJECTED`, `EVENT_APPROVED`, `EVENT_REJECTED`), `EmailRenderer.java`
- Test: `admin/AdminOrganizerControllerTest.java`, `notification/EmailRendererTest.java` (extend)

**Interfaces:**
- `GET /api/v1/admin/organizer-applications?status=PENDING&page&size` → `PageResponse<OrganizerApplicationResponse>` ordered by `applied_at` asc.
- `POST /api/v1/admin/organizer-applications/{id}/approve` → 200 response; requires `PENDING` else 409 `INVALID_STATE`. Sets `APPROVED`, `reviewed_by/at`, grants role `ORGANIZER` (`UserRepository.grantRole`), `RoleLookupService.evict(userId)`, audit `ORGANIZER_APPROVED` + `ROLE_GRANTED`, enqueue `ORGANIZER_APPROVED` email to `contact_email` with `{orgName, dashboardUrl}`.
- `POST /api/v1/admin/organizer-applications/{id}/reject` body `ReviewDecisionRequest(@NotBlank @Size(max=500) reason)` → 200; `PENDING` only; sets `REJECTED` + reason; audit; email `ORGANIZER_REJECTED` `{orgName, reason, reapplyUrl}`.
- Renderer: each new template has subject + HTML + text using the Plan 1 layout; all values escaped. Subjects: `"You’re approved to sell on Stagepass"`, `"Update on your Stagepass organizer application"`, `"Your event is live: {title}"`, `"Changes needed for {title}"`.

- [ ] **Step 1: Write failing tests**

```java
@Test void adminListsPendingApplicationsOldestFirst()
@Test void approvingGrantsOrganizerRoleImmediately()     // approve → same user's GET /me roles contain ORGANIZER (cache evicted); email_outbox has ORGANIZER_APPROVED to contact email
@Test void rejectingRequiresReasonAndEmailsIt()          // reject {} → 400; reject {"reason":"PAN mismatch"} → REJECTED, outbox payload reason
@Test void approvingTwiceIs409InvalidState()
@Test void nonAdminGets403()
// EmailRendererTest
@Test void organizerRejectedEscapesReason()              // reason "<b>x</b>" → html contains "&lt;b&gt;"
@Test void eventApprovedSubjectHasTitle()
```

- [ ] **Step 2: Run — expect FAIL.** `./mvnw -q test -Dtest='AdminOrganizerControllerTest,EmailRendererTest'`
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run — expect PASS**, then full suite.
- [ ] **Step 5: Commit** — `feat(admin): review organizer applications`

---

### Task 5: Categories and venues

**Files:**
- Create in `com.stagepass.catalog`: `api/PublicCategoryController.java`, `api/AdminCategoryController.java`, `api/CategoryRequest.java`, `api/CategoryResponse.java`, `service/CategoryService.java`, `api/OrganizerVenueController.java`, `api/VenueRequest.java`, `api/VenueResponse.java`, `service/VenueService.java`
- Test: `catalog/CategoryControllerTest.java`, `catalog/VenueControllerTest.java`

**Interfaces:**
- `GET /api/v1/public/categories` → `List<CategoryResponse(UUID id, String name, String slug, String icon, int sortOrder, boolean active)>` active only, by `sort_order`. `Cache-Control: public, max-age=300`.
- `GET /api/v1/admin/categories` (all), `POST` `CategoryRequest(@NotBlank @Size(max=60) name, @Pattern("^[a-z0-9-]{2,40}$") slug, @Size(max=40) icon, @Min(0) Integer sortOrder, Boolean active)` → 201 (duplicate slug → 409), `PATCH /{id}` (partial). Audit `CATEGORY_CHANGED`. No delete (deactivate instead).
- `GET /api/v1/organizer/venues` → own venues by name; `POST` `VenueRequest(@NotBlank @Size(max=120) name, @NotBlank @Size(max=300) addressLine, @NotBlank city, state, @Pattern(pincode) pincode, @DecimalMin("-90") @DecimalMax("90") Double lat, @DecimalMin("-180") @DecimalMax("180") Double lng, googlePlaceId)` → 201; `PATCH /{id}` → 200; other organizer's id → 404.
- `VenueResponse(UUID id, String name, String addressLine, String city, String state, String pincode, Double lat, Double lng, String googlePlaceId)`.

- [ ] **Step 1: Write failing tests**

```java
@Test void publicCategoriesAreActiveAndOrdered()        // 9 seeded, first slug "music"; deactivate one via admin PATCH → 8
@Test void duplicateCategorySlugIs409()
@Test void organizerCreatesAndListsOwnVenues()
@Test void otherOrganizersVenueIs404()                  // org B PATCH org A's venue → 404
@Test void invalidPincodeAndLatitudeRejected()          // pincode "0123", lat 120 → 400 fields {pincode, lat}
@Test void pendingOrganizerGets403NotApproved()         // role ORGANIZER but organizers.status PENDING → 403 ORGANIZER_NOT_APPROVED
```

- [ ] **Step 2: Run — expect FAIL.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run — expect PASS**, then full suite.
- [ ] **Step 5: Commit** — `feat(catalog): categories and organizer venues`

---

### Task 6: Organizer event drafts (create, read, update, delete)

**Files:**
- Create in `com.stagepass.catalog`: `api/OrganizerEventController.java`, `api/EventUpsertRequest.java`, `api/EventResponse.java`, `api/EventSummaryResponse.java`, `service/EventService.java`, `service/EventMapper.java`
- Test: `catalog/OrganizerEventControllerTest.java`; `support/CatalogFixtures.java`

**Interfaces:**
- `POST /api/v1/organizer/events` body `EventUpsertRequest` → 201 `EventResponse` in `DRAFT`, slug `Slugs.unique(slugify(title))`.
- `GET /api/v1/organizer/events?status&page&size` → `PageResponse<EventSummaryResponse(UUID id, String title, String slug, String status, OffsetDateTime startsAt, String city, String coverImageUrl, int ticketTypeCount, long minPricePaise, int totalCapacity, int sold)>` newest first.
- `GET /api/v1/organizer/events/{id}` → `EventResponse(UUID id, String slug, String title, String summary, String description, UUID categoryId, String categoryName, VenueResponse venue, boolean online, String onlineUrl, OffsetDateTime startsAt, OffsetDateTime endsAt, String timezone, String coverImageUrl, List<String> gallery, String terms, Integer maxTicketsPerUser, String refundPolicy, Integer refundCutoffHours, String status, String rejectionReason, boolean featured, OffsetDateTime rescheduleRefundUntil, List<TicketTypeResponse> ticketTypes)`.
- `PATCH /api/v1/organizer/events/{id}` body `EventUpsertRequest` (all fields optional for PATCH; for POST `title`, `categoryId`, `startsAt`, `endsAt` required — validated in service, not annotations) → 200.
- `EventUpsertRequest(@Size(min=3,max=140) title, @Size(max=280) summary, @Size(max=20000) description, UUID categoryId, UUID venueId, Boolean online, @URL onlineUrl, OffsetDateTime startsAt, OffsetDateTime endsAt, @Size(max=10000) terms, @Min(1) @Max(50) Integer maxTicketsPerUser, String refundPolicy, @Min(0) @Max(720) Integer refundCutoffHours)`.
- Rules: `endsAt > startsAt` else 400 field `endsAt`; `venueId` must be own venue (else 404); category must exist and be active (else 400 field `categoryId`); editable in `DRAFT`, `REJECTED`, `PENDING_REVIEW` (edit moves `PENDING_REVIEW` back to `DRAFT`), and `PUBLISHED` (spec §8.4a: if `startsAt`/`endsAt`/`venueId` changes → `reschedule_refund_until = now + 7 days`, audit `EVENT_RESCHEDULED`); `CANCELLED`/`COMPLETED` → 409 `INVALID_STATE`.
- `DELETE /api/v1/organizer/events/{id}` → 204 if `DRAFT`/`REJECTED` and no rows in `bookings` for it; else 409 `INVALID_STATE`.
- `CatalogFixtures` (test bean): `UUID venue(String orgToken)`, `UUID draftEvent(String orgToken)`, `UUID ticketType(String orgToken, UUID eventId, long pricePaise, int qty)`.

- [ ] **Step 1: Write failing tests**

```java
@Test void createsDraftWithSlug()                     // POST {title:"Sunburn Goa 2026", categoryId, startsAt +30d, endsAt +30d4h} → 201 DRAFT, slug ^sunburn-goa-2026-[a-z0-9]{6}$
@Test void missingRequiredFieldsListed()              // POST {} → 400 fields ⊇ {title, categoryId, startsAt, endsAt}
@Test void endBeforeStartRejected()                   // → 400 field endsAt
@Test void otherOrganizersEventIs404ForGetPatchDelete()
@Test void listsOnlyOwnEventsNewestFirst()
@Test void editingPendingReviewEventReturnsItToDraft()
@Test void reschedulingPublishedEventOpensRefundWindow()  // jdbc set PUBLISHED → PATCH startsAt → rescheduleRefundUntil ≈ now+7d; audit EVENT_RESCHEDULED
@Test void deleteDraftWorksButNotWithBookings()        // insert a booking row via SchemaFixtures-style SQL → DELETE → 409
@Test void cancelledEventIsReadOnly()                  // status CANCELLED → PATCH → 409 INVALID_STATE
```

- [ ] **Step 2: Run — expect FAIL.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run — expect PASS**, then full suite.
- [ ] **Step 5: Commit** — `feat(catalog): organizer event drafts`

---

### Task 7: Ticket types and inventory

**Files:**
- Create in `com.stagepass.catalog`: `api/TicketTypeController.java` (under `/api/v1/organizer/events/{eventId}/ticket-types`), `api/TicketTypeRequest.java`, `api/TicketTypeResponse.java`, `service/TicketTypeService.java`
- Test: `catalog/TicketTypeControllerTest.java`

**Interfaces:**
- `POST` body `TicketTypeRequest(@Size(min=1,max=80) name, @Size(max=500) description, @Min(0) @Max(10_000_000_00L) Long pricePaise, @Min(1) @Max(100000) Integer quantity, OffsetDateTime saleStartsAt, OffsetDateTime saleEndsAt, @Min(1) Integer minPerOrder, @Min(1) @Max(50) Integer maxPerOrder, Integer sortOrder, String status)` → 201; `PATCH /{ticketTypeId}` partial → 200; `DELETE /{ticketTypeId}` → 204.
- `TicketTypeResponse(UUID id, String name, String description, long pricePaise, int quantity, int sold, int held, int available, OffsetDateTime saleStartsAt, OffsetDateTime saleEndsAt, int minPerOrder, int maxPerOrder, int sortOrder, String status)`.
- Rules: max 20 ticket types per event; `saleEndsAt ≤ event.endsAt` and `saleEndsAt > saleStartsAt` (400); `maxPerOrder ≥ minPerOrder`; price change when `sold > 0` → 409 `CONFLICT` "Create a new ticket type to change the price"; new `quantity < sold + held` → 409 `CONFLICT` with detail containing the minimum; delete only when `sold = 0 and held = 0` else 409; event in `CANCELLED`/`COMPLETED` → 409 `INVALID_STATE`; other organizer's event or ticket type → 404. Quantity update uses `UPDATE ticket_inventory SET total = :q WHERE ticket_type_id = :id AND sold + held <= :q` (0 rows → 409) so it cannot race a booking.

- [ ] **Step 1: Write failing tests**

```java
@Test void addsTicketTypesWithInventory()            // General ₹499×300, VIP ₹1499×50 → event detail lists both, available 300/50
@Test void quantityCannotGoBelowSoldPlusHeld()       // jdbc sold=40, held=5 → PATCH quantity 30 → 409 detail contains "45"; inventory total still 300
@Test void quantityCanShrinkToExactlySoldPlusHeld()  // PATCH quantity 45 → 200
@Test void priceIsLockedAfterFirstSale()             // sold=1 → PATCH pricePaise → 409
@Test void cannotDeleteTypeWithSales()
@Test void saleWindowMustEndBeforeEvent()            // saleEndsAt after event end → 400 field saleEndsAt
@Test void otherOrganizersTicketTypeIs404()
@Test void freeTicketTypeAllowed()                   // pricePaise 0 → 201
```

- [ ] **Step 2: Run — expect FAIL.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run — expect PASS**, then full suite.
- [ ] **Step 5: Commit** — `feat(catalog): ticket types with race-safe inventory edits`

---

### Task 8: Submit, admin review, feature, cancel

**Files:**
- Create in `com.stagepass.catalog`: `service/EventLifecycleService.java`, `api/OrganizerEventLifecycleController.java` (`POST /api/v1/organizer/events/{id}/submit`, `/cancel`)
- Create in `com.stagepass.admin`: `api/AdminEventController.java`, `api/FeatureRequest.java`, `service/AdminEventService.java`
- Test: `catalog/EventLifecycleTest.java`

**Interfaces:**
- `POST …/submit` → 200 `EventResponse` `PENDING_REVIEW`, `submitted_at = now()`, audit `EVENT_SUBMITTED`. Allowed from `DRAFT`/`REJECTED`. Validation collects **all** problems into one 400 `VALIDATION_FAILED` `errors[]`: `ticketTypes` "Add at least one ticket type", `coverImageUrl` "Add a cover image", `startsAt` "Start time must be in the future", `venueId` "Choose a venue or mark the event as online", `onlineUrl` "Add the online event link" (online without URL), `summary` "Add a short summary".
- `POST …/cancel` body `ReviewDecisionRequest(reason)` → 200 `CANCELLED` from `PUBLISHED`/`PENDING_REVIEW`/`DRAFT`; audit `EVENT_CANCELLED`. (Refunding confirmed bookings is added by Plan 4 inside this same service method — leave a clearly named `onCancelled(Event)` hook that does nothing yet.)
- `GET /api/v1/admin/events?status=PENDING_REVIEW&page&size` → `PageResponse<AdminEventSummary(UUID id, String title, String organizerName, String status, OffsetDateTime startsAt, OffsetDateTime submittedAt, String city, String coverImageUrl, boolean featured)>` oldest submission first; `GET /api/v1/admin/events/{id}` → `EventResponse`.
- `POST /api/v1/admin/events/{id}/approve` → `PUBLISHED`, `published_at`, `reviewed_by`; only from `PENDING_REVIEW` and `startsAt > now` (else 409 `INVALID_STATE`); audit; email `EVENT_APPROVED` to organizer contact email `{title, eventUrl: webBaseUrl + "/events/" + slug}`.
- `POST /api/v1/admin/events/{id}/reject` `ReviewDecisionRequest` → `REJECTED` + `rejection_reason`; audit; email `EVENT_REJECTED` `{title, reason, editUrl}`.
- `POST /api/v1/admin/events/{id}/feature` `FeatureRequest(@NotNull Boolean featured)` → only `PUBLISHED`; audit `EVENT_FEATURED`.

- [ ] **Step 1: Write failing tests**

```java
@Test void incompleteEventListsEveryProblem()          // draft with no cover, no types, startsAt past, no venue → 400 fields ⊇ {coverImageUrl, ticketTypes, startsAt, venueId}
@Test void completeEventCanBeSubmitted()              // → PENDING_REVIEW, submittedAt set
@Test void adminApprovesAndOrganizerIsEmailed()       // → PUBLISHED, publishedAt, outbox EVENT_APPROVED
@Test void adminRejectsWithReasonAndOrganizerCanResubmit()  // reject → REJECTED reason; organizer PATCH + submit → PENDING_REVIEW, rejectionReason cleared
@Test void cannotApproveEventThatAlreadyStarted()     // jdbc startsAt past → 409
@Test void onlyPublishedEventsCanBeFeatured()
@Test void organizerCannotApproveOwnEvent()           // organizer token → POST /admin/events/{id}/approve → 403
@Test void cancelPublishedEvent()                     // → CANCELLED, audit EVENT_CANCELLED
```

- [ ] **Step 2: Run — expect FAIL.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run — expect PASS**, then full suite.
- [ ] **Step 5: Commit** — `feat(catalog): event submission and admin review`

---

### Task 9: Cloudinary signed uploads and image attachment

**Files:**
- Create: `catalog/config/CloudinaryProperties.java` (`cloudinary.cloud-name`, `api-key`, `api-secret`), `catalog/api/UploadController.java`, `catalog/api/UploadSignatureRequest.java`, `catalog/api/UploadSignatureResponse.java`, `catalog/service/CloudinarySigner.java`, `catalog/service/ImageUrlPolicy.java`
- Modify: `EventUpsertRequest` (+ `coverImageUrl`, `List<String> gallery` max 8), `EventService` (validate URLs via `ImageUrlPolicy`), `OrganizerApplicationRequest.logoUrl` validation, `application.properties` (+ `cloudinary.*` from `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`), `application-local.properties.example`, `render.yaml`, README env table
- Test: `catalog/CloudinarySignerTest.java`, `catalog/ImageUrlPolicyTest.java`, `catalog/UploadControllerTest.java`

**Interfaces:**
- `POST /api/v1/organizer/uploads/signature` body `UploadSignatureRequest(@NotNull String purpose /* EVENT_IMAGE | ORGANIZER_LOGO */, UUID eventId)` → `UploadSignatureResponse(String cloudName, String apiKey, long timestamp, String signature, String folder, String allowedFormats, String uploadUrl, long maxBytes)`; `EVENT_IMAGE` requires own `eventId` (404 otherwise) → folder `stagepass/events/{eventId}`; `ORGANIZER_LOGO` → `stagepass/organizers/{userId}` and is also allowed for non-approved users (needed while applying) — route it as `/api/v1/uploads/signature` for that purpose. `maxBytes = 5_242_880`, `allowedFormats = "jpg,png,webp"`. Unconfigured Cloudinary → 503 `SERVICE_UNAVAILABLE`.
- `CloudinarySigner.sign(Map<String,String> params, long timestamp): String` — Cloudinary rule: sort params by key, join `k=v` with `&`, append api secret, SHA-1 hex. Signed params: `allowed_formats`, `folder`, `timestamp`.
- `ImageUrlPolicy.requireOwned(String url, String folder)` — accepts only `https://res.cloudinary.com/{cloudName}/image/upload/` + optional transformation/version segments + `{folder}/` + filename with jpg|jpeg|png|webp; else `ApiException(VALIDATION_FAILED, …)` surfaced as field error on the request field.

- [ ] **Step 1: Write failing tests**

```java
// CloudinarySignerTest — vector from Cloudinary docs
@Test void matchesCloudinaryDocumentedSignature()   // params {public_id:"sample_image", timestamp:"1315060510"}, secret "abcd" → "b4ad47fb4e25c7bf5f92a20089f9db59bc302313"
// ImageUrlPolicyTest
@Test void acceptsOwnFolderWithVersionAndTransform() // https://res.cloudinary.com/demo/image/upload/f_auto,q_auto/v1712/stagepass/events/{id}/cover.webp
@Test void rejectsOtherCloud()                       // https://res.cloudinary.com/other/...
@Test void rejectsOtherEventsFolder()
@Test void rejectsNonImageAndHttp()                  // http://… or .svg / .gif
// UploadControllerTest
@Test void signsUploadForOwnEvent()                  // response folder stagepass/events/{id}, signature == signer(...)
@Test void otherOrganizersEventCannotBeSigned()      // 404
@Test void coverFromForeignFolderIsRejected()        // PATCH event coverImageUrl foreign → 400 field coverImageUrl
@Test void unconfiguredCloudinaryIs503()
```

- [ ] **Step 2: Run — expect FAIL.**
- [ ] **Step 3: Implement** (`MessageDigest.getInstance("SHA-1")`, `HexFormat.of().formatHex`).
- [ ] **Step 4: Run — expect PASS**, then full suite.
- [ ] **Step 5: Commit** — `feat(catalog): Cloudinary signed uploads with folder-locked image URLs`

---

### Task 10: Web — shared pieces, organizer application, organizer shell

**Files:**
- Create: `stagepass-web/src/lib/datetime.ts`, `src/lib/money.ts`, `src/lib/cloudinary.ts`, `src/lib/api/types.ts` (TS mirrors of the API DTOs above)
- Create: `src/features/organizer/api.ts` (TanStack Query hooks), `features/organizer/OrganizerLayout.tsx`, `features/organizer/OrganizerHomePage.tsx`, `features/organizer/BecomeOrganizerPage.tsx`, `features/organizer/applicationSchema.ts`, `components/upload/ImageUploader.tsx`
- Modify: `src/app/router.tsx` (`/become-organizer` RequireAuth; `/organizer/*` RequireRole ORGANIZER inside `OrganizerLayout`)
- Test: `src/lib/datetime.test.ts`, `src/lib/money.test.ts`, `src/lib/cloudinary.test.ts`, `features/organizer/applicationSchema.test.ts`, `features/organizer/BecomeOrganizerPage.test.tsx`

**Interfaces:**
- `istInputToIso(value: string): string` (`'2026-12-20T19:00'` → `'2026-12-20T19:00:00+05:30'`); `isoToIstInput(iso: string): string` (any offset → IST local string); `formatEventDate(iso): string` (e.g. `Sat, 20 Dec 2026 · 7:00 PM`, en-IN, Asia/Kolkata).
- `rupeesToPaise(input: string): number` (`'499'` → 49900, `'499.5'` → 49950, rejects >2 decimals / negatives with `Error`); `formatPaise(paise: number): string` (`49900` → `₹499`, `49950` → `₹499.50`, `0` → `Free`).
- `uploadImage(file: File, sig: UploadSignature): Promise<string>` — rejects files > `sig.maxBytes` or types not in `allowedFormats` before uploading, posts `FormData(file, api_key, timestamp, signature, folder, allowed_formats)` to `sig.uploadUrl`, resolves `secure_url`.
- `applicationSchema` (zod) with the same regexes as Global Constraints, upper-casing PAN/GSTIN/IFSC.
- `BecomeOrganizerPage`: no application → form (sections: Organization, Contact, Tax, Payouts; logo via `ImageUploader` purpose `ORGANIZER_LOGO`); `PENDING` → status card "Under review"; `REJECTED` → reason + prefilled form to reapply; `APPROVED` → link to `/organizer`.
- `OrganizerLayout` nav: Overview `/organizer`, Events `/organizer/events`, Venues `/organizer/venues`, Scanner (disabled, "Coming soon").

- [ ] **Step 1: Write failing tests**

```ts
test('istInputToIso adds +05:30')                    // '2026-12-20T19:00' → '2026-12-20T19:00:00+05:30'
test('isoToIstInput converts UTC to IST')            // '2026-12-20T13:30:00Z' → '2026-12-20T19:00'
test('rupeesToPaise handles decimals and rejects 3dp') // '499.5' → 49950; '1.234' throws
test('formatPaise shows Free for zero')
test('uploadImage refuses oversized files without network') // 6 MB → rejects, fetch not called
test('uploadImage posts signed form and returns secure_url')
test('application schema rejects bad PAN and IFSC')  // errors on pan, payoutIfsc
test('rejected applicant sees reason and can reapply') // mocked hook status REJECTED reason 'PAN mismatch' → text visible, submit calls mutation
```

- [ ] **Step 2: Run — expect FAIL.** `cd stagepass-web && npx vitest run`
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run — expect PASS**; `npm run lint`, `npm run build`.
- [ ] **Step 5: Commit** — `feat(web): organizer application and organizer shell`

---

### Task 11: Web — organizer events, event editor, ticket types, venues

**Files:**
- Create: `features/organizer/events/EventsListPage.tsx`, `events/EventEditorPage.tsx`, `events/steps/BasicsStep.tsx`, `steps/WhenWhereStep.tsx`, `steps/TicketsStep.tsx`, `steps/MediaStep.tsx`, `steps/ReviewStep.tsx`, `events/eventSchema.ts`, `events/StatusBadge.tsx`, `features/organizer/venues/VenuesPage.tsx`, `venues/VenueForm.tsx`
- Modify: `src/app/router.tsx` (`/organizer/events`, `/organizer/events/new`, `/organizer/events/:id/:step?`, `/organizer/venues`)
- Test: `events/eventSchema.test.ts`, `events/TicketsStep.test.tsx`, `events/ReviewStep.test.tsx`

**Interfaces:**
- Editor route `/organizer/events/:id/:step` with steps `basics | when-where | tickets | media | review`; "Save and continue" PATCHes and moves on; new event POSTs on first save then navigates to `/organizer/events/{id}/when-where`. Stepper shows completion per step from `EventResponse` fields.
- `TicketsStep`: table of ticket types with inline add/edit dialog (name, price ₹, quantity, sale window, min/max per order); shows sold/available; disables price field when `sold > 0` with hint; maps API 409 detail into a toast.
- `ReviewStep`: renders a preview card plus a checklist mirroring the server's submit rules; "Submit for review" calls `/submit` and maps `errors[]` back to the checklist; shows `rejectionReason` banner when `REJECTED`; shows status for `PENDING_REVIEW`/`PUBLISHED`.
- `WhenWhereStep`: IST datetime inputs (`istInputToIso`), venue select + "New venue" sheet (`VenueForm`), online toggle with URL; Google Places autocomplete is **not** in this plan (Plan 3 adds Maps); lat/lng optional manual fields.
- `EventsListPage`: tabs All / Drafts / In review / Live / Past & cancelled, cards with cover, date, status badge, sold/capacity, "Continue editing".

- [ ] **Step 1: Write failing tests**

```ts
test('event schema requires end after start')
test('tickets step locks price after first sale')       // ticketType sold 3 → price input disabled, hint visible
test('tickets step shows server minimum on 409')        // mocked mutation rejects ApiError(409,'CONFLICT','…at least 45…') → toast text contains 45
test('review step maps submit errors onto checklist')   // mocked submit rejects errors [{field:'coverImageUrl'}] → "Add a cover image" item marked missing
test('review step shows rejection reason')
```

- [ ] **Step 2: Run — expect FAIL.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run — expect PASS**; lint; build; browser check: create draft → add venue → two ticket types → upload cover (needs Cloudinary keys; if absent, verify the signature-unavailable message instead) → submit.
- [ ] **Step 5: Commit** — `feat(web): organizer event editor with ticket types and venues`

---

### Task 12: Web — admin area (organizers, event review, categories)

**Files:**
- Create: `features/admin/AdminLayout.tsx`, `features/admin/api.ts`, `admin/AdminHomePage.tsx`, `admin/OrganizerApplicationsPage.tsx`, `admin/EventReviewPage.tsx`, `admin/EventReviewDetail.tsx`, `admin/CategoriesPage.tsx`, `components/feedback/ReasonDialog.tsx`
- Modify: `src/app/router.tsx` (`/admin/*` RequireRole ADMIN)
- Test: `admin/OrganizerApplicationsPage.test.tsx`, `admin/EventReviewDetail.test.tsx`, `components/feedback/ReasonDialog.test.tsx`

**Interfaces:**
- `AdminLayout` nav: Overview, Organizers, Event review, Categories (Users, Transactions, Analytics greyed "Coming soon" for Plan 8).
- `OrganizerApplicationsPage`: tabs Pending / Approved / Rejected; row expands to full details (masked account); Approve (confirm) and Reject (`ReasonDialog`, reason required, 500 chars).
- `EventReviewDetail`: full event preview (cover, gallery, description, venue, ticket types with prices), Approve, Reject (`ReasonDialog`), Feature toggle when `PUBLISHED`.
- `CategoriesPage`: list with active switch, inline edit name/icon/sort, add category.

- [ ] **Step 1: Write failing tests**

```ts
test('reason dialog requires a reason before confirming')
test('approving an application calls the API and removes it from pending')
test('event review shows ticket prices and approves')   // ₹499 and ₹1,499 visible; approve mutation called with id
```

- [ ] **Step 2: Run — expect FAIL.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run — expect PASS**; lint; build; browser check with test accounts: `organizer@stagepass.test` applies → `admin@stagepass.test` approves → organizer creates & submits event → admin approves.
- [ ] **Step 5: Commit** — `feat(web): admin review for organizers, events and categories`

---

## Self-review notes

- Spec coverage: §6 organizer onboarding (T3, T4, T10), §7 tables (T2 + V8), §8.1 lifecycle (T6, T8), §8.4a published edits/reschedule window (T6), §8.8 images (T9, T10), §9 organizer Create/Manage Events + Venues (T11), admin Event Approval, Organizers, Categories (T12), §11 ownership/validation/audit (all), emails (T4, T8).
- Deferred by design: public event pages & search (Plan 3), Google Maps/Places (Plan 3), refunds on cancel (Plan 4 via `onCancelled`), organizer dashboard KPIs (Plan 6), admin users/transactions/analytics (Plan 8), organizer suspension UI (Plan 8).
