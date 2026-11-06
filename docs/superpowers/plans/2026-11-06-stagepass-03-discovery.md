# Stagepass Public Discovery Implementation Plan (Plan 3 of 11)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Anyone (signed in or not) can browse live events on the homepage, search and filter them on Explore, open a full event page with tickets, a map and sharing, view an organizer's public profile, and share a link that previews properly on WhatsApp.

**Architecture:** New backend module `com.stagepass.discovery` — read-only, unauthenticated endpoints under `/api/v1/public/**` built on `NamedParameterJdbcTemplate` queries (dynamic filters, full-text + trigram search, aggregates) rather than JPA, plus a server-rendered share page at `/e/{slug}`. A scheduled job completes ended events. Web adds public pages on `PublicLayout`, a shared `EventCard`, URL-synced filters, Google Maps Embed and Places Autocomplete. Booking is Plan 4 (the "Book" button is present but says "Tickets on sale soon").

**Tech Stack:** as Plans 1–2; Google Maps Embed API (iframe) and Maps JavaScript API Places library (`@googlemaps/js-api-loader`).

**Spec:** `docs/superpowers/specs/2026-11-05-stagepass-design.md` (§8.7 discovery, §9 public pages, §10 design).

## Global Constraints

- All Plan 1–2 Global Constraints apply.
- **Public visibility:** an event is listed only when `status = 'PUBLISHED'` and `ends_at > now()`. The detail page by slug also shows `CANCELLED` and `COMPLETED` events (read-only, with a banner) so ticket holders' links keep working; `DRAFT`, `PENDING_REVIEW`, `REJECTED` answer 404. Organizer profiles are public only when the organizer is `APPROVED`.
- **Public ticket types:** `HIDDEN` types are never returned or counted; "from" price = minimum `price_paise` over `ACTIVE` types; an event is `soldOut` when it has no `ACTIVE` type with `total - sold - held > 0`.
- **Time:** date presets are computed in `Asia/Kolkata`: `today`, `tomorrow`, `weekend` (on Mon–Fri: coming Sat 00:00 → Mon 00:00; on Sat or Sun: now → next Mon 00:00), `week` (now → +7 days), `month` (now → +30 days); custom `from`/`to` are IST dates (`YYYY-MM-DD`, inclusive). An event matches a range when it *overlaps* it (`starts_at < rangeEnd and ends_at > rangeStart`).
- **Search:** `q` (≤ 100 chars) matches `search_vector @@ websearch_to_tsquery('simple', q)` OR `similarity(title, q) > 0.25` OR venue name/city `ILIKE '%q%'` (with `%`, `_`, `\` escaped). Never string-concatenate user input into SQL.
- **Sort:** `popular` (default) = `is_featured desc, popularity_score desc, starts_at asc`; `soonest` = `starts_at asc`; `nearby` (requires `lat`/`lng`) = distance asc.
- **Near you:** haversine on venue `lat`/`lng`, default radius 50 km (max 200); events without coordinates are excluded from nearby; online events never count as nearby.
- Public endpoints set `Cache-Control: public, max-age=60` (lists/home) or `max-age=30` (event detail); never cache anything user-specific.
- Web env: `VITE_GOOGLE_MAPS_KEY` (browser key, restrict to the site's HTTP referrers in Google Cloud). With no key, maps show the address with an "Open in Google Maps" link and venue autocomplete falls back to manual fields.
- Share URL format: `{API public base}/e/{slug}` redirecting to `{webBaseUrl}/events/{slug}`. New config `stagepass.api-base-url` (`API_BASE_URL`, default `http://localhost:8080`).

## Review Focus

1. **Unpublished events leak** — a draft, in-review or rejected event's slug, or one in search results → 404 / absent everywhere public — tested in Tasks 1, 3.
2. **Odd search input** — quotes, `&`, `:`, `%`, `_`, emoji or Hindi text (`दिल्ली`) → 200 with sensible (possibly empty) results, never 500 — tested in Task 1.
3. **Hidden or sold-out tickets** — hidden types never appear and don't set the "from" price; an event whose on-sale types are all gone shows "Sold out" — tested in Tasks 1, 3, 7.
4. **IST date edges** — an event at 23:30 IST on Saturday is in "this weekend" and an event at 00:30 IST Monday is not, regardless of the server's UTC clock — tested in Task 1.
5. **Share page injection** — a title or summary containing `"`, `<script>` or `&` renders escaped in OG tags and the redirect target is always our own web URL — tested in Task 4.

---

### Task 1: Public event search

**Files:**
- Create in `com.stagepass.discovery`: `api/PublicEventController.java`, `api/PublicEventCard.java`, `api/EventSearchParams.java`, `service/EventSearchService.java`, `service/DateRanges.java`, `repository/PublicEventQueries.java`
- Create: `db/migration/V9__discovery_indexes.sql`
- Test: `discovery/DateRangesTest.java`, `discovery/PublicEventSearchTest.java`; extend `support/CatalogFixtures.java`

**Interfaces:**
- Produces: `GET /api/v1/public/events` with query `q, city, category (slug), date (today|tomorrow|weekend|week|month), from, to (IST dates), minPrice, maxPrice (rupees, integers), free (boolean), lat, lng, radiusKm, sort (popular|soonest|nearby), page, size` → `PageResponse<PublicEventCard>`; invalid enum/number → 400 `VALIDATION_FAILED`; `sort=nearby` without `lat`/`lng` → 400 field `lat`.
- Produces: `record PublicEventCard(UUID id, String slug, String title, String summary, String coverImageUrl, OffsetDateTime startsAt, OffsetDateTime endsAt, boolean online, String venueName, String city, String categorySlug, String categoryName, Long minPricePaise, boolean soldOut, boolean featured, Double distanceKm)`.
- Produces: `DateRanges.resolve(String preset, LocalDate from, LocalDate to, Clock clock): Optional<Range>` with `record Range(OffsetDateTime start, OffsetDateTime end)` in IST; `Clock` bean (`Clock.systemUTC()`) injectable so tests pin "now".
- Produces: `PublicEventQueries.search(EventSearchParams p, Range range, Pageable page): Page<PublicEventCard>` — one SQL with a `lateral` aggregate over `ticket_types`/`ticket_inventory` for min price and availability; count query shares the same `WHERE`.
- V9: index `events (status, ends_at)`, GIN trigram index on `venues (lower(city))` and `venues (lower(name))`.
- `CatalogFixtures.publishedEvent(TestUser org, String title, OffsetDateTime start, String city, long pricePaise)` → UUID (venue with lat/lng, cover, one ACTIVE type qty 100).

- [ ] **Step 1: Write failing tests**

```java
// DateRangesTest (Clock fixed per test)
@Test void weekendFromWednesdayIsComingSatToMon()       // now Wed 2026-11-11 10:00 IST → [Sat 14 00:00, Mon 16 00:00) +05:30
@Test void weekendOnSundayStartsNow()                   // now Sun 15 09:00 IST → [Sun 15 09:00, Mon 16 00:00) +05:30
@Test void todayUsesIstNotUtc()                         // clock at 2026-11-11T20:00Z (= 12 Nov 01:30 IST) → today = 12 Nov IST
@Test void customRangeIsInclusiveOfEndDate()            // from 2026-12-01 to 2026-12-01 → [1 Dec 00:00, 2 Dec 00:00) IST
// PublicEventSearchTest
@Test void onlyPublishedUpcomingEventsAreListed()       // draft, pending, rejected, cancelled, ended-published → absent; published future → present
@Test void searchesTitleSummaryVenueAndCity()           // q "sunburn" (title), "techno" (summary), "arena" (venue), "pune" (city) each find their event
@Test void fuzzyTitleMatchesTypos()                     // q "sunbrn" finds "Sunburn Arena Tour"
@Test void oddSearchInputNeverFails()                   // q in ["\"", "a & b", "c:d", "%", "_", "😀", "दिल्ली", "' or 1=1 --"] → 200
@Test void filtersByCityCategoryPriceAndFree()          // city=Pune case-insensitive; category=comedy; minPrice=400&maxPrice=600; free=true
@Test void hiddenTypesDoNotSetFromPrice()               // ACTIVE ₹999 + HIDDEN ₹99 → minPricePaise 99900
@Test void soldOutWhenNoActiveAvailability()           // sold == total on the only ACTIVE type → soldOut true
@Test void weekendFilterUsesIstEdges()                  // clock Wed; event Sat 23:30 IST in; Mon 00:30 IST out
@Test void nearbySortsByDistanceAndRespectsRadius()     // Bengaluru event 2 km, Mysuru 140 km; lat/lng MG Road radius 50 → only Bengaluru, distanceKm ≈ 2
@Test void nearbyWithoutCoordinatesIs400()
@Test void popularPutsFeaturedFirst()
@Test void responseIsPublicAndCacheable()               // no Authorization header → 200, Cache-Control max-age=60
```

- [ ] **Step 2: Run — expect FAIL.** `cd stagepass-api && ./mvnw -q test -Dtest='DateRangesTest,PublicEventSearchTest'`
- [ ] **Step 3: Implement.** Distance SQL: `6371 * 2 * asin(sqrt(power(sin(radians(v.lat - :lat) / 2), 2) + cos(radians(:lat)) * cos(radians(v.lat)) * power(sin(radians(v.lng - :lng) / 2), 2)))`; pre-filter with a lat/lng bounding box. Escape `ILIKE` wildcards with `ESCAPE '\'`.
- [ ] **Step 4: Run — expect PASS**, then full suite.
- [ ] **Step 5: Commit** — `feat(discovery): public event search with IST date filters`

---

### Task 2: Home feeds and city list

**Files:**
- Create in `com.stagepass.discovery`: `api/PublicHomeController.java`, `api/HomeFeedResponse.java`, `api/CityResponse.java`, `service/HomeFeedService.java`
- Test: `discovery/HomeFeedTest.java`

**Interfaces:**
- Consumes: `PublicEventQueries.search` (Task 1).
- Produces: `GET /api/v1/public/home?city&lat&lng` → `HomeFeedResponse(List<PublicEventCard> featured, List<PublicEventCard> trending, List<PublicEventCard> thisWeekend, List<PublicEventCard> upcoming, List<PublicEventCard> nearby, List<CategoryRail> categories)`; `record CategoryRail(String slug, String name, String icon, List<PublicEventCard> events)` — only categories with ≥ 1 event, max 6 rails × 8 events. Sizes: featured ≤ 6, others ≤ 12. `nearby` uses lat/lng when given (radius 50 km), else events in `city`, else empty. When `city` is given, every feed except `nearby` is limited to that city plus online events.
- Produces: `GET /api/v1/public/cities` → `List<CityResponse(String name, int upcomingEvents)>` distinct venue cities with ≥ 1 listed event, most events first.
- Trending = `popularity_score desc` among events starting within 30 days (score is 0 until Plan 4 updates it, so ties fall back to soonest).

- [ ] **Step 1: Write failing tests**

```java
@Test void homeShowsFeaturedAndUpcoming()
@Test void cityLimitsFeedsButKeepsOnlineEvents()
@Test void nearbyUsesCoordinatesThenCity()
@Test void emptyCategoriesAreOmitted()
@Test void citiesListedByEventCount()
```

- [ ] **Step 2: Run — expect FAIL.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run — expect PASS**, then full suite.
- [ ] **Step 5: Commit** — `feat(discovery): home feeds and city list`

---

### Task 3: Public event page, organizer profile, completion job

**Files:**
- Create in `com.stagepass.discovery`: `api/PublicEventDetail.java`, `api/PublicTicketType.java`, `api/PublicOrganizer.java`, `api/PublicOrganizerProfile.java`, `service/PublicEventService.java`, `api/PublicOrganizerController.java`
- Create: `catalog/service/EventCompletionJob.java`
- Modify: `discovery/api/PublicEventController.java` (+ `GET /{slug}`)
- Test: `discovery/PublicEventDetailTest.java`, `catalog/EventCompletionJobTest.java`

**Interfaces:**
- Produces: `GET /api/v1/public/events/{slug}` → `PublicEventDetail(UUID id, String slug, String title, String summary, String description, String coverImageUrl, List<String> gallery, OffsetDateTime startsAt, OffsetDateTime endsAt, String timezone, String status, boolean online, VenueResponse venue, String categorySlug, String categoryName, String terms, Integer maxTicketsPerUser, String refundPolicy, Integer refundCutoffHours, boolean soldOut, Long minPricePaise, List<PublicTicketType> ticketTypes, PublicOrganizer organizer)`; `onlineUrl` is **never** included (ticket holders get it in Plan 5).
- `record PublicTicketType(UUID id, String name, String description, long pricePaise, String availability /* ON_SALE | SOLD_OUT | NOT_STARTED | ENDED */, OffsetDateTime saleStartsAt, OffsetDateTime saleEndsAt, int minPerOrder, int maxPerOrder, boolean fewLeft /* available ≤ 10% of total or ≤ 10 */)` — exact remaining counts are not exposed.
- `record PublicOrganizer(String name, String slug, String logoUrl, String bio, String website)`.
- `GET /api/v1/public/organizers/{slug}` → `PublicOrganizerProfile(PublicOrganizer organizer, List<PublicEventCard> upcoming, int pastEvents)`; non-APPROVED → 404.
- `EventCompletionJob` — `@Scheduled(cron = "0 */15 * * * *")` sets `PUBLISHED` → `COMPLETED` where `ends_at < now()`, unfeatures them, audit `EVENT_COMPLETED` (actor null); method `int completeEnded()` returns rows; disabled in tests via `stagepass.jobs.enabled=false` (scheduling bean conditional), the method is called directly.

- [ ] **Step 1: Write failing tests**

```java
@Test void publishedEventIsPublic()                   // no token → 200, organizer name, ticket types
@Test void draftPendingRejectedAre404()
@Test void cancelledEventStillLoadsWithStatus()       // status CANCELLED, ticket types still listed
@Test void hiddenTicketTypesAreNotExposed()
@Test void availabilityStates()                       // ON_SALE, SOLD_OUT, NOT_STARTED (saleStartsAt future), ENDED (saleEndsAt past)
@Test void onlineLinkIsNeverPublic()                  // online event → JSON has no onlineUrl
@Test void organizerProfileShowsUpcomingOnlyWhenApproved()
@Test void completionJobCompletesEndedEvents()        // ended PUBLISHED → COMPLETED, featured false; future untouched
```

- [ ] **Step 2: Run — expect FAIL.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run — expect PASS**, then full suite.
- [ ] **Step 5: Commit** — `feat(discovery): public event page, organizer profiles, completion job`

---

### Task 4: Share page with Open Graph tags

**Files:**
- Create: `discovery/api/SharePageController.java`, `discovery/service/SharePageRenderer.java`
- Modify: `common/config/StagepassProperties.java` (+ `apiBaseUrl`), `application.properties` (`stagepass.api-base-url=${API_BASE_URL:http://localhost:8080}`), `render.yaml`, README env table
- Test: `discovery/SharePageTest.java`

**Interfaces:**
- `GET /e/{slug}` → `text/html; charset=utf-8`, `Cache-Control: public, max-age=300`. Contains `<title>`, `og:title`, `og:description` (summary or first 160 chars of description), `og:image` (cover with Cloudinary transform `c_fill,w_1200,h_630,f_jpg` inserted after `/upload/`), `og:url` (the share URL), `og:type=website`, `twitter:card=summary_large_image`, `<link rel="canonical" href="{web}/events/{slug}">`, `<meta http-equiv="refresh" content="0; url={web}/events/{slug}">` and a visible fallback link. Unknown or non-public slug → 404 with the same minimal page pointing to `{web}/explore`.
- All values HTML-attribute-escaped (`HtmlUtils.htmlEscape`); slug path variable must match `^[a-z0-9-]{1,80}$` (else 404) so it can't break out of URLs.

- [ ] **Step 1: Write failing tests**

```java
@Test void sharePageHasOpenGraphTagsAndRedirects()   // og:title, og:image with c_fill,w_1200,h_630, refresh to http://localhost:5173/events/{slug}
@Test void titleAndSummaryAreEscaped()               // title `Rock "n" <script>alert(1)</script> & Roll` → &quot; &lt;script&gt; &amp; present, raw <script> absent
@Test void unknownSlugIs404WithExploreLink()
@Test void invalidSlugCharactersAre404()             // /e/..%2F..%2Fetc → 404
@Test void draftEventSharePageIs404()
```

- [ ] **Step 2: Run — expect FAIL.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run — expect PASS**, then full suite.
- [ ] **Step 5: Commit** — `feat(discovery): share page with Open Graph previews`

---

### Task 5: Web — public API, event card, homepage

**Files:**
- Create: `stagepass-web/src/features/discovery/api.ts`, `features/discovery/types.ts`, `components/events/EventCard.tsx`, `components/events/EventRail.tsx`, `components/events/EventCardSkeleton.tsx`, `features/discovery/useLocationPreference.ts`, `features/discovery/CityPicker.tsx`
- Modify: `features/home/HomePage.tsx` (replace static category grid with live feeds; keep hero + organizer CTA)
- Test: `components/events/EventCard.test.tsx`, `features/discovery/useLocationPreference.test.ts`, `features/home/HomePage.test.tsx`

**Interfaces:**
- `usePublicHome(params: { city?: string; lat?: number; lng?: number })`, `useCities()`, `usePublicEvents(filters: ExploreFilters)` (infinite query, `getNextPageParam` from `page/totalPages`), `usePublicEvent(slug)`, `usePublicOrganizer(slug)`.
- `<EventCard event={PublicEventCard} variant="grid" | "rail" />` — cover (Cloudinary `f_auto,q_auto,w_640` inserted after `/upload/`), date badge (`DD MMM` IST), title, venue · city or "Online", "From ₹X" / "Free" / "Sold out" pill, "Featured" chip; whole card links to `/events/{slug}`.
- `useLocationPreference(): { city: string | null; setCity(c); coords: {lat,lng} | null; requestCoords(): Promise<void>; status: 'idle'|'locating'|'denied'|'granted' }` — city persisted in `localStorage['stagepass-city']`; geolocation requested only from a user click ("Use my location"); denial sets `status='denied'` and keeps the city.
- Homepage sections (each hidden when empty, skeletons while loading): Featured (large cards), This weekend, Trending, Near you (city picker + "Use my location"), Upcoming, category rails, organizer CTA. Hero search submits to `/explore?q&city`.

- [ ] **Step 1: Write failing tests**

```ts
test('event card shows from-price, free and sold-out states')
test('event card links to the event page and formats date in IST')
test('location preference persists city and handles denied geolocation')  // navigator.geolocation mock calls error → status 'denied', city kept
test('homepage hides empty feeds and shows featured events')               // mocked usePublicHome
```

- [ ] **Step 2: Run — expect FAIL.** `cd stagepass-web && npx vitest run`
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run — expect PASS**; lint; build.
- [ ] **Step 5: Commit** — `feat(web): live homepage feeds and event cards`

---

### Task 6: Web — Explore page

**Files:**
- Create: `features/discovery/ExplorePage.tsx`, `features/discovery/filters.ts`, `features/discovery/FilterPanel.tsx`
- Modify: `src/app/router.tsx` (`/explore`)
- Test: `features/discovery/filters.test.ts`, `features/discovery/ExplorePage.test.tsx`

**Interfaces:**
- `type ExploreFilters = { q?: string; city?: string; category?: string; date?: 'today'|'tomorrow'|'weekend'|'week'|'month'; from?: string; to?: string; minPrice?: number; maxPrice?: number; free?: boolean; sort?: 'popular'|'soonest'|'nearby'; lat?: number; lng?: number }`.
- `parseFilters(search: URLSearchParams): ExploreFilters` (ignores invalid values) and `toSearchParams(f: ExploreFilters): URLSearchParams` (omits empty/default values; `price=free` from the footer link maps to `free=true`).
- Page: search box (debounced 300 ms) + city picker in a sticky header; filter chips for date presets and categories (horizontal scroll on mobile); "Filters" button opening a `Sheet` on mobile / side panel on `lg` with price range, free only, custom dates, sort; result count; grid of `EventCard`s; "Load more" button (infinite query); empty state with "Clear filters". All state lives in the URL (back/forward and shared links reproduce results).

- [ ] **Step 1: Write failing tests**

```ts
test('parses and serialises filters round-trip, dropping invalid values')
test('footer price=free link becomes free=true')
test('changing a chip updates the URL and query')         // mocked usePublicEvents called with { date: 'weekend' }
test('empty results offer clear filters')
```

- [ ] **Step 2: Run — expect FAIL.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run — expect PASS**; lint; build.
- [ ] **Step 5: Commit** — `feat(web): explore page with URL-synced filters`

---

### Task 7: Web — event page, organizer profile, share, maps

**Files:**
- Create: `features/event/EventPage.tsx`, `features/event/TicketList.tsx`, `features/event/ShareButton.tsx`, `features/event/VenueMap.tsx`, `features/event/calendar.ts`, `features/organizer-public/OrganizerProfilePage.tsx`, `lib/maps.ts`
- Modify: `src/lib/env.ts` (optional `googleMapsKey`), `src/app/router.tsx` (`/events/:slug`, `/o/:slug`), `.env.example`
- Test: `features/event/TicketList.test.tsx`, `features/event/ShareButton.test.tsx`, `features/event/calendar.test.ts`, `features/event/EventPage.test.tsx`

**Interfaces:**
- Event page layout: full-bleed cover with gradient, title, date/time (IST) with "Add to calendar" (`.ics` download), venue line, category; two-column on `lg` with the sticky ticket panel on the right; on mobile a sticky bottom bar ("From ₹X · Book" ). Sections: About (description, `whitespace-pre-line`), Gallery (lightbox via `Dialog`), Venue (map + address + "Open in Google Maps"), Organizer card linking to `/o/{slug}`, Terms, Refund policy in plain words. `CANCELLED` → red banner, booking disabled; `COMPLETED` → "This event has ended". Document title `"{title} · Stagepass"`.
- `TicketList` shows each public type: name, description, price, availability pill (On sale / Few left / Sold out / Sales open {date} / Sales ended); the Book button is disabled with text "Tickets on sale soon" until Plan 4 replaces it.
- `ShareButton({ url, title })` — `navigator.share` when available, else a menu with Copy link (clipboard + toast), WhatsApp (`https://wa.me/?text=` encoded "title url"), X (`https://twitter.com/intent/tweet?url=&text=`). `url` is the API share URL `{VITE_API_BASE_URL}/e/{slug}`.
- `VenueMap({ venue })` — with key: `<iframe src="https://www.google.com/maps/embed/v1/place?key=…&q=…">` using `lat,lng` when present else the encoded address, `loading="lazy"`, `referrerPolicy="no-referrer-when-downgrade"`, title for a11y; without key: static card + link `https://www.google.com/maps/search/?api=1&query=…`.
- `buildIcs({ title, startsAt, endsAt, location, url }): string` — RFC 5545 `VEVENT` with UTC `DTSTART/DTEND`, escaped commas/semicolons/newlines, `UID` = `{slug}@stagepass`.
- `OrganizerProfilePage` — logo, name, bio, website, upcoming events grid, "N past events".

- [ ] **Step 1: Write failing tests**

```ts
test('ticket list shows availability states and never exact counts')
test('book button is disabled until booking launches')
test('share uses Web Share API when available, else offers WhatsApp and copy')
test('ics escapes text and uses UTC times')
test('cancelled event shows banner and no booking')
test('map falls back to a Google Maps link without a key')
```

- [ ] **Step 2: Run — expect FAIL.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run — expect PASS**; lint; build.
- [ ] **Step 5: Commit** — `feat(web): public event page, organizer profiles, sharing and maps`

---

### Task 8: Web — venue Places autocomplete, final polish and review

**Files:**
- Create: `features/organizer/venues/PlacesAutocomplete.tsx`
- Modify: `features/organizer/venues/VenueForm.tsx`, `features/organizer/events/steps/ReviewStep.tsx` (link "View event page" → `/events/{slug}` now resolves), `components/layout/SiteFooter.tsx` (Explore links match new filters)
- Test: `features/organizer/venues/VenueForm.test.tsx`

**Interfaces:**
- `PlacesAutocomplete({ onPlace(place: { name, addressLine, city, state, pincode, lat, lng, googlePlaceId }) })` — loads Places via `@googlemaps/js-api-loader` only when `VITE_GOOGLE_MAPS_KEY` is set, restricted to India (`componentRestrictions: { country: 'in' }`), fields `name, formatted_address, address_components, geometry, place_id`; maps `locality` → city, `administrative_area_level_1` → state, `postal_code` → pincode.
- `VenueForm` shows the search box above the manual fields when a key exists; choosing a place fills the fields (still editable); without a key the form is unchanged.

- [ ] **Step 1: Write failing tests**

```ts
test('choosing a place fills venue fields')          // PlacesAutocomplete mocked to call onPlace
test('venue form works without a maps key')          // env key undefined → no search box, manual fields present
```

- [ ] **Step 2: Run — expect FAIL.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run — expect PASS**; full backend + web suites; lint; build; browser check: home (light/dark/mobile), explore filters, event page, share page HTML via curl, organizer profile.
- [ ] **Step 5: Commit** — `feat(web): Google Places venue autocomplete`

---

## Self-review notes

- Spec coverage: §8.7 search on title/summary/venue/city + trigram (T1), filters & sorts (T1, T6), home feeds incl. near-you fallback (T2, T5), PUBLISHED-and-not-ended rule (T1, T3), share page with OG + redirect (T4, T7), Maps embed + Places autocomplete (T7, T8); §8.1 COMPLETED job (T3); §9 public pages Home/Explore/Event details/Organizer profile (T5–T7).
- Deferred: booking and the live Book button (Plan 4), saved events heart (Plan 9), popularity score updates from bookings/saves (Plans 4, 9), sitemap/SSR SEO beyond share pages (Plan 10), route code-splitting (Plan 10).
