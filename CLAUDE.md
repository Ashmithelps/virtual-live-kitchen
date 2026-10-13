# Virtual Live Kitchen — Claude Code Guide

## Product

A multi-restaurant platform where customers order dine-in, takeaway, or delivery. Each restaurant
has cameras at kitchen prep stations. When a chef starts an order, the kitchen device records it,
and the customer later receives a 30–60 s clip of their own order being prepared, which they can
view, share, and download. Customers can tip the chef directly. An AI service scores kitchen hygiene
from the clips (gloves, caps, masks). Restaurants pay a monthly subscription to be listed. Live
streaming is deferred to a later milestone.

---

## Monorepo Layout

```
/
├── backend/        Spring Boot 3, Java 21, Maven — modular monolith, package-by-feature
│   └── src/main/java/com/virtualkitchen/
│       ├── auth/
│       ├── restaurant/
│       ├── menu/
│       ├── order/
│       ├── kitchen/
│       ├── clip/
│       ├── payment/
│       ├── tip/
│       ├── hygiene/
│       ├── billing/
│       └── admin/
├── web/            Next.js App Router, TypeScript, Tailwind
│   └── app/
│       ├── (customer)/     customer-facing ordering site
│       ├── (restaurant)/   restaurant owner dashboard
│       └── (kitchen)/      kitchen screen (tablet UI for chefs)
├── ai-service/     Python FastAPI — hygiene detection (added in a later milestone)
└── infra/          docker-compose.yml (Postgres 16, MinIO as local S3)
```

---

## Coding Conventions

### Money
- Store all monetary values as `long` in **paise** (1 INR = 100 paise). Never use `double` or
  `float` for money.
- Use a dedicated value type / record (e.g. `Money`) or at minimum a clearly named field
  `long amountPaise`.

### Timestamps
- Persist all timestamps as `java.time.Instant` (UTC) in the database (`TIMESTAMPTZ` column).
- Convert to `Asia/Kolkata` only at the presentation layer (API response DTO or frontend).

### Database Migrations (Flyway)
- Every schema change must have a Flyway migration under
  `src/main/resources/db/migration/`.
- Naming: `V<version>__<snake_case_description>.sql` (e.g. `V3__add_clip_status_column.sql`).
- **Never edit an already-applied migration.** Always create a new one.

### API Layer (Spring Boot)
- Controllers must return **DTOs**, never JPA entities.
- All request DTOs must be validated with `jakarta.validation` annotations
  (`@NotNull`, `@Size`, `@Valid`, etc.).
- A single `GlobalExceptionHandler` (`@RestControllerAdvice`) must catch all exceptions and return
  a consistent JSON error shape:
  ```json
  {
    "status": 400,
    "error": "Bad Request",
    "message": "...",
    "timestamp": "2024-01-01T00:00:00Z",
    "path": "/api/orders"
  }
  ```

### Testing
- Integration tests must use **Testcontainers** with a real Postgres container — no in-memory
  databases (H2 is banned for integration tests).
- Unit tests for pure logic are fine without Testcontainers.

### Secrets & Configuration
- Secrets come **only from environment variables** — no hardcoded credentials anywhere.
- Every service that requires env vars must ship a `.env.example` file listing all required
  variables with placeholder values.
- Never commit `.env` files.

---

## Working Rule

> **Build ONE milestone per session. When the milestone is done, stop and print a "Manual checks"
> list with the exact commands and/or URLs needed to verify the work. Do not start the next
> milestone until the user confirms the current one passes.**

Milestones are agreed at the start of each session. If no milestone is stated, ask before writing
any application code.

---

## Project Status (update at the end of every milestone)

| # | Milestone | Status | Migration |
|---|-----------|--------|-----------|
| 1 | Scaffolding: monorepo, docker-compose, health endpoint, error handler | Done | V1 |
| 2 | Auth & roles: JWT + rotating refresh tokens, login rate limit, seeded ADMIN | Done | V2 |
| 3 | Restaurant domain: restaurants, menu, tables, stations, chefs, recording consent, image uploads | Done | V3 |
| 4 | Orders: state machine, daily display numbers, mock payments, chef/station on PREPARING | Done | V4 |
| 5 | Table QR: printable PDF, table sessions, QR-locked dine-in, guest checkout | Done | V5 |
| 6 | Customer web app (Next.js): browse, menu, QR dine-in, checkout, order tracking, login | Done | none |
| 7 | Kitchen display (KDS) PWA + STOMP/SockJS live order updates | Done | none |
| 8 | Real payments: Razorpay checkout + webhooks, payments table, auto refunds, pay-at-counter | Done | V6 |
| 9 | Station recorder: phone pairing, device auth, 60 s chunk ring buffer, footage windows on READY, uploads | Built, awaiting user's manual check | V7 |
| 10 | Clip pipeline: Postgres job queue, FFmpeg render (cut, timelapse, overlay, 720p H.264), clips table, retention, READY push | Built, awaiting user's manual check | V8 |
| 10.5 | Complete frontend for M1–10 (role-aware app, owner dashboard, minimal admin, chef settings, station debug) + dev tooling (dev-seed profile, role switcher, Playwright) | Built, awaiting user's manual check | V9 |
| 11 | Not yet defined (clip player + sharing per the M10.5 spec) | — | next: V10 |

Note: the user's last commit message ("completed till milestone 8 and tests pending for 6,7,8") says the manual
checks for milestones 6–8 are still pending; 9, 10 and 10.5 have not been checked either. Milestone 10's checks are in
the README "Kitchen clips" section; 10.5's (dev-seed stack, role-by-role browser walkthrough, `npm run test:e2e`) are in
the README "Trying the whole product" section. Next session: ask whether those checks passed before defining Milestone 11.
Milestones announced so far: 11 = clip player and sharing on the order page, 14 = full admin panel.

`ai-service/` contains only `.env.example`. The owner dashboard lives at `web/app/(app)/dashboard` (the empty
`(restaurant)` group from the original layout was never used).

### Backend map (`com.virtualkitchen`)

- `config/SecurityConfig`: stateless JWT filter chain, CORS, `@EnableMethodSecurity`, public routes
  (`/api/auth/*`, `GET /api/restaurants/**`, `GET /api/tables/*`, `/api/guest/**`, `/api/health`).
- `common/`: `error/` (`ApiException`, `GlobalExceptionHandler`, `ErrorResponseWriter`), `crypto/SecureTokens`
  (random tokens, SHA-256), `storage/ObjectStorage` (presigned S3 PUT/GET), `geo/Geo` (haversine),
  `web/PageResponse`, `config/` (`Clock` bean, `WebProperties`, scheduling).
- `auth/`: `AuthService` (register / login / refresh / logout), `credential/CredentialVerifier` (OTP plugs in as
  a new verifier), `token/` (`JwtService`, `RefreshTokenService`, `ScopedTokens`), `user/` (`User`, `UserAccountService`,
  `AdminSeeder`), `ratelimit/LoginRateLimiter`, `security/` (JWT filter, 401/403 JSON handlers).
- `restaurant/`: `Restaurant` (opening hours as `OpeningSlot`, cuisine tags as `text[]`), `RestaurantAccess.requireOwned`,
  `RestaurantSearchService` (public listing in plain SQL), `table/` (tables, `TableSessions`, `TableQrSheetRenderer`),
  `media/` (upload purposes incl. `RESTAURANT_LOGO`, object-key validation). `Restaurant.logoKey` (optional,
  set via `RestaurantRequest.logoKey`) is burned into clips.
- `menu/`: categories and items, `MenuService.customerMenu`.
- `kitchen/`: stations, `chef/`, `consent/ChefConsentService`, and `KitchenStaffing`, the API other modules use.
- `order/`: `OrderStateMachine`, `DisplayNumberAllocator`, `OrderService` (customer), `RestaurantOrderService`
  (owner/chef), `OrderPayments`, `guest/` (guest checkout).
- `payment/`: the `PaymentProvider` interface, `Payment` (one row per attempt in `payments`) and
  `PaymentRepository`, plus two providers: `MockPaymentProvider` (`PAYMENT_PROVIDER=mock`) and
  `razorpay/RazorpayPaymentProvider` (`=razorpay`; Orders, Payments, Capture and Refunds APIs via `RazorpayClient`).
  `razorpay/RazorpayWebhookService` verifies `X-Razorpay-Signature` over the raw body and dedupes events
  through `payment_webhook_events` in the same transaction.
- `order/OrderPaymentService`: checkout start/confirm, idempotent `applyCaptured/applyFailed/applyRefundProcessed`,
  refunds sent after commit plus a 2-minute retry job that reconciles with the gateway first, counter
  settlement, and the unpaid-bills list.
- `admin/`: approve owners (`/api/admin/owners`), approve or suspend restaurants (`/api/admin/restaurants`),
  read-only paged user list (`GET /api/admin/users?role=&page=&size=`, `AdminUserListController`).
- `devseed/DevSeeder`: profile `dev-seed` only (refuses to start with `prod`/`production`/`staging`; needs
  `DEV_SEED_PASSWORD`). Idempotently creates admin `+919000000001`, owner `…02` with the approved restaurant
  "Dev Spice Kitchen" (`/r/dev-spice-kitchen`, 2 categories, 8 items, tables T1–T3, stations Tandoor/Curry),
  chefs `…03` (Ravi, Tandoor) and `…04` (Meera, Curry) with consent, customer `…05`; logs every login + table QR link.
  The phones are mirrored in `web/lib/dev/accounts.ts`.
- `realtime/`: STOMP over SockJS at `/ws` with an in-memory broker. `StompAuthInterceptor` authenticates CONNECT
  (bearer JWT, a station phone's `X-Device-Token`, or a guest's `X-Guest-Order-Token`), checks every SUBSCRIBE
  with `RealtimeAccess` (`/topic/station/{id}` is for that station's phone only), and refuses all
  client SENDs. `OrderEventBroadcaster` publishes `{type: "ORDER_UPDATED", order}` after commit to
  `/topic/order/{id}` (that order's customer) and `/topic/restaurant/{id}/orders` (owner + chefs; only orders the
  restaurant can see).
- `kitchen_stations.device_last_seen_at` (V9) is bumped (at most every 15 s) whenever a station phone authenticates;
  `StationResponse.deviceLastSeenAt` drives the owner's online dot (phone heartbeats `GET /api/station-device/me`
  every 30 s; online = seen within 90 s, `lib/owner/status.ts`).
- `kitchen/device/`: station phones. `StationDeviceService` pairs a phone (a one-time code from the owner's QR,
  10 min, is exchanged for a device token; only its SHA-256 is stored) and authenticates it.
  `StationDeviceAuthenticationFilter` honours `X-Device-Token` **only** on `/api/station-device/**` and `/api/clips/**`
  (principal `StationDevice`, `ROLE_STATION_DEVICE`).
- `clip/`: `ClipService` handles `OrderReadyEvent` inside the READY transaction. With valid consent and a paired
  phone it creates a `clip_requests` row (window = PREPARING − 10 s … READY + 5 s) and pushes `CLIP_WINDOW` to
  `/topic/station/{id}` after commit. Otherwise the order's `clipStatus` is NOT_AVAILABLE with an internal reason.
  Device endpoints: `POST /api/clips/{orderId}/upload-urls` (presigned PUTs into the clips bucket, pinned to one
  key under `raw/{restaurantId}/{orderId}/` and to the exact size) and `/chunks-uploaded` (each key checked to
  exist). There is also a catch-up list and a 2 h expiry job. The first `chunks-uploaded` enqueues a
  `CLIP_PROCESS` job (dedupe key `clip:{orderId}`); READY creates the `clips` row (PENDING, or NOT_AVAILABLE).
- `common/jobs/`: generic Postgres job queue (`jobs` table). `JobQueue.enqueue` joins the caller's transaction and
  dedupes through a partial unique index on QUEUED/RUNNING jobs; `claim` uses `FOR UPDATE SKIP LOCKED`; failures
  back off as `retry-base-delay × 2^(attempt−1)` until `max-attempts`, then DEAD and `JobHandler.onExhausted`.
  `PermanentJobFailure` skips retries. `JobWorker` runs `app.jobs.worker-threads` threads (`JOB_WORKER_THREADS`,
  0 = this instance only enqueues), and a lease reaper requeues jobs whose worker died. New job types implement `JobHandler`.
- `clip/render/`: `FfmpegRunner` (ProcessBuilder, per-run log file, timeout → `destroyForcibly`, `probeDuration`),
  `ClipOverlayRenderer` (Java2D 1280×720 PNG: logo, restaurant name, "Order #N · Chef X · date", watermark top-right;
  used because Homebrew/Debian ffmpeg may lack `drawtext`), `ClipRenderer` (pass 1: concat demuxer + trim to the
  window, 720p 30 fps intermediate; pass 2: if > 50 s, timelapse the first part to 40 s + last 5 s at normal speed
  = 45 s; overlay; libx264 yuv420p `+faststart`; JPG thumbnail at 40%).
- `clip/ClipProcessingJob` downloads chunks + logo, renders in a temp dir, uploads `clips/{restaurantId}/{orderId}.mp4`
  and `.jpg` (clips bucket), marks READY via `ClipLifecycle` (short REQUIRES_NEW transactions; READY pushes
  `ORDER_UPDATED` to `/topic/order/{id}` with `order.clip` links), then deletes the raw chunks. No footage in the
  window → NOT_AVAILABLE; retries exhausted → FAILED. `ClipLinks` (implements `order.OrderClipLinks`) adds
  presigned `clip.videoUrl/thumbnailUrl` to `OrderResponse`. `ClipRetentionJob` (hourly) deletes raw chunks older
  than `CLIP_RAW_RETENTION` (24h) and expires READY clips past `expires_at` (`CLIP_RETENTION`, default 30d).
- `clip/OwnerClipService` + `OwnerClipController`: `GET /api/owner/restaurants/{id}/clips?status=&page=&size=` (SQL join
  of clips/orders/chefs/stations, presigned links for READY) and `POST …/clips/{orderId}/retry` (FAILED only and only
  while raw footage still exists; clip → PENDING, order clipStatus → UPLOADED, re-enqueues `CLIP_PROCESS`).
- `common/time/TimeController`: public `GET /api/time` for device clock sync.
- `common/openapi/OpenApiConfig`: springdoc spec at `/v3/api-docs` (OpenAPI 3.0) and Swagger UI at `/swagger-ui.html`.
  Every field of a response-only schema is marked required; nullable fields carry `@Schema(nullable = true)`.

### Web map (`web/`, Next.js 16 App Router, TypeScript, Tailwind 4, TanStack Query 5)

- Route groups: `(customer)` (narrow, `AppHeader`), `(app)` (wide, `AppHeader wide`: `/dashboard/**`, `/admin/**`,
  `/chef/settings`), `(kitchen)` (`/kitchen`, dark full-screen) and `(station)` (`/station`).
- Customer pages: `/` (city + filters), `/r/[slug]` (menu, veg filter, cart bar), `/t/[qrToken]` (saves the table
  session, then opens `/r/[slug]` in dine-in mode), `/checkout?r=<slug>` (client-side delivery-radius check via
  `lib/geo.ts`; staff accounts are told to log out), `/orders/[id]` (live over STOMP with 5 s polling fallback,
  Razorpay/mock payment panel with success/failure banners, cancel, refund notes, clip status card showing only the
  thumbnail when READY; the player comes in M11), `/orders`, `/login` (log in + customer sign-up),
  `/register/owner`, `/pending` (owners awaiting approval; "Check again" refreshes the session), `/forbidden`,
  root `app/not-found.tsx`.
- Owner dashboard `/dashboard`: restaurant list → `/dashboard/new`, and per restaurant (`[restaurantId]/layout.tsx`
  tabs): live orders (`page.tsx`, STOMP on `/topic/restaurant/{id}/orders`, accept/reject/close out, "Paid at counter"),
  `menu` (categories/items CRUD, ↑↓ reorder by rewriting `sortOrder`, optimistic availability toggle, item photos),
  `tables` (add/rename/delete, new QR token, PDF via `parseAs: "blob"`), `stations` (add/rename/delete, assign chefs,
  pairing QR, online dot), `chefs` (create with initial password, edit + photo, remove, consent + station), `clips`
  (filter, thumbnail → inline player, Retry, reason text), `profile` (`components/dashboard/restaurant-form.tsx`, also
  used by `new`). Shared: `lib/owner/{queries,uploads,status}.ts`, `components/dashboard/confirm-button.tsx`
  (two-click confirm instead of browser dialogs).
- Admin `/admin` (pending owners → approve; restaurants by status → approve/reinstate/suspend) and `/admin/users`.
- Chef `/chef/settings` (profile, consent accept / withdraw). The KDS consent gate has Accept / Decline.
- `lib/api/`: `schema.ts` is generated by `npm run api:gen` from `openapi/openapi.json`; never edit it by hand.
  `client.ts` is the `openapi-fetch` client with a bearer-token middleware; `unwrap()` turns errors into `ApiError`.
- Auth (one session for every role since M10.5): `app/api/session/*` route handlers keep the refresh token in an
  httpOnly cookie (`vk_rt`, path `/api/session`) and give the page only the access token, held in memory
  (`session` in `lib/auth/token-store.ts`). Refreshes are serialised across tabs with the Web Locks API, because the
  backend revokes reused refresh tokens. `lib/api/client.ts` adds the bearer token and, on a 401, refreshes once and
  replays the request; if that fails the session clears and guards send the user to `/login?next=…`.
  `/api/session/register` accepts role CUSTOMER or RESTAURANT_OWNER. `AuthProvider` + `ToastProvider` wrap the whole
  app in `app/providers.tsx`.
- Roles: `lib/auth/roles.ts` (`homeFor` = landing page per role, `navFor` = header links). `components/layout/guards.tsx`
  `RequireRole` (anonymous → login, pending owner → `/pending`, wrong role → `Forbidden` 403 view).
- Dev only (`NODE_ENV=development`): header role switcher (`DevRoleSwitcher` in `components/layout/app-header.tsx`)
  posts to `app/api/dev/login-as`, which logs in as a dev-seed account with the server-side `DEV_SEED_PASSWORD`;
  the route answers 404 in production builds.
- Per-device state goes through `lib/storage.ts` (localStorage + `useSyncExternalStore`): carts per restaurant
  (`lib/cart`), table sessions (`lib/table-session.ts`) and guest order tokens (`lib/guest-orders.ts`).
- `app/(kitchen)/kitchen`: tablet kitchen display. Flow: login → (chef: consent gate, pick station | owner: pick
  restaurant) → `components/kds/board.tsx`, with New / Preparing / Ready columns, one-tap actions, chime + flash
  on new orders, age colours, offline banner and wake lock. Board rules live in `lib/kds/board.ts`, which has unit tests.
- `/kitchen` refuses customers/admins and pending owners; the board header links to chef settings / the dashboard
  and offers "Install app" when the browser fires `beforeinstallprompt` (`lib/use-install-prompt.ts`).
- `lib/realtime/use-stomp.ts`: the only WebSocket client. Exponential reconnect (1 s → 30 s), fresh token per
  attempt, and status `connecting | connected | offline | denied`. Callers poll REST while it isn't `connected`.
- PWA: `public/kitchen.webmanifest` (scope `/kitchen`, fullscreen, landscape), `public/icons/kitchen-*`, and
  `public/kitchen-sw.js` (registered in production builds only; caches the shell and static assets, never `/api/*`).
- `app/(station)/station`: station phone PWA (`station.webmanifest`, `station-sw.js`). It pairs from
  `#pair=<code>`; device credentials are in localStorage via `lib/station/device.ts` (`stationApi` adds
  `X-Device-Token`).
  - `lib/station/runtime.ts` (`StationRuntime`) runs the recorder (`recorder.ts`: a new MediaRecorder every 60 s,
    VP8 WebM, 1.2 Mbit/s, no audio), clock sync every 5 min (`clock.ts`), the IndexedDB ring buffer (`db.ts`:
    chunk metadata and blobs in separate stores) and the upload queue.
  - The pure rules in `buffer.ts` (overlap, pinning, expiry, readiness, backoff) have unit tests.
  - The runtime also heartbeats every 30 s and offers `retryUploadsNow()`; the screen has "Retry now", "Install as
    app" and a toggleable debug panel (IndexedDB chunks, pending uploads, clock offset, last heartbeat).
- KDS owner header: **Stations** (pairing QR per station, `components/kds/stations-drawer.tsx`) and **Bills**.
- Shared hooks: `lib/use-wake-lock.ts`, `lib/use-battery.ts`.
- Next 16 writes `web/AGENTS.md` and `web/CLAUDE.md` itself on dev/build; leave them alone.

### Established patterns (follow these in new code)

- **Errors:** throw `ApiException(HttpStatus, message)`. Never return error bodies by hand.
- **Owner-scoped routes:** use `/api/owner/restaurants/{restaurantId}/...` and call
  `RestaurantAccess.requireOwned` first. Then look child rows up by `(id, restaurantId)`. Another owner's
  resource answers **404**, never 403.
- **Role checks:** class-level `@PreAuthorize("hasRole('X')")`. Only ACTIVE users get a role authority, so
  PENDING_APPROVAL owners and DISABLED users fail every `hasRole`.
- **Services:** return DTOs built inside the transaction (`open-in-view` is off). Controllers never touch entities.
- **Time:** inject `Clock` wherever "now" matters, so tests can control it. Restaurant-local logic (opening
  hours, order business date) uses `app.restaurant.timezone` (Asia/Kolkata).
- **Secrets at rest:** store only a SHA-256 of credentials (refresh tokens, station device tokens). Table QR
  tokens are stored raw because they must be re-printable.
- **Purpose-scoped tokens:** use `ScopedTokens` with a new purpose string. Each purpose derives its own key,
  so its tokens are never valid as login tokens or for another purpose.
- **Order status:** changes go only through `OrderStateMachine.transition` (`Order.applyStatus` is package-private).
- **Recording consent:** anything that records or shows a chef must check
  `ChefConsentService.hasValidConsent` or `KitchenStaffing.requireRecordableOnStation`.
- **Images:** store object keys, never URLs. Validate keys with `MediaKeys.requireValid` and return
  short-lived presigned GET URLs.
- **Money:** order lines snapshot name, price and veg flag. Tax is integer basis-points math in `OrderProperties.taxOn`.
- **API contract:** when you add or change a response DTO, mark fields that can be null with `@Schema(nullable = true)`,
  then run `npm run api:update` in `web/`. The web app must use the generated types, never hand-written API shapes.
- **Payments:** amounts come only from `OrderPaymentService.amountDue` (the order lines plus the tax fixed at
  placement), never from the client. Gateway calls happen outside DB transactions; settle through the
  `apply*` methods, which are idempotent. Never call `Order.markPaid` and friends directly. Work done from
  AFTER_COMMIT listeners needs `REQUIRES_NEW`. Listener order matters: the broadcaster is `HIGHEST_PRECEDENCE`.
- **Device vs user auth:** device endpoints use `@PreAuthorize("hasRole('STATION_DEVICE')")` and must live under
  `/api/station-device/` or `/api/clips/`, the only paths where the device filter runs. Never accept a device
  token anywhere else.
- **Footage:** never send a window (or create upload URLs) unless consent was valid at READY. Keys are server-built
  and validated against `raw/{restaurantId}/{orderId}/`.
- **Background work:** anything slow or retryable goes through `JobQueue.enqueue` inside the business transaction
  plus a `JobHandler`; handlers must be idempotent (a job can run again after a crash). External processes go
  through `FfmpegRunner`-style wrappers with timeouts, never bare `Process.waitFor()`.
- **Live updates:** after changing an order, call `OrderEvents.changed(order)` inside the transaction. New topics
  must be added to both `realtime/Topics` and `RealtimeAccess`; anything unlisted is refused.
- **Web pages for a role:** wrap them in `RequireRole` (via the group/segment `layout.tsx`), report action results with
  `useToast()`, confirm destructive actions with `ConfirmButton`, upload images with `uploadImage()` then save the key.
  Inputs that set a width use `inputBaseClass` (`inputClass` includes `w-full`).
- **Web UI states:** every page handles loading, empty and error states (`components/ui.tsx`). Show money with
  `formatPaise` and times with `formatTime` / `formatDateTime`, which display IST.

### Key decisions

- Owners may have several restaurants. Restaurants start PENDING_APPROVAL; only an ADMIN moves them to ACTIVE.
- Online orders stay invisible to the restaurant until PAID. PAY_LATER is dine-in only. Rejecting or
  cancelling a PAID order refunds it automatically (`REFUND_PENDING` until the gateway confirms, then
  `REFUNDED`). Money captured for an order that can't use it (cancelled, or already paid) is refunded too.
- Payment is a separate step after placing an order: `POST /api/orders/{id}/payment` returns the checkout
  (Razorpay order id, public key, server-computed amount), then `POST .../payment/confirm` with Razorpay's
  ids + signature. The same endpoints exist under `/api/guest/orders/...`. PAY_LATER bills can be paid online
  at any time, or marked "Paid at counter" by the owner (KDS → Bills).
- Display numbers come from the per-restaurant, per-IST-day `order_daily_counters` table via an upsert.
- Dine-in: `GET /api/tables/{qrToken}` returns the menu plus a 4-hour signed `tableSessionToken`. Orders send that
  token; the table comes only from it. Regenerating the QR invalidates old sessions (fingerprint check).
- Guest checkout (`/api/guest/orders`) creates or reuses a passwordless CUSTOMER by phone. The guest gets an
  order-scoped `X-Guest-Order-Token`, never login tokens. Registering later with that phone claims the account.
- "Veg-only" restaurant = no non-veg items on its menu (derived, not a flag).
- Removing a chef is a soft delete: their user is DISABLED and consent history is kept.
- Recording-consent text lives in `backend/src/main/resources/consent/recording-consent-v1.txt`. It is a **DRAFT that
  needs legal review**. Bumping `app.chef-consent.current-version` forces every chef to re-accept.

### Running & testing

- **JDK:** the user's shell `JAVA_HOME` points at JDK 17. Prefix Maven with
  `JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null || echo /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home)`.
- **Tests:** `cd backend && ./mvnw clean verify`. `*Test` = unit tests (surefire); `*IT` = integration tests
  (failsafe). Integration tests extend `support/IntegrationTest`, which starts shared Testcontainers
  `postgres:16-alpine` and `rustfs/rustfs:1.0.0` (S3). `minio/minio` images are no longer pullable.
- **Test helpers:** `support/AuthClient` (register, login, `approvedOwnerToken()`, `send(...)`),
  `support/OwnerFixtures` (restaurant, menu, chef/station builders, `consentedChefOnStation`), and
  `order/OrderTestKitchen` (a ready-to-order restaurant).
- **Testcontainers** is pinned to 1.21.4, which Docker Desktop 29 requires. Last full run (M10.5): 346 unit + 142 integration tests passing.
- **FFmpeg:** clip tests need `ffmpeg`/`ffprobe` on PATH (`brew install ffmpeg`; skipped otherwise). Sample chunks:
  `src/test/resources/clips/chunk-{1,2}.webm` (30 s, 320×180, black / blue). Job worker threads are 0 in tests;
  `order/ClipPipelineIT` drives `JobWorker.runOnce` by hand.
- **Docker:** `backend/Dockerfile` (multi-stage, Temurin 21, runtime installs ffmpeg + DejaVu fonts, non-root uid 10001).
- **Browser end-to-end (manual, not in CI):** headless Chrome with `--use-fake-device-for-media-stream` via
  `puppeteer-core`, against throwaway containers. It verified pairing → recording → READY → upload → `UPLOADED`
  in M9. The Claude-in-Chrome extension wasn't connected in M6–M9.
- **Razorpay in tests:** `support/FakeRazorpay` (JDK HttpServer) stands in for the Razorpay API. `RazorpayPaymentIT` runs the
  real provider against it. Webhook/signature secrets used there are test-only constants.
- **springdoc** is pinned to 2.8.8. 2.8.9+ targets Boot 3.5 and fails on Boot 3.4 with an invalid swagger-ui path pattern.
- **Web:** `cd web && npm test && npm run typecheck && npm run lint && npm run build` (34 unit tests, Vitest + jsdom).
- **E2E:** `cd web && npm run test:e2e` (Playwright 1.63.0, `e2e/happy-path.spec.ts`: table-QR PAY_LATER order → owner
  accepts → chef starts/readies on /kitchen → customer sees Ready → owner marks paid at counter). Needs a running stack
  with the backend's `dev-seed` profile and `DEV_SEED_PASSWORD`; uses installed Google Chrome (`PLAYWRIGHT_CHANNEL` to
  override); `E2E_BASE_URL` / `E2E_API_URL` default to :3000 / :8080. Verified green in M10.5 on a throwaway stack.
  npm bug: adding a package with `npm install <pkg>` can drop the platform binary (`@rolldown/binding-*`) from the
  lockfile, after which Vitest fails with "Cannot find module './rolldown-binding…'". After adding any package,
  delete `node_modules` and `package-lock.json` and run `npm install` (all direct deps are pinned exactly).
- **Local run:** infra Postgres listens on host port **5433**. Start the backend with
  `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` and these env vars: `JWT_SECRET`, `ADMIN_PHONE`,
  `ADMIN_PASSWORD`, `S3_ENDPOINT=http://localhost:9000`, `S3_ACCESS_KEY` and `S3_SECRET_KEY` (matching
  `infra/.env`), and optionally `WEB_BASE_URL`. The full list is in `backend/.env.example`.
- **Git:** the repo is on GitHub (`origin`). Leave changes uncommitted unless the user asks for a commit.

### Known gaps / backlog

- Phone numbers are not verified (OTP pending). Guest-account claiming and guest reuse trust the phone.
- Guest checkout has no rate limit. Login rate limiting is in-memory, so it only works on a single node.
- Unpaid online orders never expire. There is no way to settle PAY_LATER bills. Order placement has no
  idempotency key. Owners only see active orders, not history.
- Chefs cannot change the initial password their owner sets.
- Uploads have no size limit server-side (presigned PUT; the dashboard checks ≤ 5 MB JPG/PNG/WebP client-side). The
  listing doesn't filter by distance from the customer.
- The QR PDF uses Helvetica (Latin only); non-Latin restaurant names need an embedded Unicode font.
- `application-local.yml` hardcodes the local DB password, against the env-vars-only rule. Raised with
  the user; not changed yet.
- Web: delivery addresses need lat/lng (browser geolocation or typed in; no geocoding yet). With
  `PAYMENT_PROVIDER=mock` the order page shows a test-mode payment panel.
- Payments: counter payments are not refunded automatically (staff return cash in person). An amount mismatch
  on capture is logged as an error and left FAILED for manual review. There are no partial refunds and no
  payout or settlement reports yet. "Paid at counter" is on both the KDS (Bills) and the dashboard's live orders.
- Station recorder: the phone must stay on screen. Android may throttle or kill a hidden tab, so the UI keeps a
  wake lock and warns when not charging. 45 min at ~1.2 Mbit/s is ~400 MB of IndexedDB, which can be evicted
  under storage pressure (`storage.persist()` is requested). Chunks switch with a few ms gap. Anyone at the
  station is recorded locally, but only windows for consented chefs are uploaded.
  The device token lives in localStorage. Camera access needs HTTPS off localhost.
- Realtime: subscriptions are authorised at SUBSCRIBE time only. A chef removed mid-shift keeps receiving events
  until their socket reconnects. The in-memory broker only reaches clients of one backend instance; scaling out
  needs a broker relay (e.g. RabbitMQ).
- KDS PWA: installing and the service worker need HTTPS (or localhost), so a tablet on LAN `http://192.168…` can
  run the board but can't install it. Owners can't "Start" an order from the KDS (the backend needs a chef + station).
- Clips: one job renders one order, so orders sharing a window are rendered separately (simple, more CPU). The
  timelapse has no audio (recordings have none). Presigned clip links last `S3_DOWNLOAD_URL_TTL` (1h); the order
  page refetches. No download/share buttons or chef tips yet. FAILED clips aren't retried automatically; requeue by
  inserting a job. Fonts on the overlay depend on the host (DejaVu in Docker); non-Latin names need a suitable font.
- M10.5 frontend: owners' live orders show active orders plus unpaid bills only (no order history). Chef "current
  station" is the assignment, not where they last cooked. Every anonymous page load makes one 401
  `/api/session/refresh` call (session restore). The restaurant logo has no preview URL in the API (only `logoKey`).
  Admin is minimal until Milestone 14.
- Web: ESLint is pinned to 9.39.x, which npm flags as end-of-life; move to ESLint 10 once `eslint-config-next` supports it.
