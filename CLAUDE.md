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
| 9 | Not yet defined | — | next: V7 |

`ai-service/` contains only `.env.example`. The restaurant owner dashboard (`web/app/(restaurant)`) is still empty.

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
  `media/` (upload purposes, object-key validation).
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
- `admin/`: approve owners (`/api/admin/owners`), approve or suspend restaurants (`/api/admin/restaurants`).
- `realtime/`: STOMP over SockJS at `/ws` with an in-memory broker. `StompAuthInterceptor` authenticates CONNECT
  (bearer JWT, or a guest's `X-Guest-Order-Token`), checks every SUBSCRIBE with `RealtimeAccess`, and refuses all
  client SENDs. `OrderEventBroadcaster` publishes `{type: "ORDER_UPDATED", order}` after commit to
  `/topic/order/{id}` (that order's customer) and `/topic/restaurant/{id}/orders` (owner + chefs; only orders the
  restaurant can see).
- `common/openapi/OpenApiConfig`: springdoc spec at `/v3/api-docs` (OpenAPI 3.0) and Swagger UI at `/swagger-ui.html`.
  Every field of a response-only schema is marked required; nullable fields carry `@Schema(nullable = true)`.

### Web map (`web/`, Next.js 16 App Router, TypeScript, Tailwind 4, TanStack Query 5)

- Pages live in `app/(customer)/`: `/` (city + filters), `/r/[slug]` (menu, veg filter, cart bar), `/t/[qrToken]`
  (saves the table session, then opens `/r/[slug]` in dine-in mode), `/checkout?r=<slug>`, `/orders/[id]`
  (5 s polling, mock payment panel, cancel, clip placeholder), `/orders`, `/login` (log in + create account).
- `lib/api/`: `schema.ts` is generated by `npm run api:gen` from `openapi/openapi.json`; never edit it by hand.
  `client.ts` is the `openapi-fetch` client with a bearer-token middleware; `unwrap()` turns errors into `ApiError`.
- Auth: `app/api/session/*` route handlers keep the refresh token in an httpOnly cookie (`vk_rt`, path
  `/api/session`) and give the page only the access token, held in memory (`lib/auth/token-store.ts`).
  Refreshes are serialised across tabs with the Web Locks API, because the backend revokes reused refresh tokens.
- Per-device state goes through `lib/storage.ts` (localStorage + `useSyncExternalStore`): carts per restaurant
  (`lib/cart`), table sessions (`lib/table-session.ts`) and guest order tokens (`lib/guest-orders.ts`).
- `app/(kitchen)/kitchen`: tablet kitchen display. Flow: login → (chef: consent gate, pick station | owner: pick
  restaurant) → `components/kds/board.tsx`, with New / Preparing / Ready columns, one-tap actions, chime + flash
  on new orders, age colours, offline banner and wake lock. Board rules live in `lib/kds/board.ts`, which has unit tests.
- Two session stores (`lib/auth/token-store.ts`): `customerSession` (`/api/session`, cookie `vk_rt`) and
  `kitchenSession` (`/api/kitchen/session`, cookie `vk_kitchen_rt`, CHEF/RESTAURANT_OWNER only), with matching
  `api` / `kitchenApi` clients. Each route group's layout wraps its pages in `AuthProvider` with its store.
- `lib/realtime/use-stomp.ts`: the only WebSocket client. Exponential reconnect (1 s → 30 s), fresh token per
  attempt, and status `connecting | connected | offline | denied`. Callers poll REST while it isn't `connected`.
- PWA: `public/kitchen.webmanifest` (scope `/kitchen`, fullscreen, landscape), `public/icons/kitchen-*`, and
  `public/kitchen-sw.js` (registered in production builds only; caches the shell and static assets, never `/api/*`).
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
- **Live updates:** after changing an order, call `OrderEvents.changed(order)` inside the transaction. New topics
  must be added to both `realtime/Topics` and `RealtimeAccess`; anything unlisted is refused.
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
- **Testcontainers** is pinned to 1.21.4, which Docker Desktop 29 requires. Last full run: 340 unit + 120 integration tests passing.
- **Razorpay in tests:** `support/FakeRazorpay` (JDK HttpServer) stands in for the Razorpay API. `RazorpayPaymentIT` runs the
  real provider against it. Webhook/signature secrets used there are test-only constants.
- **springdoc** is pinned to 2.8.8. 2.8.9+ targets Boot 3.5 and fails on Boot 3.4 with an invalid swagger-ui path pattern.
- **Web:** `cd web && npm test && npm run typecheck && npm run lint && npm run build` (24 unit tests, Vitest + jsdom).
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
- Uploads have no size limit (presigned PUT). The listing doesn't filter by distance from the customer.
- The QR PDF uses Helvetica (Latin only); non-Latin restaurant names need an embedded Unicode font.
- `application-local.yml` hardcodes the local DB password, against the env-vars-only rule. Raised with
  the user; not changed yet.
- Station device tokens are issued, but device authentication and recording come in a later milestone.
- Web: delivery addresses need lat/lng (browser geolocation or typed in; no geocoding yet). Payment uses a
  test-mode panel against the mock provider. Order updates poll every 5 s until Milestone 7 adds WebSockets.
- Payments: counter payments are not refunded automatically (staff return cash in person). An amount mismatch
  on capture is logged as an error and left FAILED for manual review. There are no partial refunds and no
  payout or settlement reports yet. The owner's "Paid at counter" lives on the KDS until an owner dashboard exists.
- Realtime: subscriptions are authorised at SUBSCRIBE time only. A chef removed mid-shift keeps receiving events
  until their socket reconnects. The in-memory broker only reaches clients of one backend instance; scaling out
  needs a broker relay (e.g. RabbitMQ).
- KDS PWA: installing and the service worker need HTTPS (or localhost), so a tablet on LAN `http://192.168…` can
  run the board but can't install it. Owners can't "Start" an order from the KDS (the backend needs a chef + station).
- Web: ESLint is pinned to 9.39.x, which npm flags as end-of-life; move to ESLint 10 once `eslint-config-next` supports it.
