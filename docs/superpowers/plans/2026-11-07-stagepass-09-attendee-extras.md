# Stagepass Attendee Extras Implementation Plan (Plan 9 of 11)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Attendees get a real account area:
- a profile they can edit, with a photo and a reminder setting;
- a bell with in-app notifications for everything Stagepass emails them about;
- an email and notification 24 hours before each event;
- saved events (a heart on every event card and on the event page);
- star reviews for events they attended, shown on the event page and the organizer's public profile.

**Architecture:**
- **New `engagement` backend module** (as spec §5.2 names it): saved events and reviews.
- **`notification` module grows:**
  - `NotificationService`, which writes rows to the existing `notifications` table with a dedupe key;
  - `ReminderJob`.
- **Hooks for notifications:** notifications are written in the same places that already queue emails — `BookingEmails`, `AdminOrganizerService`, `EventLifecycleService` — so every important email also shows in the bell.
- **Profile:** extends the existing `/api/v1/me`.
- **Web:**
  - replaces `AccountPlaceholderPage`;
  - adds a bell menu to the header;
  - adds a heart button to `EventCard` and the event page;
  - adds a reviews section to the event page.

**Tech Stack:** as Plans 1–8; no new libraries.

**Spec:** `docs/superpowers/specs/2026-11-05-stagepass-design.md`:
- §5.2 `engagement` module;
- §7 `users`, `reviews`, `saved_events`, `notifications`;
- §8.7 popularity "recent bookings weighted + saves";
- §8.9 event reminder email (24 h before) and in-app notifications with a bell menu;
- §9 Attendee pages: Dashboard (upcoming tickets), Profile/edit, Saved events;
- §15 milestone 10.

## Global Constraints

- All Plan 1–8 Global Constraints apply. Money is in paise. Times are shown in IST. Errors are RFC 9457 with a `code` field.
- **Notifications:**
  - Created through `NotificationService.notify(UUID userId, String type, String title, String body, String link, String dedupeKey)`.
  - The insert is `on conflict (dedupe_key) do nothing`, and it joins the caller's transaction.
  - `title` is at most 120 characters and `body` at most 300; longer text is cut with "…".
  - `link` is an in-app path starting with `/`, never a full URL.
- **When a notification is written, and its type:**

  | Event | Type | Dedupe key |
  |---|---|---|
  | Booking confirmed | `BOOKING_CONFIRMED` | `booking-confirmed:{bookingId}` |
  | Refund processed | `REFUND_PROCESSED` | `refund-processed:{refundId}` |
  | Refund declined | `REFUND_DECLINED` | `refund-declined:{refundId}` |
  | Event cancelled | `EVENT_CANCELLED` | `event-cancelled:{bookingId}` |
  | Event changed | `EVENT_CHANGED` | `event-changed:{bookingId}:{rescheduleRefundUntil}` |
  | Reminder | `EVENT_REMINDER` | `event-reminder:{bookingId}:{startsAt epoch seconds}` |
  | Organizer approved / rejected | `ORGANIZER_APPROVED` / `ORGANIZER_REJECTED` | `organizer-decision:{organizerId}:{status}` |
  | Event approved / rejected | `EVENT_APPROVED` / `EVENT_REJECTED` | `event-decision:{eventId}:{status}:{reviewedAt epoch seconds}` |

  - Booking notifications go to `bookings.user_id`.
  - Organizer and event decisions go to the organizer's `user_id`.
  - Links:
    - booking-related → `/account/bookings/{bookingId}`;
    - organizer decisions → `/organizer` (approved) or `/become-organizer` (rejected);
    - event decisions → `/organizer/events/{eventId}`.
- **Notification API (signed in):**
  - `GET /api/v1/me/notifications?page&size` returns `PageResponse<NotificationView(id, type, title, body, link, createdAt, read)>`, newest first.
  - `GET /api/v1/me/notifications/unread-count` returns `{count}`.
  - `POST /api/v1/me/notifications/{id}/read` returns 204; another user's id or an unknown id gives 404.
  - `POST /api/v1/me/notifications/read-all` returns 204.
- **24-hour reminders:** `ReminderJob` runs every 15 minutes. Each run, one transaction per booking:
  - It picks bookings with status `CONFIRMED` or `PARTIALLY_REFUNDED`, at least one `VALID` ticket, `reminder_sent_at is null`, and an event that is `PUBLISHED` with `starts_at` between now + 1 h and now + 24 h.
  - The candidate list is locked `for update skip locked` and capped at 200 bookings per run.
  - For each booking it sets `reminder_sent_at = now()` and writes the `EVENT_REMINDER` notification.
  - It also queues email `EVENT_REMINDER` (same dedupe key as the notification, to `bookings.attendee_email`) unless the booking's user has `email_reminders = false`.
  - Email subject: `Tomorrow: {title}` when the event's IST date is tomorrow, otherwise `Today: {title}`.
  - The email body gives the IST start time and the venue (or "Online"), and links to the tickets at `{webBaseUrl}/account/bookings/{id}`.
  - When an event's date or venue changes (the existing `EventChangeHandler`, implemented by `EventChangeEmails`), `reminder_sent_at` is reset to null for that event's bookings. Because the dedupe key holds the start time, a new time gets its own reminder, while a venue-only change does not re-send one (its EVENT_CHANGED email covers it).
- **Saved events (signed in):**
  - `PUT /api/v1/me/saved/{eventId}` returns 204 and is idempotent. It works only for events visible in discovery (`PUBLISHED` and not ended, organizer not suspended); otherwise 404.
  - `DELETE /api/v1/me/saved/{eventId}` returns 204 and is idempotent; deleting an unknown or unsaved event is still 204.
  - `GET /api/v1/me/saved?page&size` returns `PageResponse<PublicEventCard>`, newest save first, including events that have since ended (marked by their status).
  - `GET /api/v1/me/saved/ids` returns `List<UUID>` for up to 500 most recent saves, so the web can fill hearts.
  - Popularity becomes tickets as before plus 0.5 × saves made in the last 30 days.
- **Reviews:**
  - **Who:** a user can review event E when E has ended (`ends_at < now`, status `COMPLETED` or `PUBLISHED`), and they hold a booking for E with status `CONFIRMED` or `PARTIALLY_REFUNDED` and at least one ticket `VALID` or `CHECKED_IN`. Fully refunded bookings and cancelled events cannot be reviewed.
  - **Endpoints (signed in):**
    - `PUT /api/v1/events/{eventId}/review {rating 1–5, comment? ≤1000}` creates (201) or updates (200) your own review. Not eligible → 403 `FORBIDDEN` "You can review events you attended, after they end".
    - `DELETE /api/v1/events/{eventId}/review` removes it and returns 204.
    - `GET /api/v1/events/{eventId}/review/mine` returns `{eligible, review|null}`.
  - **Public:** `GET /api/v1/public/events/{slug}/reviews?page&size` returns `PageResponse<PublicReview(id, rating, comment, authorName, createdAt, updatedAt)>`, newest first.
    - `authorName` is the first name only, or "Stagepass guest" when there is no name — never an email.
  - **Rating on public records:** `PublicEventDetail` gains `rating: RatingSummary(average (1 decimal, null when count 0), count)`. `PublicOrganizerProfile` gains the same across all the organizer's events.
  - **Admin:** `DELETE /api/v1/admin/reviews/{id}` hard-deletes a review, with audit `REVIEW_REMOVED` holding before = `{eventId, userId, rating, comment}`.
- **Profile:**
  - `GET /me` gains `emailReminders` (boolean, default true).
  - `PATCH /me` accepts `emailReminders`.
  - `avatarUrl` must be null/empty (removes the photo) or a URL our Cloudinary account serves from the folder `stagepass/avatars/{userId}`, checked with `ImageUrlPolicy.isOwned`; otherwise 400 field `avatarUrl`.
  - Avatar upload signature: `POST /api/v1/uploads/signature {purpose: "AVATAR"}` signs the folder `stagepass/avatars/{userId}`.
- **Web:**
  - **Account page (`/account`):**
    - a header with avatar, name and email;
    - "Upcoming tickets" (the next 3 upcoming bookings, linking to all);
    - a profile form (name, phone, city, photo upload with remove);
    - a "Notifications" card with the "Email me a reminder 24 hours before my events" switch.
  - **Saved events:** at `/account/saved`, a grid of `EventCard`s with an empty state.
  - **Heart button:**
    - `aria-label` "Save event" / "Remove from saved", with `aria-pressed`;
    - optimistic toggle that rolls back with a toast on error;
    - signed-out users go to `/login?next=<current path>`.
  - **Bell:**
    - in the header for signed-in users;
    - a badge with the unread count (9+ when above 9), refreshed every 60 s and on window focus;
    - the menu lists the latest 10, each as a link that marks it read, plus "Mark all as read";
    - empty state "You're all caught up".
  - **Reviews on the event page:**
    - a summary of stars and count;
    - the 5 newest, with "Show more";
    - for eligible users a "Rate this event" / "Edit your review" dialog (1–5 stars as radio buttons, comment ≤1000);
    - admins see "Remove" on each review.
  - **Mobile tab bar:** gains no new tab; Saved is reached from the account page and the header menu.

## Review Focus

1. **A booking whose event is rescheduled after its reminder went out.** It gets a second reminder for the new time, never two for the same time. Test in Task 2.
2. **Two reminder job runs overlapping** (slow run, next tick). Each booking gets exactly one email and one notification. Test in Task 2 (`skip locked` plus dedupe, threads).
3. **A fully refunded attendee, or someone who never booked, tries to review**, including after the event ends. Gets 403, and nothing shows publicly. Test in Task 4.
4. **Saving a draft, rejected or suspended organizer's event by id** gives 404 and no row; double-saving gives one row. Test in Task 3.
5. **A user marking another user's notification as read by id** gets 404, and the other user's row is unchanged. Test in Task 1.

---

### Task 1: In-app notifications

**Files:**
- Create: `db/migration/V15__attendee_extras.sql`, holding all Plan 9 schema:
  - `notifications.dedupe_key text` with a unique index;
  - `users.email_reminders boolean not null default true`;
  - `bookings.reminder_sent_at timestamptz`;
  - an index `bookings_reminder_idx on bookings (event_id) where reminder_sent_at is null`;
  - an index `saved_events_user_created_idx on saved_events (user_id, created_at desc)`;
  - an index `reviews_event_created_idx on reviews (event_id, created_at desc)`.
- Create in `notification`: `service/NotificationService.java`, `api/NotificationController.java`, `api/NotificationView.java`
- Modify: `booking/service/BookingEmails.java` (notify next to each email it already queues: confirmed, refundProcessed, refundDeclined, eventCancelled, eventChanged), `admin/service/AdminOrganizerService.java` (approve/reject), `catalog/service/EventLifecycleService.java` (approve/reject)
- Test: `notification/NotificationTest.java`

**Interfaces:**
- `NotificationService`:
  - `notify(UUID userId, String type, String title, String body, String link, String dedupeKey): void`
  - `list(UUID userId, Integer page, Integer size): PageResponse<NotificationView>`
  - `unreadCount(UUID userId): long`
  - `markRead(UUID userId, UUID id)`
  - `markAllRead(UUID userId)`
- Endpoints as in Global Constraints.

- [ ] **Step 1: Write failing tests**

```java
@Test void confirmedBookingNotifiesTheBuyerOnce()      // support.confirmed → one BOOKING_CONFIRMED row for buyer with link /account/bookings/{id}; settle again (webhook replay) → still one
@Test void refundAndCancellationNotify()               // attendee self-refund (in policy) → REFUND_PROCESSED after onProcessed; organizer cancels event → EVENT_CANCELLED for each holder
@Test void organizerAndEventDecisionsNotifyTheOrganizer() // approve application → ORGANIZER_APPROVED link /organizer; reject event → EVENT_REJECTED link /organizer/events/{id}
@Test void listUnreadCountAndMarkRead()                // newest first; unread-count 2 → mark one → 1 → read-all → 0; read flag in list
@Test void cannotTouchAnotherUsersNotification()       // other user's id → 404 and row still unread; unknown id → 404
@Test void longTextIsTrimmed()                         // 200-char title stored as 120 chars ending "…"
@Test void signedOutGets401()
```

- [ ] **Step 2: Run — expect FAIL.** `cd stagepass-api && ./mvnw -q test -Dtest=NotificationTest`
- [ ] **Step 3: Implement.** Notification titles are short, plain sentences. Examples: "Booking confirmed: {title}", "Refund of Rs. X processed", "{title} was cancelled", "{title} has a new date", "You're approved to sell on Stagepass", "{title} is live". Bodies say one more useful thing, such as the date, the amount or the reason.
- [ ] **Step 4: Run — expect PASS**, then the full suite.
- [ ] **Step 5: Commit** — `feat(notification): in-app notifications for bookings, refunds and approvals`

### Task 2: 24-hour reminders

**Files:**
- Create: `notification/service/ReminderJob.java`
- Modify: `notification/service/EmailTemplate.java` and `EmailRenderer.java` (`EVENT_REMINDER`), the existing `EventChangeHandler` implementation for reschedules (reset `reminder_sent_at`)
- Test: `notification/ReminderJobTest.java`, `notification/ConcurrentReminderTest.java`, an `EmailRendererTest` case

**Interfaces:**
- `ReminderJob.run(): int`: the number of bookings reminded. It is `@Scheduled(cron = "0 */15 * * * *")` through a package-private `scheduled()`, like `PopularityJob`.
- It consumes `NotificationService.notify` (Task 1) and `EmailOutbox.enqueue(to, template, data, dedupeKey)`.

- [ ] **Step 1: Write failing tests**

```java
@Test void remindsOnceInsideTheWindow()            // event starts in 20 h → run → 1 email EVENT_REMINDER to attendee_email + 1 notification; run again → 0, still 1 each
@Test void outsideTheWindowOrNotLiveIsSkipped()    // starts in 30 h, in 30 min, CANCELLED event, fully refunded booking, PENDING_PAYMENT booking → nothing
@Test void emailPreferenceOffStillNotifiesInApp()  // users.email_reminders=false → notification yes, email no
@Test void rescheduleGetsANewReminder()            // remind → change starts_at (through the organizer edit endpoint that fires EventChangeHandler) to another time in window → run → second email/notification with the new key; run again → no third
// ConcurrentReminderTest
@Test void overlappingRunsSendOnce()               // 5 bookings; 3 threads call run() together → exactly 5 emails, 5 notifications
// EmailRendererTest
@Test void reminderSaysTomorrowOrToday()           // startsAt tomorrow IST → "Tomorrow: Sunburn Goa", venue line, link /account/bookings/{id}
```

- [ ] **Step 2: Run — expect FAIL.** `./mvnw -q test -Dtest='ReminderJobTest,ConcurrentReminderTest,EmailRendererTest'`
- [ ] **Step 3: Implement.**
  - The select takes candidate ids with `for update of b skip locked limit 200`.
  - Each booking is handled in `REQUIRES_NEW` through a `TransactionTemplate`, so one failure doesn't stop the rest; the failure is logged.
  - "Today" versus "tomorrow" is decided by the IST date of `starts_at` compared with the IST date at render time.
- [ ] **Step 4: Run — expect PASS**, then the full suite.
- [ ] **Step 5: Commit** — `feat(notification): 24-hour event reminders by email and in-app`

### Task 3: Saved events

**Files:**
- Create in `com.stagepass.engagement`: `service/SavedEventService.java`, `api/SavedEventController.java`
- Modify: `discovery/service/PopularityJob.java` (saves), and reuse `discovery`'s `PublicEventCard` mapping for the saved list (extract the row mapper into a package-visible helper if it is private)
- Test: `engagement/SavedEventTest.java`, a `PopularityJobTest` case

**Interfaces:**
- `SavedEventService`:
  - `save(UUID userId, UUID eventId)`
  - `unsave(UUID userId, UUID eventId)`
  - `list(UUID userId, Integer page, Integer size): PageResponse<PublicEventCard>`
  - `ids(UUID userId): List<UUID>`

- [ ] **Step 1: Write failing tests**

```java
@Test void saveIsIdempotentAndListed()             // PUT twice → one row; GET /me/saved has the card; ids contains it; newest save first across two events
@Test void onlyVisibleEventsCanBeSaved()           // draft, rejected, ended, suspended organizer, unknown id → 404, no row
@Test void unsaveIsIdempotent()                    // DELETE twice → 204, 204; not in ids
@Test void endedSavedEventStaysInTheList()         // save, then event COMPLETED → still listed with status COMPLETED
@Test void signedOutGets401()
// PopularityJobTest
@Test void savesAddHalfATicket()                   // event with 2 tickets sold this week + 2 saves → score 3.0
```

- [ ] **Step 2: Run — expect FAIL.** `./mvnw -q test -Dtest='SavedEventTest,PopularityJobTest'`
- [ ] **Step 3: Implement.** `insert … on conflict do nothing`. Visibility uses the discovery rule (PUBLISHED, `ends_at > now`, organizer not suspended).
- [ ] **Step 4: Run — expect PASS**, then the full suite.
- [ ] **Step 5: Commit** — `feat(engagement): saved events and saves in popularity`

### Task 4: Reviews and ratings

**Files:**
- Create in `engagement`: `service/ReviewService.java`, `api/ReviewController.java` (signed-in endpoints), `api/PublicReviewController.java`, `api/ReviewRequest.java`, `api/PublicReview.java`, `api/RatingSummary.java`, `api/MyReview.java`; `admin/api/AdminReviewController.java`
- Modify: `discovery/api/PublicEventDetail.java` and `PublicOrganizerProfile.java` (+ `rating`), plus the services that build them
- Test: `engagement/ReviewTest.java`

**Interfaces:**
- `ReviewService`:
  - `upsert(UUID userId, UUID eventId, ReviewRequest r): Saved(PublicReview review, boolean created)`
  - `delete(UUID userId, UUID eventId)`
  - `mine(UUID userId, UUID eventId): MyReview(boolean eligible, PublicReview review)`
  - `forEvent(String slug, Integer page, Integer size): PageResponse<PublicReview>`
  - `eventRating(UUID eventId): RatingSummary`
  - `organizerRating(UUID organizerId): RatingSummary`
  - `adminDelete(UUID adminId, UUID reviewId)`

- [ ] **Step 1: Write failing tests**

```java
@Test void attendeeReviewsAfterTheEvent()          // confirmed booking, event ended → PUT 201; again with new rating → 200 same id; public list shows first name only; event detail rating {4.0,1}
@Test void notEligibleIs403()                      // before the event ends; never booked; fully refunded booking; cancelled event → 403 each; mine.eligible false
@Test void checkedInTicketCounts()                 // ticket CHECKED_IN, event COMPLETED → eligible
@Test void validation()                            // rating 0 / 6 / missing → 400 rating; comment 1001 chars → 400 comment
@Test void deleteOwnAndAdminRemove()               // DELETE own → gone; admin DELETE /admin/reviews/{id} → gone + audit REVIEW_REMOVED; non-admin → 403
@Test void organizerProfileAveragesAllEvents()     // two events rated 5 and 3 → organizer rating {4.0, 2}
@Test void authorNameNeverLeaksEmail()             // user with null full_name → "Stagepass guest"; "Asha Rao" → "Asha"
```

- [ ] **Step 2: Run — expect FAIL.** `./mvnw -q test -Dtest=ReviewTest`
- [ ] **Step 3: Implement.** Upsert with `on conflict (event_id, user_id) do update … returning (xmax = 0) as created`. Averages are `round(avg(rating)::numeric, 1)`.
- [ ] **Step 4: Run — expect PASS**, then the full suite.
- [ ] **Step 5: Commit** — `feat(engagement): event reviews and organizer ratings`

### Task 5: Profile — photo, reminder preference, account API

**Files:**
- Modify: `identity/api/UpdateProfileRequest.java` (+ `Boolean emailReminders`), `identity/api/MeResponse.java` (+ `emailReminders`), `identity/service/ProfileService.java` (avatar folder check, preference), `identity/domain/User.java` (+ `emailReminders` column), `catalog/api/UploadSignatureRequest.java` (+ `AVATAR` purpose), `catalog/api/UploadController.java` and `catalog/service/UploadService.java` (`signAvatar(userId)`)
- Test: `identity/ProfileExtrasTest.java`

**Interfaces:**
- `UploadService.signAvatar(UUID userId): UploadSignatureResponse` (folder `stagepass/avatars/{userId}`).

- [ ] **Step 1: Write failing tests**

```java
@Test void avatarMustBeOurCloudinaryFolder()       // other user's folder, other host, event folder → 400 avatarUrl; own folder → saved; "" → cleared
@Test void avatarUploadSignature()                 // purpose AVATAR → folder stagepass/avatars/{me}; purpose EVENT_IMAGE here → 400 purpose
@Test void reminderPreferenceRoundTrips()          // GET /me emailReminders true; PATCH false → GET false; PATCH {} leaves it
```

- [ ] **Step 2: Run — expect FAIL.** `./mvnw -q test -Dtest=ProfileExtrasTest`
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run — expect PASS**, then the full suite.
- [ ] **Step 5: Commit** — `feat(identity): profile photo upload and reminder preference`

### Task 6: Web — account page, saved events, bell

**Files:**
- Create in `stagepass-web/src/features/account/`: `AccountPage.tsx` (replaces `AccountPlaceholderPage.tsx`, which is deleted), `ProfileForm.tsx`, `AvatarUpload.tsx` (reuses `lib/cloudinary` upload helper with the AVATAR signature), `SavedEventsPage.tsx`, `api.ts`, `types.ts`
- Create: `features/engagement/SaveButton.tsx`, `features/engagement/useSaved.ts` (ids query + optimistic toggle); `components/layout/NotificationBell.tsx`, `features/notifications/api.ts`
- Modify: `components/events/EventCard.tsx` (heart in the top-right corner of the image), `features/event/EventPage.tsx` (heart next to Share), `components/layout/SiteHeader.tsx` (bell), `components/layout/HeaderAccount.tsx` ("Saved events" link), `app/router.tsx` (`/account` → `AccountPage`, `/account/saved`)
- Test: `AccountPage.test.tsx`, `SavedEventsPage.test.tsx`, `SaveButton.test.tsx`, `NotificationBell.test.tsx`

- [ ] **Step 1: Write failing tests**

```tsx
test('profile form saves changed fields and shows field errors')    // phone "12345" → 400 phone message under field
test('reminder switch saves emailReminders')
test('upcoming tickets show the next three bookings')
test('save button toggles optimistically and rolls back on error') // aria-pressed true then false + toast
test('signed-out save sends to login with next')
test('bell shows 9+ and marks an item read when opened')           // count 12 → "9+"; click item → markRead(id) and navigate to link
test('saved page empty state')                                      // "Nothing saved yet" + Explore link
```

- [ ] **Step 2: Run — expect FAIL.** `cd stagepass-web && npx vitest run src/features/account src/features/engagement src/components/layout`
- [ ] **Step 3: Implement** to the Global Constraints. The bell polls with `refetchInterval: 60_000` and `refetchOnWindowFocus`. Existing `EventCard` tests must keep passing; if they render without auth, wrap the heart in a component that reads auth safely, as `ReportEventLink` does.
- [ ] **Step 4: Run — expect PASS**; lint, tsc and build clean.
- [ ] **Step 5: Commit** — `feat(web): account page, saved events and notification bell`

### Task 7: Web — reviews

**Files:**
- Create in `features/engagement/`: `ReviewsSection.tsx` (summary, list, "Show more", admin Remove), `ReviewDialog.tsx`, `Stars.tsx` (display-only stars with an accessible label "4.5 out of 5"), `reviewsApi.ts`
- Modify: `features/event/EventPage.tsx` (Reviews section for events that have ended or have reviews), `features/organizer-public/OrganizerProfilePage.tsx` (rating next to the name), `features/discovery/types.ts` (+ `rating`)
- Test: `ReviewsSection.test.tsx`, `ReviewDialog.test.tsx`

- [ ] **Step 1: Write failing tests**

```tsx
test('summary and newest reviews show, with show more')        // "4.0 · 12 reviews", 5 items, Show more loads page 1
test('eligible user can rate; ineligible sees no button')
test('dialog sends rating and comment and edits an existing review')
test('admin sees Remove and it asks to confirm')
```

- [ ] **Step 2: Run — expect FAIL.** `npx vitest run src/features/engagement src/features/event`
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run — expect PASS**; lint, tsc and build clean; full web suite.
- [ ] **Step 5: Commit** — `feat(web): event reviews and organizer rating`

---

## Rulings to confirm with the user (assistant's calls)

1. **Who can review:** only people who had a valid (not fully refunded) ticket, and only after the event ends. There is no time limit after that, and no organizer replies yet.
2. **Reminder timing:** a booking made less than an hour before the event gets no reminder. Reminders go to the email on the booking, and can be switched off in the profile; the in-app reminder always shows.
3. **Saves in popularity:** a save counts as half a ticket.
4. **Removing reviews:** admins can delete a review; organizers can't. Reviews show the reviewer's first name only.
5. **Profile photos:** must be uploaded through Stagepass (Cloudinary folder per user). Other image links are refused.
