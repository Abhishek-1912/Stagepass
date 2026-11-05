# Stagepass Foundation Implementation Plan (Plan 1 of 11)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A running monorepo with a Spring Boot API that validates Supabase JWTs and owns roles, the full Flyway database schema, and a themed React app with working register/login/forgot/reset flows and role-gated routes.

**Architecture:** Modular-monolith Spring Boot jar (`com.stagepass.<module>`) + Vite React SPA. Supabase Auth issues tokens; the API is an OAuth2 resource server that upserts a local `users` row and loads roles from our DB. All later plans (catalog, discovery, booking, payments, tickets, organizer, check-in, admin, engagement, hardening) build on this one.

**Tech Stack:** Java 17, Spring Boot 4.1.2 (Maven wrapper, jar), Spring Security OAuth2 Resource Server, Spring Data JPA, Flyway, PostgreSQL (Supabase); tests with JUnit 5 + MockMvc + zonky embedded-postgres 2.2.2 (Postgres binaries 17.11.0). Frontend: Vite 8, React 19, TypeScript, Tailwind CSS 4, shadcn/ui, React Router 7, TanStack Query 5, react-hook-form + zod, @supabase/supabase-js 2, Vitest + Testing Library.

**Spec:** `docs/superpowers/specs/2026-11-05-stagepass-design.md`

**Later plans** (written when reached, each from the spec's §15 order): 02-catalog-organizer-onboarding, 03-discovery, 04-booking-payments, 05-tickets-email, 06-organizer-dashboard, 07-checkin, 08-admin, 09-engagement, 10-hardening-deploy. (Spec milestones 1 and 2 are combined here.)

## Global Constraints

- Java **17**; Spring Boot **4.1.2** (GA); **jar** packaging; Maven wrapper; config in `application.properties` (+ `application-dev.properties`, `application-prod.properties`), bound via typed `@ConfigurationProperties` records.
- Base package `com.stagepass`; folders `stagepass-api`, `stagepass-web`; monorepo root is the working directory.
- Each backend module: `api` (controllers + DTO records), `service`, `domain` (entities), `repository`. Cross-module access via services only.
- Money as paise `bigint`; timestamps `timestamptz` UTC; PKs `uuid` default `gen_random_uuid()`; status columns `text` + `CHECK`.
- Roles never come from the JWT; only from `user_roles`. Values: `ATTENDEE`, `ORGANIZER`, `ADMIN`.
- Errors are RFC 9457 Problem Details with extra properties `code` and (for validation) `errors[]` of `{field, message}`.
- API base path `/api/v1`. Pagination `{ items, page, size, totalItems, totalPages }`.
- Palette: primary `#6C4CF1`, secondary `#377DFF`, background `#F8F9FC`, foreground `#171923`, accent `#FF6B6B`; dark background `#0B0C10`, dark surface `#13141A`. Fonts: Inter (UI), Bricolage Grotesque (headings).
- Secrets only via env vars; commit `.env.example` / `application-dev.properties` with placeholders only.
- No Docker on the dev machine: tests must not require Testcontainers.

## Review Focus

1. **Bad tokens** (expired, wrong issuer, wrong audience, bad signature) → `401` Problem Details, never `500` — tested in Task 4.
2. **Suspended user** calling any authenticated endpoint → `403` with `code: ACCOUNT_SUSPENDED` — tested in Task 4.
3. **New user's first page load fires several API calls at once** → all succeed, exactly one `users` row (insert is `ON CONFLICT DO NOTHING`) — tested in Task 4.
4. **Supabase user deleted and re-created with the same email** (new id, existing email) → `409` with `code: ACCOUNT_CONFLICT`, not a `500` — tested in Task 4.
5. **Access token expires mid-session** → SPA API client refreshes the Supabase session once and retries; if refresh fails it signs out and routes to `/login?next=…` — tested in Task 6.

---

### Task 1: Monorepo and backend skeleton

**Files:**
- Create: `.gitignore`, `.editorconfig`, `README.md` (root)
- Create: `stagepass-api/` from Spring Initializr (pom, mvnw, `StagepassApiApplication.java`)
- Create: `stagepass-api/src/main/resources/application.properties`, `application-dev.properties`, `application-prod.properties`
- Create: `stagepass-api/src/main/java/com/stagepass/common/config/StagepassProperties.java`, `SupabaseProperties.java`
- Create: `stagepass-api/src/test/java/com/stagepass/support/PostgresIntegrationTest.java`
- Test: `stagepass-api/src/test/java/com/stagepass/StagepassApiApplicationTests.java`

**Interfaces:**
- Produces: `abstract class PostgresIntegrationTest` — `@SpringBootTest(webEnvironment = MOCK) @AutoConfigureMockMvc @ActiveProfiles("test")`, starts one shared `EmbeddedPostgres` (static, started once per JVM) and registers `spring.datasource.url/username/password` via `@DynamicPropertySource`; exposes `protected MockMvc mvc`.
- Produces: `record StagepassProperties(String webBaseUrl, Cors cors, Bootstrap bootstrap, int holdMinutes, String qrSecret, String payoutEncryptionKey, Payout payout)` prefix `stagepass`, nested `record Cors(List<String> allowedOrigins)`, `record Bootstrap(List<String> adminEmails)`, `record Payout(int holdDays)`. Defaults: `holdMinutes=10`, `payout.holdDays=3`.
- Produces: `record SupabaseProperties(String url, String jwksUri, String issuer, String audience)` prefix `supabase`; `audience` default `authenticated`.

- [ ] **Step 1: Init git and root files**

```bash
git init -b main
```
`.gitignore` covers `target/`, `node_modules/`, `dist/`, `.env`, `.env.local`, `*.log`, `.idea/`, `.vscode/`, `stagepass-api/src/main/resources/application-local.properties`.

- [ ] **Step 2: Generate backend from Initializr**

```bash
curl -s https://start.spring.io/starter.zip -d type=maven-project -d bootVersion=4.1.2.RELEASE -d javaVersion=17 -d packaging=jar -d groupId=com.stagepass -d artifactId=stagepass-api -d name=stagepass-api -d packageName=com.stagepass -d dependencies=web,security,oauth2-resource-server,data-jpa,validation,postgresql,flyway,actuator,configuration-processor,lombok -o api.zip && mkdir stagepass-api && unzip -q api.zip -d stagepass-api && rm api.zip
```
Add test deps to pom: `io.zonky.test:embedded-postgres:2.2.2` and import BOM `io.zonky.test.postgres:embedded-postgres-binaries-bom:17.11.0` in `dependencyManagement` (scope `test`). Rename main class to `StagepassApiApplication` with `@ConfigurationPropertiesScan`.

- [ ] **Step 3: Write properties files**

`application.properties`: `spring.application.name=stagepass-api`, `spring.profiles.default=dev`, `server.servlet.context-path` unset, datasource from `${DB_URL}`, `${DB_USERNAME}`, `${DB_PASSWORD}`, `spring.datasource.hikari.maximum-pool-size=10`, `spring.jpa.open-in-view=false`, `spring.jpa.hibernate.ddl-auto=validate`, `spring.flyway.enabled=true`, `management.endpoints.web.exposure.include=health`, `stagepass.*` and `supabase.*` bound to `${...}` env vars (`SUPABASE_URL`, `SUPABASE_JWKS_URI`, `SUPABASE_ISSUER`, `WEB_BASE_URL`, `CORS_ALLOWED_ORIGINS`, `BOOTSTRAP_ADMIN_EMAILS`, `QR_SECRET`, `PAYOUT_ENCRYPTION_KEY`).
`application-dev.properties`: `CORS` default `http://localhost:5173`, SQL logging off, `spring.config.import=optional:application-local.properties` (gitignored, for real dev secrets).
`application-prod.properties`: JSON structured logging (`logging.structured.format.console=ecs`).
`src/test/resources/application-test.properties`: dummy supabase issuer `https://test.supabase.co/auth/v1`, dummy secrets.

- [ ] **Step 4: Write the failing context test**

```java
class StagepassApiApplicationTests extends PostgresIntegrationTest {
  @Test void healthIsUp() throws Exception {
    mvc.perform(get("/actuator/health")).andExpect(status().isOk())
       .andExpect(jsonPath("$.status").value("UP"));
  }
}
```

- [ ] **Step 5: Run — expect FAIL** (no `PostgresIntegrationTest` / security blocks health)

Run: `cd stagepass-api && ./mvnw -q test -Dtest=StagepassApiApplicationTests`

- [ ] **Step 6: Implement `PostgresIntegrationTest`, properties records, and a minimal `SecurityConfig` permitting `/actuator/health`** (full security comes in Task 4). Add placeholder `V1__init.sql` only if Flyway refuses an empty location — prefer `spring.flyway.fail-on-missing-locations=false`.

- [ ] **Step 7: Run — expect PASS**

Run: `cd stagepass-api && ./mvnw -q test`
Expected: `BUILD SUCCESS`.

- [ ] **Step 8: Commit**

```bash
git add . && git commit -m "chore: scaffold monorepo and Spring Boot 4.1.2 API"
```

---

### Task 2: API error contract

**Files:**
- Create: `stagepass-api/src/main/java/com/stagepass/common/error/ErrorCode.java`, `ApiException.java`, `GlobalExceptionHandler.java`
- Test: `stagepass-api/src/test/java/com/stagepass/common/error/GlobalExceptionHandlerTest.java` (uses a test-only `@RestController` under `src/test`)

**Interfaces:**
- Produces: `enum ErrorCode { VALIDATION_FAILED(400), UNAUTHENTICATED(401), FORBIDDEN(403), ACCOUNT_SUSPENDED(403), NOT_FOUND(404), CONFLICT(409), ACCOUNT_CONFLICT(409), SOLD_OUT(409), RATE_LIMITED(429), INTERNAL(500) }` with `HttpStatus status()`.
- Produces: `class ApiException extends RuntimeException` — `ApiException(ErrorCode code, String detail)`; static helpers `notFound(String what)`, `conflict(String detail)`, `forbidden(String detail)`.
- Produces: `@RestControllerAdvice GlobalExceptionHandler extends ResponseEntityExceptionHandler` producing `ProblemDetail` with properties `code` and, for `MethodArgumentNotValidException`, `errors: [{field, message}]`. Unknown exceptions → `INTERNAL`, detail `"Unexpected error"`, logged with stack trace, never returned.
- Produces: `class ProblemAuthEntryPoint implements AuthenticationEntryPoint` and `ProblemAccessDeniedHandler implements AccessDeniedHandler` writing the same JSON shape (`UNAUTHENTICATED` / `FORBIDDEN`) — used by Task 4.

- [ ] **Step 1: Write failing tests**

```java
@Test void apiExceptionBecomesProblem() {   // GET /test/missing throws ApiException.notFound("Event")
  mvc.perform(get("/test/missing")).andExpect(status().isNotFound())
     .andExpect(jsonPath("$.code").value("NOT_FOUND"))
     .andExpect(jsonPath("$.detail").value("Event not found"));
}
@Test void validationListsFields() {        // POST /test/validated {"name":""} with @NotBlank name
  ... .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
      .andExpect(jsonPath("$.errors[0].field").value("name"));
}
@Test void unexpectedErrorHidesDetails() {  // GET /test/boom throws IllegalStateException("secret")
  ... .andExpect(status().isInternalServerError())
      .andExpect(jsonPath("$.detail").value("Unexpected error"));
}
```
Test controller is permitted in the test-profile security (`/test/**` permitAll in a test `@TestConfiguration`).

- [ ] **Step 2: Run — expect FAIL.** `./mvnw -q test -Dtest=GlobalExceptionHandlerTest`
- [ ] **Step 3: Implement the four classes.**
- [ ] **Step 4: Run — expect PASS.**
- [ ] **Step 5: Commit** — `feat(api): problem-details error contract`

---

### Task 3: Database schema (Flyway)

Implements spec §7 plus §8.4a additions. Entities for non-identity tables are **not** created here; each later plan adds its module's entities against this schema.

**Files:**
- Create: `stagepass-api/src/main/resources/db/migration/V1__extensions_identity.sql`, `V2__catalog.sql`, `V3__booking_payments.sql`, `V4__tickets_checkin.sql`, `V5__engagement_notifications.sql`, `V6__finance_admin.sql`, `V7__seed_reference_data.sql`
- Test: `stagepass-api/src/test/java/com/stagepass/schema/SchemaConstraintsTest.java`

**Interfaces:**
- Produces: tables and columns exactly as spec §7, with these decisions:
  - `users.email` **nullable** unique (future phone-OTP users); `users.status` CHECK in (`ACTIVE`,`SUSPENDED`).
  - `organizers` adds `pan` text not null, `gstin` text null, `state` text.
  - `events` adds `refund_policy` CHECK in (`NONE`,`UNTIL_HOURS_BEFORE`) default `NONE`, `refund_cutoff_hours int`, `reschedule_refund_until timestamptz` null; `search_vector` is a `GENERATED ALWAYS AS (setweight(to_tsvector('simple', coalesce(title,'')),'A') || setweight(to_tsvector('simple', coalesce(summary,'')),'B')) STORED` column with GIN index; trigram GIN index on `title`.
  - `payments` adds `gateway_fee_paise bigint`, `gateway_tax_paise bigint`.
  - `bookings` adds `buyer_state text`.
  - `ticket_inventory` CHECK `(total >= 0 AND sold >= 0 AND held >= 0 AND sold + held <= total)`.
  - `check_ins.ticket_id` UNIQUE.
  - `invoices(id, booking_id unique, number text unique, financial_year text, seq int, taxable_paise, cgst_paise, sgst_paise, igst_paise, total_paise, issued_at)` and `invoice_sequences(financial_year text PK, last_seq int)`.
  - `organizer_ledger_entries.type` CHECK in (`SALE`,`REFUND`,`COMMISSION`,`PAYOUT`,`ADJUSTMENT`,`TDS`).
  - Every FK indexed; `updated_at` maintained by a shared `set_updated_at()` trigger function.
- Seeds (V7): roles `ATTENDEE`,`ORGANIZER`,`ADMIN`; categories Music, Concerts, Technology, Business, Workshops, Sports, Comedy, Festivals, Food & Lifestyle (slugs kebab-case, sort_order 1–9, lucide icon names); `platform_settings` key `fees` = `{"convenienceFeeBps":250,"convenienceFeeFlatPaise":1000,"gstBps":1800,"commissionBps":0,"refundFeeOnAttendeeRequest":false}`.

- [ ] **Step 1: Write failing tests** (JdbcTemplate against migrated embedded DB, each test in a rolled-back transaction)

```java
@Test void inventoryCannotOversell()      // insert inventory total=2,sold=2; UPDATE held=1 → DataIntegrityViolationException
@Test void checkInIsUniquePerTicket()     // second insert into check_ins for same ticket → DuplicateKeyException
@Test void seedsRolesAndCategories()      // count(roles)=3; count(event_categories)=9; fees setting convenienceFeeBps=250
@Test void searchVectorIsGenerated()      // insert event title 'Sunburn Goa' → select where search_vector @@ plainto_tsquery('simple','sunburn') returns 1
@Test void moneyColumnsAreBigint()        // information_schema: bookings.total_paise data_type = 'bigint'
```
Provide a test helper `SchemaFixtures` that inserts the minimal parent rows (user → organizer → category → event → ticket_type → booking → booking_item → ticket).

- [ ] **Step 2: Run — expect FAIL.** `./mvnw -q test -Dtest=SchemaConstraintsTest`
- [ ] **Step 3: Write migrations V1–V7.**
- [ ] **Step 4: Run — expect PASS**, then full suite `./mvnw -q test`.
- [ ] **Step 5: Commit** — `feat(db): initial Stagepass schema and reference data`

---

### Task 4: Supabase JWT auth, users, roles, `/me`

**Files:**
- Create in `com.stagepass.identity`: `domain/User.java`, `domain/Role.java`, `domain/UserStatus.java`, `domain/RoleName.java`, `repository/UserRepository.java`, `repository/RoleRepository.java`, `service/UserProvisioningService.java`, `service/RoleLookupService.java`, `service/CurrentUser.java`, `api/MeController.java`, `api/MeResponse.java`, `api/UpdateProfileRequest.java`
- Create in `com.stagepass.common`: `security/SecurityConfig.java`, `security/StagepassAuthenticationConverter.java`, `security/StagepassPrincipal.java`, `security/SuspendedUserFilter.java`, `audit/AuditLogService.java`, `audit/AuditLog.java`, `audit/AuditLogRepository.java`
- Modify: `stagepass-api/pom.xml` (add `spring-boot-starter-cache`, `com.github.ben-manes.caffeine:caffeine`)
- Test: `stagepass-api/src/test/java/com/stagepass/support/TestJwt.java`, `support/TestSecurityConfig.java`, `identity/MeControllerTest.java`, `identity/UserProvisioningServiceTest.java`

**Interfaces:**
- Consumes: `ErrorCode`, `ApiException`, `ProblemAuthEntryPoint`, `ProblemAccessDeniedHandler` (Task 2); `SupabaseProperties`, `StagepassProperties` (Task 1).
- Produces: `record StagepassPrincipal(UUID userId, String email, Set<RoleName> roles)`; `Authentication#getPrincipal()` returns it for every authenticated request.
- Produces: `@Component CurrentUser` — `StagepassPrincipal get()` (throws `UNAUTHENTICATED`), `UUID id()`, `boolean hasRole(RoleName)`.
- Produces: `UserProvisioningService#provision(UUID id, String email, String fullName, String avatarUrl): User` — native `INSERT … ON CONFLICT (id) DO NOTHING`, then load; unique violation on email → `ApiException(ACCOUNT_CONFLICT, …)`. New users get `ATTENDEE`; emails in `stagepass.bootstrap.admin-emails` (case-insensitive) also get `ADMIN`, audit-logged as `ROLE_GRANTED` with actor null.
- Produces: `RoleLookupService#rolesFor(UUID userId): Set<RoleName>` — cached 60 s (Caffeine via `spring-boot-starter-cache` + `com.github.ben-manes.caffeine:caffeine`), `evict(UUID)` for later role changes.
- Produces: `AuditLogService#record(UUID actorId, String action, String entityType, String entityId, Object before, Object after)` — `before`/`after` serialized to jsonb; IP taken from current request if present.
- Produces: `SecurityConfig` — stateless, CSRF off, CORS from `stagepass.cors.allowed-origins`, permits `GET /actuator/health`, `GET /api/v1/public/**`, `POST /api/v1/webhooks/**`, `GET /e/**`; everything else authenticated. Method security enabled. `JwtDecoder` = `NimbusJwtDecoder.withJwkSetUri(jwksUri)` with validator from `static OAuth2TokenValidator<Jwt> supabaseValidator(SupabaseProperties)` (issuer + audience contains `authenticated` + timestamps).
- Produces: `StagepassAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken>` — calls `provision(sub, email, user_metadata.full_name, user_metadata.avatar_url)` then `rolesFor`; authorities `ROLE_<name>`.
- Produces: `SuspendedUserFilter` — after authentication, if user status `SUSPENDED` → 403 `ACCOUNT_SUSPENDED`.
- Produces: `GET /api/v1/me` → `MeResponse(UUID id, String email, String fullName, String phone, String avatarUrl, String city, Set<String> roles)`; `PATCH /api/v1/me` with `UpdateProfileRequest(@Size(max=120) String fullName, @Pattern(regexp="^[6-9]\\d{9}$") String phone, @Size(max=80) String city, @URL String avatarUrl)` (null fields untouched).
- Test support: `TestJwt` — static RSA key pair; `String token(UUID sub, String email)` and builder for `issuer`, `audience`, `expiresAt`, `signedWithOtherKey()`. `TestSecurityConfig` (`@TestConfiguration`, imported by `PostgresIntegrationTest`) supplies `@Primary JwtDecoder` built from `TestJwt` public key + `SecurityConfig.supabaseValidator(props)`.

- [ ] **Step 1: Write failing tests** in `MeControllerTest`

```java
@Test void meProvisionsAttendee()          // new sub → 200, roles == ["ATTENDEE"], email echoed
@Test void bootstrapEmailGetsAdmin()       // email in test props bootstrap list → roles contain ADMIN; audit_logs has ROLE_GRANTED
@Test void noTokenIs401Problem()           // → 401, $.code UNAUTHENTICATED
@Test void expiredTokenIs401()             // expiresAt in past → 401
@Test void wrongIssuerIs401()
@Test void wrongAudienceIs401()
@Test void foreignSignatureIs401()
@Test void suspendedUserIs403()            // set users.status=SUSPENDED → GET /me → 403, $.code ACCOUNT_SUSPENDED
@Test void sameEmailNewIdIs409()           // provision id A with x@y.com, then token id B same email → 409 ACCOUNT_CONFLICT
@Test void patchMeUpdatesProfile()         // PATCH {fullName:"Asha", city:"Bengaluru"} → 200 with values; phone untouched
@Test void patchMeRejectsBadPhone()        // phone "12345" → 400 VALIDATION_FAILED, errors[0].field "phone"
@Test void rolesComeFromDbNotToken()       // token with claim "role":"ADMIN" (non-bootstrap email) → roles == ["ATTENDEE"]
```
In `UserProvisioningServiceTest`:
```java
@Test void concurrentFirstRequestsCreateOneUser()  // 8 threads call provision(sameId,...) via CountDownLatch → no exception; count(users)=1; count(user_roles for id)=1
```

- [ ] **Step 2: Run — expect FAIL.** `./mvnw -q test -Dtest='MeControllerTest,UserProvisioningServiceTest'`
- [ ] **Step 3: Implement entities and repositories** (`User` maps `users`; roles via `@ManyToMany` join table `user_roles`).
- [ ] **Step 4: Implement services, converter, filter, security config, controller.** Grant `ATTENDEE` with `INSERT … ON CONFLICT DO NOTHING` too, so the concurrent test cannot double-insert.
- [ ] **Step 5: Run — expect PASS**, then full suite.
- [ ] **Step 6: Commit** — `feat(identity): Supabase JWT resource server with DB-owned roles`

---

### Task 5: Web app skeleton and design system

**Files:**
- Create: `stagepass-web/` (Vite React TS template), `vite.config.ts` (Tailwind plugin, `@` alias → `src`, Vitest config with jsdom), `src/index.css` (Tailwind import + theme tokens), `components.json` (shadcn)
- Create: `src/app/providers.tsx`, `src/app/router.tsx`, `src/app/theme/ThemeProvider.tsx`, `src/app/theme/ThemeToggle.tsx`
- Create: `src/components/layout/PublicLayout.tsx`, `SiteHeader.tsx`, `SiteFooter.tsx`, `MobileTabBar.tsx`, `DashboardLayout.tsx` (collapsible sidebar shell for organizer/admin)
- Create: `src/features/home/HomePage.tsx` (hero with search input + gradient, placeholder rails), `src/features/misc/NotFoundPage.tsx`
- Create: `src/test/setup.ts`
- Test: `src/app/theme/ThemeToggle.test.tsx`

**Interfaces:**
- Produces: `useTheme(): { theme: 'light'|'dark'|'system'; resolved: 'light'|'dark'; setTheme(t) }` — persists to `localStorage['stagepass-theme']`, toggles `dark` class on `<html>`.
- Produces: `<DashboardLayout nav={NavItem[]} title={string}>` with `type NavItem = { to: string; label: string; icon: LucideIcon }`.
- Produces: CSS variables (shadcn names) in `:root` and `.dark`: `--background`, `--foreground`, `--card`, `--primary`, `--primary-foreground`, `--secondary`, `--accent`, `--muted`, `--border`, `--ring`, `--radius: 0.875rem`, plus `--gradient-brand: linear-gradient(135deg,#6C4CF1 0%,#377DFF 100%)`. Utility classes `font-display` (Bricolage Grotesque) and default `font-sans` (Inter).

- [ ] **Step 1: Scaffold**

```bash
npm create vite@latest stagepass-web -- --template react-ts && cd stagepass-web && npm i && npm i tailwindcss @tailwindcss/vite react-router @tanstack/react-query @supabase/supabase-js react-hook-form zod @hookform/resolvers motion lucide-react @fontsource-variable/inter @fontsource-variable/bricolage-grotesque && npm i -D vitest jsdom @testing-library/react @testing-library/user-event @testing-library/jest-dom @types/node
```
Then `npx shadcn@latest init` (style new-york, CSS variables, base color neutral) and add: `button input label card badge dropdown-menu sheet separator skeleton sonner form avatar tabs dialog`.

- [ ] **Step 2: Write failing test**

```tsx
test('toggle switches to dark and persists', async () => {
  render(<ThemeProvider><ThemeToggle /></ThemeProvider>);
  await userEvent.click(screen.getByRole('button', { name: /toggle theme/i }));
  expect(document.documentElement).toHaveClass('dark');
  expect(localStorage.getItem('stagepass-theme')).toBe('dark');
});
```

- [ ] **Step 3: Run — expect FAIL.** `cd stagepass-web && npx vitest run src/app/theme`
- [ ] **Step 4: Implement theme, tokens, layouts, Home hero, router** (`/` Home inside `PublicLayout`, `*` NotFound). Header: logo wordmark "Stagepass", Explore link, theme toggle, Sign in button; MobileTabBar (Home, Explore, Tickets, Profile) visible `< md`.
- [ ] **Step 5: Run — expect PASS**, plus `npm run build` and `npm run lint` clean.
- [ ] **Step 6: Visual check** with the browser pane at `http://localhost:5173` in light/dark and mobile (375×812).
- [ ] **Step 7: Commit** — `feat(web): Vite app, Stagepass design system, layouts`

---

### Task 6: Web auth flows, API client, route guards

**Files:**
- Create: `src/lib/supabase.ts`, `src/lib/env.ts`, `src/lib/api/client.ts`, `src/lib/api/ApiError.ts`
- Create: `src/features/auth/AuthProvider.tsx`, `useMe.ts`, `RequireAuth.tsx`, `RequireRole.tsx`, `LoginPage.tsx`, `RegisterPage.tsx`, `ForgotPasswordPage.tsx`, `ResetPasswordPage.tsx`, `AuthCard.tsx`, `schemas.ts`
- Create: `src/features/account/AccountPlaceholderPage.tsx` (protected stub at `/account` showing `useMe()` data) and `.env.example`
- Modify: `src/app/router.tsx`, `src/app/providers.tsx`, `src/components/layout/SiteHeader.tsx` (avatar menu when signed in)
- Test: `src/lib/api/client.test.ts`, `src/features/auth/RequireRole.test.tsx`, `src/features/auth/schemas.test.ts`

**Interfaces:**
- Consumes: `GET/PATCH /api/v1/me` → `MeResponse` (Task 4); Problem Details shape (Task 2).
- Produces: `env` — typed reads of `VITE_API_BASE_URL`, `VITE_SUPABASE_URL`, `VITE_SUPABASE_ANON_KEY` (throws on missing at startup).
- Produces: `class ApiError extends Error { status: number; code: string; detail: string; fieldErrors: {field: string; message: string}[] }`.
- Produces: `api<T>(path: string, init?: RequestInit & { json?: unknown }): Promise<T>` — prefixes base URL, attaches `Authorization: Bearer <session.access_token>` when signed in, sets JSON headers, parses Problem Details into `ApiError`; on `401` calls `supabase.auth.refreshSession()` once and retries; if still `401`, `supabase.auth.signOut()` and `window.location.assign('/login?next=' + encodeURIComponent(currentPath))`.
- Produces: `useAuth(): { session: Session|null; loading: boolean; signOut(): Promise<void> }`; `useMe(): UseQueryResult<Me>` (query key `['me']`, enabled only with session; cleared on sign-out).
- Produces: `<RequireAuth>` (redirects to `/login?next=`), `<RequireRole roles={('ATTENDEE'|'ORGANIZER'|'ADMIN')[]}>` (renders 403 page if `me.roles` lacks all of them).
- Produces: zod schemas `loginSchema` (email, password min 8), `registerSchema` (fullName 2–120, email, password min 8 with ≥1 letter and ≥1 digit, confirm matches), `forgotSchema`, `resetSchema`.
- Routes: `/login`, `/register`, `/forgot-password`, `/reset-password` (Supabase `PASSWORD_RECOVERY` event), `/account` (RequireAuth). Register passes `options.data.full_name` and `emailRedirectTo: ${origin}/login`; forgot uses `redirectTo: ${origin}/reset-password`. Google button calls `signInWithOAuth({ provider: 'google' })`.

- [ ] **Step 1: Write failing tests**

```ts
// client.test.ts (fetch and supabase mocked)
test('parses problem details into ApiError')      // 409 {code:'ACCOUNT_CONFLICT',detail:'…'} → rejects ApiError status 409 code ACCOUNT_CONFLICT
test('attaches bearer token')                     // session token 'abc' → Authorization 'Bearer abc'
test('refreshes once on 401 then retries')        // 401 then 200 → refreshSession called 1×, resolves body
test('signs out and redirects when refresh fails')// 401, refresh error → signOut called, location.assign('/login?next=%2Faccount')
// RequireRole.test.tsx
test('renders children for matching role')       // me.roles ['ORGANIZER'], roles ['ORGANIZER'] → child visible
test('shows forbidden for missing role')          // me.roles ['ATTENDEE'] → text /don’t have access/i
// schemas.test.ts
test('register rejects mismatched confirm')       // error path ['confirmPassword']
test('register rejects password without digit')
```

- [ ] **Step 2: Run — expect FAIL.** `npx vitest run`
- [ ] **Step 3: Implement lib + auth feature + pages** (split-screen AuthCard: form left, event photo + gradient right on `md+`, form only on mobile; inline field errors; toast on success; "Check your email" state after register/forgot).
- [ ] **Step 4: Run — expect PASS**; `npm run build`, `npm run lint`.
- [ ] **Step 5: Manual end-to-end check (needs user's Supabase project):** set `.env.local` + `application-local.properties`, run API (`./mvnw spring-boot:run`) and web (`npm run dev`), register → verify email → login → `/account` shows roles `ATTENDEE`. If Supabase credentials are not yet available, record this step as pending and say so.
- [ ] **Step 6: Commit** — `feat(web): Supabase auth flows, API client, role guards`

---

### Task 7: CI, Render blueprint, developer docs

**Files:**
- Create: `.github/workflows/ci.yml`, `render.yaml`, `stagepass-api/Dockerfile`, `stagepass-api/.dockerignore` (SPA rewrite lives in `render.yaml`)
- Modify: `README.md` (setup, env vars table, run/test commands, Supabase setup checklist: Mumbai region, enable email + Google providers, set Site URL/redirect URLs, custom SMTP via Resend, copy JWKS URL and issuer)

**Interfaces:**
- `ci.yml`: on push/PR to `main`; job `api` (temurin 17, `./mvnw -B verify` in `stagepass-api`), job `web` (Node 24, `npm ci && npm run lint && npx vitest run && npm run build` in `stagepass-web`).
- `Dockerfile`: stage 1 `eclipse-temurin:17-jdk` runs `./mvnw -B -DskipTests package`; stage 2 `eclipse-temurin:17-jre`, non-root user, `java -XX:MaxRAMPercentage=75 -jar app.jar`, `EXPOSE 8080`, `SPRING_PROFILES_ACTIVE=prod`.
- `render.yaml`: region `singapore`; service `stagepass-api` (type web, runtime docker, rootDir `stagepass-api`, healthCheckPath `/actuator/health`, plan `starter`, env vars declared `sync: false`); service `stagepass-web` (type web, runtime static, rootDir `stagepass-web`, buildCommand `npm ci && npm run build`, staticPublishPath `dist`, route rewrite `/*` → `/index.html`, `VITE_*` env vars `sync: false`).

- [ ] **Step 1: Write the three files and README section.**
- [ ] **Step 2: Verify locally:** `cd stagepass-api && ./mvnw -B verify` → `BUILD SUCCESS`; `cd stagepass-web && npm ci && npm run lint && npx vitest run && npm run build` → all succeed. Validate YAML parses: `node -e "require('yaml')"` is not available by default — use `npx --yes yaml-lint render.yaml .github/workflows/ci.yml`.
- [ ] **Step 3: Commit** — `chore: CI, Render blueprint, Dockerfile, setup docs`

---

## Self-review notes

- Spec coverage for this plan's scope: §3 constraints (Tasks 1, 5), §5 structure (1, 5), §5.4 error/pagination contract (2; pagination record arrives with first list endpoint in Plan 02), §6 identity (4, 6), §7 + §8.4a schema (3), §10 design system (5), §11 JWT/validation/errors/audit/CORS (2, 4; rate limiting deferred to Plan 10 per spec §15), §12 config (1, 6), §13 deploy (7), §14 test approach (1, 3, 4, 5, 6).
- Organizer application, role grants by admin, and all business modules are intentionally in later plans.
