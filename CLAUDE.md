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
├── backend/        Spring Boot 3.4, Java 21, Maven — modular monolith, package-by-feature
│   └── src/main/java/com/virtualkitchen/
│       ├── auth/  restaurant/  menu/  order/  kitchen/  clip/  payment/  tip/  hygiene/  billing/  admin/
│       ├── realtime/       STOMP/SockJS live updates
│       ├── common/         error, jobs, storage, ratelimit, settings, openapi, … (shared infrastructure)
│       ├── config/         SecurityConfig, ProductionGuard
│       └── devseed/        demo data (profile dev-seed only)
├── web/            Next.js 16 App Router, TypeScript, Tailwind 4 (deployed to Vercel)
│   └── app/
│       ├── (customer)/     customer site + public pages (share links, chef pages, legal pages)
│       ├── (app)/          signed-in staff areas: /dashboard (owners), /admin, /chef/* (chef app)
│       ├── (kitchen)/      /kitchen — kitchen display (KDS) PWA for tablets
│       ├── (station)/      /station — station-phone recorder PWA
│       ├── (restaurant)/   empty leftover of the original plan; the owner dashboard is (app)/dashboard
│       └── api/            route handlers: session cookies (/api/session/*), dev login
├── ai-service/     Python 3.11 FastAPI + YOLO — hygiene detection (M13)
├── infra/          docker-compose.yml (local Postgres 16 + RustFS as S3), docker-compose.prod.yml, caddy/,
│                   backup/, deploy/, prod/*.env.example (M15)
├── .github/workflows/   ci.yml, deploy.yml (M15)
├── PRODUCTION.md   deployment + go-live checklist
└── README.md       local setup and per-milestone walkthroughs (the manual checks)
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
| 6 | Customer web app (Next.js): browse, menu, QR dine-in, checkout, order tracking, login | Committed; manual check pending | none |
| 7 | Kitchen display (KDS) PWA + STOMP/SockJS live order updates | Committed; manual check pending | none |
| 8 | Real payments: Razorpay checkout + webhooks, payments table, auto refunds, pay-at-counter | Committed; manual check pending | V6 |
| 9 | Station recorder: phone pairing, device auth, 60 s chunk ring buffer, footage windows on READY, uploads | Built, awaiting user's manual check | V7 |
| 10 | Clip pipeline: Postgres job queue, FFmpeg render (cut, timelapse, overlay, 720p H.264), clips table, retention, READY push | Built, awaiting user's manual check | V8 |
| 10.5 | Complete frontend for M1–10 (role-aware app, owner dashboard, minimal admin, chef settings, station debug) + dev tooling (dev-seed profile, role switcher, Playwright) | Built, awaiting user's manual check | V9 |
| 11 | Clip viewing & sharing: 10-min watch/download links, public share links /c/{token} with OG tags, revoke, per-visitor-per-day view counts, chef profile, dashboard views/shares | Built, awaiting user's manual check | V10 |
| 12 | Chef profiles & tipping: Razorpay Route linked accounts (payout onboarding), tips as transfer orders, follows, public chef page /chef/[id], chef "My tips"/"My clips", owner tip counts | Built, awaiting user's manual check | V11 |
| 13 | AI hygiene scoring: ai-service (FastAPI/YOLO/OpenCV), HYGIENE_ANALYZE job lane, hygiene_results, rolling scores, owner trend/worst clips/disputes, admin review + public switch | Built, awaiting user's manual check | V12 |
| 14 | Restaurant billing & admin panel: plans (admin-priced), Razorpay Subscriptions + webhooks, trial/grace/suspension, GST invoice PDFs, owner Billing tab, FSSAI/GST details, admin overview metrics, restaurant detail, billing admin, audit log | Built, awaiting user's manual check | V13 |
| 15 | Production deployment: prod Dockerfiles + compose (Caddy, backend, ai-service, backup) for one Indian-region VM, managed Postgres, R2, GitHub Actions CI/CD over SSH with auto-rollback, Vercel, prod profile (JSON logs, Flyway, health), Sentry, daily backups + restore drill, public rate limits, legal pages, PRODUCTION.md | Built, awaiting user's manual check | none |
| 16 | Not yet defined | — | next: V14 |

**Manual checks.** The user has not confirmed the manual checks of milestones 6–15. Their last commit message
("completed till milestone 8 and tests pending for 6,7,8") says the checks for 6–8 are still pending, and each later
milestone was followed straight away by the next one. Before defining the next milestone, ask which checks have
passed. Where each milestone's checks live (README sections unless noted):

| Milestone | Checks |
|---|---|
| 6, 10.5 | "Trying the whole product (dev seed)" (role-by-role walkthrough, `npm run test:e2e`) |
| 7 | "Trying the whole product" (KDS part) |
| 8 | "Payments (Razorpay test mode)" |
| 9 | "Station recorder phones (`/station`)" |
| 10 | "Kitchen clips (processing)" |
| 11 | "Watching and sharing clips" |
| 12 | "Chef payouts and tips (Razorpay Route)" |
| 13 | "4. AI hygiene scoring (ai-service)" |
| 14 | "Restaurant billing and the admin panel" |
| 15 | `PRODUCTION.md` (go-live checklist) and the README "Production" section |

The git history has only three commits (up to M8 plus a "little frontend" commit). Everything from M9 to M15 is
uncommitted in the working tree. Don't commit unless the user asks.

`ai-service/` (M13): Python 3.11 FastAPI service, see its README. NO fine-tuned PPE weights are committed (none could
be produced here); with the stock COCO `yolo11n.pt` only `person` is detected and every item is "not evaluated".

### Database migrations (Flyway, `backend/src/main/resources/db/migration`)

| Version | Adds |
|---|---|
| V1 | baseline (empty) |
| V2 | `users`, `refresh_tokens` |
| V3 | `restaurants`, `restaurant_opening_hours`, `menu_categories`, `menu_items`, `dining_tables`, `kitchen_stations`, `chefs`, `station_assignments`, `chef_consents` |
| V4 | `orders`, `order_items`, `order_daily_counters` |
| V5 | guests: `users.password_hash` nullable for CUSTOMER only |
| V6 | `payments` (one row per attempt), `payment_webhook_events` (dedupe) |
| V7 | station pairing columns on `kitchen_stations`, `orders.clip_status`, `clip_requests` |
| V8 | `jobs` (queue), `clips`, `clip_requests.raw_deleted_at`, `restaurants.logo_key` |
| V9 | `kitchen_stations.device_last_seen_at` |
| V10 | `clip_shares`, `clip_views` |
| V11 | `chef_payout_accounts`, `tips`, `chef_follows` |
| V12 | `hygiene_results`, `hygiene_disputes`, `platform_settings` |
| V13 | `plans`, `restaurant_subscriptions`, `invoice_counters`, `invoices`, `admin_audit_log`; compliance + `suspended_reason` columns on `restaurants`; trial backfill |

The next migration is **V14**.

### Domain states (enums)

- `Role`: CUSTOMER, RESTAURANT_OWNER, CHEF, ADMIN. `UserStatus`: ACTIVE, PENDING_APPROVAL (new owners), DISABLED
  (removed chefs). `RestaurantStatus`: PENDING_APPROVAL → ACTIVE ↔ SUSPENDED; `suspended_reason` is ADMIN or BILLING,
  and only a BILLING suspension is lifted by a payment.
- `OrderStatus` (`OrderStateMachine`):
  - common moves: PLACED → ACCEPTED | REJECTED | CANCELLED, ACCEPTED → PREPARING | CANCELLED, PREPARING → READY;
  - READY → SERVED (dine-in), PICKED_UP (takeaway) or OUT_FOR_DELIVERY → DELIVERED (delivery);
  - customers may only cancel, chefs may only move to PREPARING/READY, owners may make any legal move.

  Illegal moves → 409, moves not allowed for the actor → 403. `OrderType`: DINE_IN, TAKEAWAY, DELIVERY.
- `PaymentStatus` (on the order): PENDING (online, not paid; hidden from the restaurant), PAID, FAILED, PAY_LATER,
  REFUND_PENDING, REFUNDED. `PaymentAttemptStatus` (`payments` rows): CREATED, CAPTURED, FAILED, REFUND_REQUESTED,
  REFUND_PENDING, REFUNDED.
- `ClipStatus` (order/clip): PENDING → UPLOADED → PROCESSING → READY → EXPIRED, or FAILED / NOT_AVAILABLE.
  `ClipRequestStatus`: PENDING_UPLOAD, UPLOADED, NOT_AVAILABLE.
- `TipStatus`: CREATED → PAID → TRANSFERRED, or FAILED. `PayoutStatus`: NOT_SET_UP (no row), PENDING_KYC, ACTIVE.
- `SubscriptionStatus`: TRIALING, ACTIVE, PAST_DUE, SUSPENDED, CANCELLED.

### API surface (controller roots)

- Public: `/api/auth/*`, `GET /api/restaurants[/{slug}]`, `GET /api/tables/{qrToken}`, `/api/guest/orders/**`
  (order-scoped token), `/api/public/{clips,chefs,legal}/**`, `GET /api/time`, `/api/health`, `/actuator/health/**`,
  `/ws` (STOMP authenticates on CONNECT), `POST /api/payments/razorpay/webhook`, `POST /api/station-device/pair`.
- Any signed-in user: `GET /api/me`. Customer: `/api/orders/**` (incl. `/{id}/payment`, `/{id}/clip`, `/{id}/tips`), `/api/chefs/{id}/follow`,
  `/api/me/followed-chefs`.
- Owner: `/api/owner/restaurants/{id}/{menu,tables,stations,chefs,orders,clips,hygiene,billing,uploads,compliance}`.
- Chef: `/api/chef/{orders,consent,payout-account,tips,clips,me}`.
- Station phone (`X-Device-Token`): `/api/station-device/**`, `/api/clips/{orderId}/**`.
- Admin: `/api/admin/{owners,restaurants,users,clips,hygiene,billing,metrics,audit}`.

### Backend map (`com.virtualkitchen`)

- `config/SecurityConfig`: stateless JWT filter chain, CORS (`CORS_ALLOWED_ORIGINS`, also used for the `/ws` origin
  check), `@EnableMethodSecurity`, and the public routes listed under "API surface" above. Everything else needs a JWT.
- `common/`: `error/` (`ApiException`, `GlobalExceptionHandler`, `ErrorResponseWriter`), `crypto/SecureTokens`
  (random tokens, SHA-256), `storage/ObjectStorage` (presigned S3 PUT/GET), `geo/Geo` (haversine),
  `web/PageResponse`, `config/` (`Clock` bean, `WebProperties`, scheduling), `health/` (`/api/health`), `jobs/` (queue,
  below), `settings/PlatformSettings` (runtime key/value settings in `platform_settings`: hygiene public switch, billing
  trial/grace days), `billing/PlanLimits` and `hygiene/PublicHygieneScores` (interfaces other modules depend on, so
  they don't import `billing`/`hygiene` directly), `ratelimit/` (M15).
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
- `admin/`: approve owners (`/api/admin/owners`), approve or suspend restaurants (`/api/admin/restaurants`, plus
  `GET /{id}` detail: owner, compliance with a presigned FSSAI document URL, subscription, invoices), read-only paged
  user list (`GET /api/admin/users?role=&page=&size=`, `AdminUserListController`). `AdminAudit.audited(adminId, action,
  targetType, targetId, details, work)` runs the change and writes `admin_audit_log` in one transaction; every admin
  mutation and every view of private material (restaurant detail/licence, clip playback) is recorded.
  `AdminPanelController`: `GET /api/admin/metrics?days=` (`AdminMetricsService`, IST days: orders placed, GMV = totals of
  orders not rejected/cancelled, clips READY, clip views, shares, paid tips; active restaurants; MRR = plan prices of ACTIVE subscriptions) and
  `GET /api/admin/audit?action=&page=&size=`.
- `billing/` (M14): `plans` (price_paise excl. GST, `PlanFeatures` jsonb: maxStations, clipRetentionDays, analytics;
  the Razorpay plan id is cached per price in `provider_plan_id/provider_amount_paise`), `restaurant_subscriptions`
  (one per restaurant; TRIALING → ACTIVE ↔ PAST_DUE → SUSPENDED, CANCELLED; `pending_plan_id` = scheduled downgrade).
  `BillingService`: `RestaurantCreatedEvent` starts the trial (`PlatformSettings` `billing.trial_days`, default
  `BILLING_TRIAL_DAYS`); checkout creates a gateway subscription starting at trial end (`SubscriptionProvider`: mock or
  `RazorpaySubscriptionProvider`); upgrades apply now, downgrades at the next charge while ACTIVE; `apply*` methods are
  idempotent and fed by `RazorpayWebhookService` (`subscription.*`) or `AdminBillingService.simulate` (mock only).
  Hourly `enforce()`: unpaid trial end → PAST_DUE with grace (`billing.grace_days`), grace or paid-up cancellation over
  → SUSPENDED + `RestaurantService.suspendForBilling` (`suspended_reason` BILLING; a charge reinstates only BILLING
  suspensions). It implements `common/billing/PlanLimits` (station count on create, clip `expires_at` from retention).
  `InvoiceService.issue` (per gateway payment id, inside the charge transaction): numbers `PREFIX/FY/00001` via the
  `invoice_counters` upsert, `GstSplit.ofTotal` back-computes taxable + CGST/SGST (same state) or IGST, buyer snapshot;
  `InvoicePdfRenderer` (OpenPDF). Owner `/api/owner/restaurants/{id}/billing` (GET, `checkout`, `checkout/confirm`,
  `PUT plan`, `cancel`, `invoices/{id}.pdf`); admin `/api/admin/billing` (plans CRUD, settings, subscriptions,
  `restaurants/{id}/simulate`, invoice PDF). Restaurant compliance: `GET|PUT /api/owner/restaurants/{id}/compliance`
  (FSSAI 14 digits + document key `FSSAI_LICENCE` (PDF/image), legal name, GSTIN whose prefix must match the state).
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
- `clip/ClipPlaybackService` (M11): watch + download links presigned for `app.clips.watch-url-ttl` (10m;
  download uses `response-content-disposition: attachment; filename="{slug}-order-{n}.mp4"`), issued only via
  `GET /api/orders/{id}/clip` (customer), `GET /api/guest/orders/{id}/clip` (guest token),
  `GET /api/owner/restaurants/{rid}/clips/{orderId}/playback` and `GET /api/admin/clips/{orderId}/playback`.
  `OrderResponse.clip` (`ClipView`) carries only duration, thumbnail and expiry, because chefs and the restaurant topic
  see orders too. Share links: `POST|DELETE …/clip/share` (customer or guest; one active link per clip, token stored
  as-is in `clip_shares` so it can be copied again; revoke sets `revoked_at`). Public: `GET /api/public/clips/{token}`
  (404 revoked/unknown, 410 expired; no customer data) and `POST /api/public/clips/{token}/views {visitorId}`
  (`clip_views` PK clip+IST date+sha256(visitorId) → one view per visitor per day). `GET /api/public/chefs/{id}`
  (`PublicChefController`; active chefs of ACTIVE restaurants). `/api/public/**` is permitted in `SecurityConfig`.
  `OwnerClipResponse` has `viewCount`, `shareCount`, `shared` and no video link.
- `tip/` (M12): money never rests with the platform. `PayoutProvider` (payment pkg; `RazorpayPayoutProvider` /
  `MockPayoutProvider`): Route linked account = `POST /v2/accounts` (type route, individual, PAN) → stakeholder → Route
  product → `PATCH` settlements (bank). `RazorpayClient` uses the JDK `HttpClient` factory because `HttpURLConnection`
  can't send PATCH. `chef_payout_accounts` stores only account/product ids, status (`PayoutStatus` NOT_SET_UP = no row,
  PENDING_KYC, ACTIVE), legal name, bank code (IFSC first 4), last 4 of the account and PAN; request DTO `toString()`s
  mask. Mock: new accounts are under_review and become activated on the next fetch (`POST /api/chef/payout-account/refresh`).
  Endpoints: `GET|PUT /api/chef/payout-account`, `POST …/refresh`. Legal name/PAN can't change after set-up (409).
  Tips: `TipService` creates a `tips` row (CREATED), then a gateway order with `transfers[{account, amount = amount −
  platform fee (app.tips.platform-fee-bps, default 0), on_hold:false}]`; confirm (signature + fetch, like orders) or the
  `payment.captured` webhook → PAID; `transfer.processed` → TRANSFERRED (mock: instantly); `transfer.failed` is logged
  as an ERROR (money held at Razorpay; retry from Dashboard). Eligible chefs on an order: the one who cooked it, or
  chefs the customer follows at that restaurant, and only with ACTIVE payouts. Customer `GET|POST /api/orders/{id}/tips`,
  `POST …/tips/{tipId}/confirm`; guest equivalents under `/api/guest/orders/{id}/tips`; chef `GET /api/chef/tips`
  (paged, totals; no customer data). Follows: `chef_follows`, `PUT|DELETE|GET /api/chefs/{id}/follow`,
  `GET /api/me/followed-chefs?restaurantId=` (CUSTOMER). The Razorpay webhook now dispatches tips before food orders,
  plus `transfer.*` and `product.route.*` (applied from the signed payload, no gateway call inside the transaction).
- `kitchen/chef/ChefStats`: counts for `PublicChefResponse` (clips, views, tips count, followers, tippable,
  `hygieneScore` null) and `ChefResponse.tipCount/payoutStatus` for owners (counts, never amounts).
- `clip/ChefClipController`: `GET /api/chef/clips` ("My clips": thumbnails + view counts, no watch links).
- `hygiene/` (M13): `ClipLifecycle.finish` publishes `ClipReadyEvent`; `HygieneScheduler` (AFTER_COMMIT, own
  REQUIRES_NEW tx, swallowed errors) enqueues `HYGIENE_ANALYZE` when `app.hygiene.enabled`. `HygieneJob` runs in job
  lane "hygiene" (`app.jobs.lanes.hygiene`, `JobHandler.lane()`, `JobWorker` starts threads per lane): presigns the
  video via `ObjectStorage.clipUrlVia(AI_VIDEO_S3_ENDPOINT, …)` (SigV4 signs the host, so the URL must be signed for
  the address the AI container uses), calls `HygieneClient` (X-Api-Key; 4xx other than 401/408/429 → permanent),
  stores `hygiene_results` (one row per clip+model_version; newest row = the clip's result; per-item %, score = mean of
  judged items via `HygieneScoring`, violations jsonb). `HygieneService`: effective score = override, or null if
  excluded, else model score; rolling averages over clips READY within `app.hygiene.window`; implements
  `common/hygiene/PublicHygieneScores` (used by `RestaurantDetailResponse.hygiene` and `PublicChefResponse.hygiene`):
  only when `platform_settings['hygiene.public_scores_enabled']` is true and clips ≥ `min-public-clips`.
  Endpoints: owner `GET /api/owner/restaurants/{id}/hygiene` (score, weekly trend, chefs, 10 worst), `POST
  …/hygiene/results/{resultId}/disputes`; admin `GET /api/admin/hygiene/disputes?status=`, `POST
  …/disputes/{id}/resolve` (UPHOLD + overrideScore | exclude, or REJECT), `GET|PUT /api/admin/hygiene/settings`.
- `common/time/TimeController`: public `GET /api/time` for device clock sync.
- `common/ratelimit/` (M15): `PublicRateLimitFilter` (servlet filter before security) + `PublicRateLimiter` (in-memory token
  buckets per rule + client IP; rules in `app.rate-limit.rules`: `auth` POST login/register/refresh/pair 30/min,
  `public-write` 60/min, `public-read` GET 300/min; first match wins; 429 + Retry-After in the standard error shape).
  `ClientIp`: trusts `X-Client-IP` only with `X-Proxy-Secret` = `BACKEND_PROXY_SECRET` (the Vercel server sends both,
  `web/lib/server/proxy-headers.ts`), else the peer address (Caddy's X-Forwarded-For via `forward-headers-strategy`).
  Disabled in `application-test.yml`; `RateLimitIT` enables it.
- `config/ProductionGuard` (profile `prod`): refuses to start with mock payments, Razorpay test keys, empty webhook
  secret, non-https CORS/web/storage, short proxy secret, rate limiting off, or `dev-seed`; `PRODUCTION_GUARD_ALLOW`
  waives named checks (not dev-seed) for staging rehearsals.
- `application-prod.yml`: ECS JSON console logs, forward headers, graceful shutdown, health details only when
  authorised, API docs off, `create-buckets` off. Always on: Hikari `connectTimeout` 10 s / `socketTimeout` 30 s
  (`DB_SOCKET_TIMEOUT_SECONDS`), actuator liveness/readiness probes. Sentry (`sentry-spring-boot-starter-jakarta` +
  `sentry-logback` 8.58.0; ERROR logs + unhandled exceptions, `send-default-pii: false`, off without `SENTRY_DSN`).
- `kitchen/consent/PublicConsentController`: `GET /api/public/legal/recording-consent` (version, text, hash) for the
  web's `/recording-consent` page.
- `common/openapi/OpenApiConfig`: springdoc spec at `/v3/api-docs` (OpenAPI 3.0) and Swagger UI at `/swagger-ui.html`.
  Every field of a response-only schema is marked required; nullable fields carry `@Schema(nullable = true)`
  (object fields become `allOf` + nullable; `nullable` is stripped from component schemas, where springdoc leaks it).

### Web map (`web/`, Next.js 16 App Router, TypeScript, Tailwind 4, TanStack Query 5)

- Route groups: `(customer)` (narrow, `AppHeader`), `(app)` (wide, `AppHeader wide`: `/dashboard/**`, `/admin/**`,
  `/chef/settings`), `(kitchen)` (`/kitchen`, dark full-screen) and `(station)` (`/station`).
- Customer pages: `/` (city + filters), `/r/[slug]` (menu, veg filter, cart bar), `/t/[qrToken]` (saves the table
  session, then opens `/r/[slug]` in dine-in mode), `/checkout?r=<slug>` (client-side delivery-radius check via
  `lib/geo.ts`; staff accounts are told to log out), `/orders/[id]` (live over STOMP with 5 s polling fallback,
  Razorpay/mock payment panel with success/failure banners, cancel, refund notes,
  `components/clip/order-clip.tsx` clip card), `/orders`, `/login` (log in + customer sign-up),
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
- Clips (M11): `components/clip/order-clip.tsx` (Being prepared + progress animation, player with poster, Download via
  a fresh link, Share = Web Share API else copy (`lib/share.ts`), public link box, "Turn off link"; the player's src is
  kept stable and re-fetched only on error, since links last 10 min). Public share page `/c/[shareToken]` is a server
  component (`lib/api/server.ts`, React `cache`) with `generateMetadata` (OG `video.other` + `summary_large_image`,
  noindex) and `og.jpg/route.ts`, which proxies the thumbnail at a stable URL for crawlers; the client part
  (`components/clip/shared-clip.tsx`) records a view with `lib/visitor.ts`. `/chefs/[chefId]` public chef page.
  `metadataBase` = `SITE_URL` (must match the backend's `WEB_BASE_URL`). Next streams metadata but sends it blocking
  to WhatsApp/facebookexternalhit/Twitterbot etc. (its default `htmlLimitedBots`).
- Tips & chefs (M12): public chef page `/chef/[chefId]` (server component; stats, hygiene placeholder,
  `components/tip/follow-button.tsx`); `/chefs/[id]` permanently redirects there. The chef app lives in
  `app/(app)/chef/(me)/` (guarded layout; `payouts`, `tips`, `clips`, `settings`) so the public `/chef/[chefId]` in
  `(customer)` isn't guarded. `components/tip/tip-panel.tsx` ("Loved watching Chef X make this?", presets + custom,
  Razorpay or mock approve) appears under the clip once ≥ 80% has played, or at the top of the order page when checkout
  passed `?tipChef=&tipAmount=` (checkout's "Tip a chef you follow" section; the tip is paid after the order). `lib/tips.ts`.
- Hygiene (M13): dashboard tab `hygiene` (score card, CSS bar trend, chef table, `components/hygiene/result-card.tsx`
  with violation chips that load a playback URL and seek), admin `/admin/hygiene` (switch + dispute queue), badges on
  `/r/[slug]` and `/chef/[id]` when `hygiene` is present. Header nav: longest matching link is active.
- Admin (M14): `/admin` overview (metrics cards + CSS bar charts, 7/30/90 days), `/admin/restaurants` (pending owners,
  restaurants by status, Details) and `/admin/restaurants/[id]` (owner, licence document, subscription with mock
  simulate buttons, invoices), `/admin/users`, `/admin/hygiene`, `/admin/billing` (trial/grace, plan editor,
  subscriptions table), `/admin/audit` (filter by action, paged).
- Billing (M14): dashboard tab `billing` (status banners, plan cards with set up payment / upgrade / downgrade /
  keep, cancel autopay, invoice table with PDF via `saveBlob` from `lib/download.ts`), `lib/billing.ts`,
  `openRazorpaySubscription` in `lib/razorpay.ts` (mock: approve panel), Profile → `components/dashboard/compliance-card.tsx`
  (`lib/gst-states.ts`).
- Chef `/chef/settings` (profile, consent accept / withdraw). The KDS consent gate has Accept / Decline.
- Production (M15): Sentry via `@sentry/nextjs` 10.75.3 (`instrumentation.ts` + `sentry.server/edge.config.ts`,
  `instrumentation-client.ts`, `app/global-error.tsx`, `withSentryConfig` from `@sentry/nextjs/config` in
  `next.config.ts`; off without a DSN; source maps upload only with `SENTRY_AUTH_TOKEN`). `next.config.ts` adds
  security headers (nosniff, DENY framing, Referrer-Policy, Permissions-Policy camera/geolocation self). Server-side
  backend calls (`callBackend`, `lib/api/server.ts`) add `proxyHeaders()`. Legal pages `/privacy`, `/terms`,
  `/recording-consent` (`components/legal/legal-page.tsx`, values from `lib/legal.ts` env vars, draft banner until
  `LEGAL_PAGES_REVIEWED=true`), `SiteFooter` in the `(customer)` and `(app)` layouts, consent notes on both sign-up
  forms. `vercel.json`: region `bom1`, Git deploys of `main` off (production deploys come from `deploy.yml`).
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
- **Time:** inject `Clock` wherever "now" matters, so tests can control it. The bean (`ClockConfig.MicrosecondClock`)
  truncates to microseconds, Postgres's precision: Linux clocks have nanoseconds, which Postgres rounds on write, so a
  value pushed before commit wouldn't match the stored one (this failed CI once). Never use `Instant.now()` for
  persisted values (or truncate to `ChronoUnit.MICROS`), and never `Clock.tick(…, < 1 ms)` (its `millis()` divides by zero). Restaurant-local logic (opening
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
- **Testcontainers** is pinned to 1.21.4, which Docker Desktop 29 requires. Last full run (CI fix after M15): 362 unit + 166 integration tests passing on macOS and in a Linux container (as CI); ai-service 23 tests.
- **FFmpeg:** clip tests need `ffmpeg`/`ffprobe` on PATH (`brew install ffmpeg`; skipped otherwise). Sample chunks:
  `src/test/resources/clips/chunk-{1,2}.webm` (30 s, 320×180, black / blue). Job worker threads are 0 in tests;
  `order/ClipPipelineIT` drives `JobWorker.runOnce` by hand.
- **AI service:** `cd ai-service && docker build --target test .` runs its 23 pytest tests in python:3.11 (local
  Python is 3.14; don't install another). Pins chosen ≥ 2 weeks old; torch/torchvision come from the CPU-only index.
  Backend side: `order/HygieneIT` uses `support/FakeAiService` (JDK HttpServer that also fetches the presigned video).
- **Docker:** `backend/Dockerfile` (multi-stage, Temurin 21, runtime installs ffmpeg + DejaVu fonts + curl, non-root
  uid 10001, HEALTHCHECK on `/actuator/health/liveness`, `APP_VERSION` build arg). `ai-service/Dockerfile` runtime sets
  `LOG_FORMAT=json` (`app/logs.py`) and a Python HEALTHCHECK.
- **Production (M15), see `PRODUCTION.md`:** `infra/docker-compose.prod.yml` (caddy 2.11.4, backend, ai-service,
  backup; images `${IMAGE_REGISTRY}/vlk-{backend,ai,backup}:${IMAGE_TAG}`; env files `backend.env`/`ai.env`/`backup.env`
  from `infra/prod/*.env.example`; json-file log rotation), `infra/caddy/Caddyfile` (TLS, HSTS, 2 MB bodies, actuator
  except health → 404), `infra/backup/` (image on postgres:16.15-alpine3.24 + aws-cli + age: `backup-now`, `schedule`
  daily at `BACKUP_AT` UTC, `restore <url> [latest|key] [--overwrite]`, prunes by `BACKUP_RETENTION_DAYS`, optional age
  encryption + heartbeat URL; `restore-test.sh` = drill into a throwaway container; `test/roundtrip.sh` = CI test),
  `infra/deploy/deploy.sh` (on the VM: pre-deploy backup, pull with the new tag first, write `IMAGE_TAG` to `.env`,
  `up -d`, wait for `/actuator/health` UP, else roll back to the previous tag) and `validate.sh` (compose + Caddyfile).
  Verified in M15 by a full local rehearsal (local registry, RustFS for R2, Caddy on localhost): deploy, rollback of a
  broken release, missing tag, upgrade, backup + drill, Sentry events, rate limits, JSON logs.
- **CI/CD:** `.github/workflows/ci.yml` (PRs + called by deploy: backend verify with ffmpeg, OpenAPI drift check
  `backend/target/openapi.json` vs `web/openapi/openapi.json`, web checks on Node 22, ai-service `--target test`,
  shellcheck + `validate.sh` + backup roundtrip) and `deploy.yml` (main: tests → GHCR images tagged with the SHA →
  SSH deploy in environment `production` → public health check → `vercel@61.1.0` pull/build/deploy --prebuilt).
  Lint locally with `docker run --rm -v "$PWD:/repo" -w /repo rhysd/actionlint:1.7.7` and koalaman/shellcheck:v0.11.0
  (CI runs the same shellcheck image; the runner's apt shellcheck 0.9.0 has different rules). Actions are pinned to
  exact Node 24 releases (checkout v7.0.1, setup-java v5.7.0, setup-node v7.0.0, upload-artifact v7.0.1, buildx v4.4.1,
  login v4.6.0, build-push v7.4.0). The deploy jobs run only when the repository variable `DEPLOY_ENABLED` is `true`.
  To reproduce CI's Linux test run locally: `docker run --rm -v "$PWD:/src" -v /var/run/docker.sock:/var/run/docker.sock
  -w /src -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -e TESTCONTAINERS_RYUK_DISABLED=true
  eclipse-temurin:21.0.12_8-jdk-noble ./mvnw -B verify` (from `backend/`; install ffmpeg first for the clip tests).
  `gh` is installed and logged in (`gh run view <id> --log-failed`).
- **Browser end-to-end (manual, not in CI):** headless Chrome with `--use-fake-device-for-media-stream` via
  `puppeteer-core`, against throwaway containers. It verified pairing → recording → READY → upload → `UPLOADED`
  in M9. The Claude-in-Chrome extension wasn't connected in M6–M9.
- **Razorpay in tests:** `support/FakeRazorpay` (JDK HttpServer) stands in for the Razorpay API. `RazorpayPaymentIT` runs the
  real provider against it. Webhook/signature secrets used there are test-only constants.
- **springdoc** is pinned to 2.8.8. 2.8.9+ targets Boot 3.5 and fails on Boot 3.4 with an invalid swagger-ui path pattern.
- **Web:** `cd web && npm test && npm run typecheck && npm run lint && npm run build` (42 unit tests, Vitest + jsdom).
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
- Rate limits (per phone for login, per IP for public endpoints including guest checkout, M15) are in memory, so they
  only work on a single node.
- Unpaid online orders never expire. There is no way to settle PAY_LATER bills. Order placement has no
  idempotency key. Owners only see active orders, not history.
- Chefs cannot change the initial password their owner sets.
- Uploads have no size limit server-side (presigned PUT; the dashboard checks ≤ 5 MB JPG/PNG/WebP client-side). The
  listing doesn't filter by distance from the customer.
- The QR PDF uses Helvetica (Latin only); non-Latin restaurant names need an embedded Unicode font.
- `application-local.yml` hardcodes the local DB password, against the env-vars-only rule. Raised with
  the user; not changed yet.
- Admin can't create, disable or delete users, or reset passwords. DPDP data requests (export/erasure) are manual
  SQL. Nobody can change their own password (owners, chefs, admin).
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
  timelapse has no audio (recordings have none). Watch/download links last 10 min (`CLIP_WATCH_URL_TTL`, M11); the
  player refetches on error. FAILED clips aren't retried automatically: owners use "Retry" while raw footage exists
  (24 h), else requeue by inserting a job. Fonts on the overlay depend on the host (DejaVu in Docker); non-Latin names need a suitable font.
- Hygiene (M13): the model must be trained on a PPE dataset and validated with `scripts/eval.py` before public scores
  are turned on. Judgement is per frame, not per person. Results aren't re-analysed when the model changes (new clips
  use the new version). Disputes can be raised on the 10 worst clips shown (no full results list). The ai-service image
  is ~2.2 GB (CPU torch). Trend weeks are IST.
- Tips (M12): the platform's Razorpay account receives each tip payment and Route transfers the chef's share on capture
  (that's how Route works; it never settles to the platform's bank). Gateway fees are borne by the platform (chef gets
  100% by default). A failed setup after `POST /v2/accounts` leaves an orphaned linked account at Razorpay (resubmitting
  creates a new one). No refunds/reversals of tips. Linked-account business category/subcategory are config
  (`RAZORPAY_ROUTE_CATEGORY/SUBCATEGORY`); verify them against the account in test mode. Customers can tip a chef they
  follow on any of their orders at that restaurant (not only ones that chef cooked). Chefs can't see who tipped.
- Clips (M11): views count share-page visits only (not plays on the order page or by the owner). The visitor id comes
  from the browser, so a determined person can inflate views (the per-IP public-write limit, M15, only slows this). Storage
  keys (and so presigned URLs) contain the order UUID. A revoked link's preview image may stay cached by chat apps
  (`og.jpg` is cacheable for 10 min). Guests share with their order token, so the link survives only as long as the
  clip. Admins have no clip browser; they watch clips through hygiene disputes (`/api/admin/clips/{orderId}/playback`,
  audited).
- Web lockfile: the user's commit of 13 Oct 2026 dropped `@rolldown/binding-*` from `web/package-lock.json` (Vitest
  then fails with "Cannot find module './rolldown-binding…'"); M11 regenerated it. Check for this before committing.
- M10.5 frontend: owners' live orders show active orders plus unpaid bills only (no order history). Chef "current
  station" is the assignment, not where they last cooked. Every anonymous page load makes one 401
  `/api/session/refresh` call (session restore). The restaurant logo has no preview URL in the API (only `logoKey`).
- Billing (M14): changing a plan's price doesn't move existing Razorpay mandates (they keep the old price until the owner
  changes plan; a new gateway plan is created per price). The SAC code (998315) and invoice layout need an
  accountant's review; the supplier GSTIN/address come from env. No proration on upgrades (the gateway's update applies
  at once; Razorpay may charge the difference by its own rules). The plan's `analytics` feature isn't gated anywhere
  yet. Enforcement runs hourly, so suspension can lag grace expiry by up to an hour. Invoices aren't emailed. Credit
  notes and refunds of subscription charges aren't supported. The admin restaurant page writes an audit entry on every
  load (it issues a licence link each time).
- Production (M15): rate limits, login limits and the STOMP broker are in memory (one backend instance only). Sentry
  misses crashes during startup (Spring closes it first; the deploy health check and logs cover those). No
  Content-Security-Policy yet (Razorpay checkout + Sentry need a tuned policy). R2 has no India jurisdiction (APAC
  hint). Account deletion / DPDP data requests are handled by hand. Legal pages and the recording consent are drafts
  for a lawyer. Deploys restart the backend in place (a few seconds of 502 from Caddy; `lb_try_duration` rides out
  short gaps). The web's `/recording-consent` is ISR (1 h) and needs the API reachable at build time.
  `next` was bumped to 16.3.8 (security fixes: image-optimizer SSRF, cache poisoning).
- Web: ESLint is pinned to 9.39.x, which npm flags as end-of-life; move to ESLint 10 once `eslint-config-next` supports it.
