# Virtual Live Kitchen

A multi-restaurant platform where customers can order food and receive a short video clip of their
order being prepared in the kitchen.

---

## Prerequisites

| Tool | Version |
|------|---------|
| Java | 21+ |
| Maven | 3.9+ |
| Node.js | 20+ (LTS) |
| Docker + Docker Compose | v2+ |
| FFmpeg (with ffprobe, libx264, libvpx) | 6+ (`brew install ffmpeg`); bundled in the backend Docker image |
| Python | 3.11+ (ai-service, later milestone) |

---

## Running Locally

### 1. Start infrastructure (Postgres + MinIO)

```bash
cd infra
docker compose up -d
```

This starts:
- **Postgres 16** on `localhost:5432`
- **MinIO** on `localhost:9000` (console at `localhost:9001`)

Default credentials are in `infra/.env.example` — copy to `infra/.env` before starting.

### 2. Backend (Spring Boot)

```bash
cd backend
cp .env.example .env          # set JWT_SECRET (openssl rand -base64 48), ADMIN_PHONE, ADMIN_PASSWORD
set -a; source .env; set +a   # export the variables into this shell
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Maven uses `JAVA_HOME`, which must point at JDK 21 (`/usr/libexec/java_home -v 21`, or the Homebrew
`openjdk@21` path). With JDK 17 the build fails with "release version 21 not supported".

On first start the backend seeds one `ADMIN` user from `ADMIN_PHONE` / `ADMIN_PASSWORD` (skipped if
`ADMIN_PHONE` is empty; never overwrites an existing account).

API available at `http://localhost:8080`.

Flyway migrations run automatically on startup.

### 3. Web (Next.js)

```bash
cd web
cp .env.example .env.local    # NEXT_PUBLIC_API_URL / API_URL point at the backend
npm install
npm run dev
```

- Customer site: `http://localhost:3000`
- Owner dashboard: `http://localhost:3000/dashboard`. Owners sign up at `/register/owner` and see "Pending approval"
  until an admin approves them.
- Admin: `http://localhost:3000/admin`. Chef settings (recording consent): `http://localhost:3000/chef/settings`.
- Kitchen display (chefs and owners, tablet/landscape): `http://localhost:3000/kitchen`. To install it as an app
  the site must be served over HTTPS (or opened on the same machine via localhost).
- Swagger UI for the backend: `http://localhost:8080/swagger-ui.html`

### Trying the whole product (dev seed)

The `dev-seed` profile creates a ready-made demo the first time it starts (and nothing on later starts): an admin, an
owner with the approved restaurant **Dev Spice Kitchen** (2 categories, 8 dishes, tables T1–T3, stations Tandoor and
Curry), two chefs who have accepted recording consent, and a customer. All of them use one password,
`DEV_SEED_PASSWORD`. **Never enable this profile on a shared or production database.** It refuses to start next to a
`prod`, `production` or `staging` profile.

```bash
# backend/.env: set DEV_SEED_PASSWORD (12+ characters)
cd backend && set -a; source .env; set +a
./mvnw spring-boot:run -Dspring-boot.run.profiles=local,dev-seed   # the log prints every login and the table QR links

# web/.env.local: the same DEV_SEED_PASSWORD, for the header's role switcher
cd web && npm run dev
```

| Role | Phone |
|------|-------|
| Admin | +919000000001 |
| Owner | +919000000002 |
| Chef (Tandoor) | +919000000003 |
| Chef (Curry) | +919000000004 |
| Customer | +919000000005 |

With `npm run dev`, the header shows a **DEV · Log in as…** menu for one-click switching between these accounts.
Production builds don't have it.

After changing backend endpoints or DTOs, regenerate the typed API client:

```bash
cd web
npm run api:update   # pulls /v3/api-docs from the running backend (or backend/target/openapi.json) and regenerates lib/api/schema.ts
```

### Payments (Razorpay test mode)

The backend uses a mock gateway by default (`PAYMENT_PROVIDER=mock`). To take real test-mode payments:

1. In the Razorpay Dashboard, switch to **Test Mode** → *Account & Settings* → *API Keys* → generate a key.
2. Start a public tunnel to the backend so Razorpay can reach the webhook (pick one):
   ```bash
   cloudflared tunnel --url http://localhost:8080     # prints https://<random>.trycloudflare.com
   # or
   ngrok http 8080                                    # prints https://<random>.ngrok-free.app
   ```
3. Dashboard (Test Mode) → *Account & Settings* → *Webhooks* → *Add New Webhook*:
   URL `https://<tunnel-host>/api/payments/razorpay/webhook`, a secret of your choice, and the events
   `payment.captured`, `payment.failed`, `refund.processed`.
4. Run the backend with:
   ```bash
   export PAYMENT_PROVIDER=razorpay RAZORPAY_KEY_ID=rzp_test_... RAZORPAY_KEY_SECRET=... RAZORPAY_WEBHOOK_SECRET=<the secret from step 3>
   ```
   The web app needs no Razorpay settings: the backend hands the browser the public key id with each checkout.
5. Pay with Razorpay's test instruments, e.g. card `4111 1111 1111 1111` (any future expiry, any CVV) or UPI
   `success@razorpay` / `failure@razorpay`.

Tunnel URLs change on every restart (unless you reserve one), so update the webhook URL when they do.
Webhook deliveries and retries are listed in the Dashboard under the webhook's details.

### Station recorder phones (`/station`)

Each prep station has a cheap Android phone running `https://<web host>/station` (Chrome, installable PWA).

1. Owner: kitchen display (`/kitchen`, log in as the owner) → **Stations** → **Pair phone** → a QR appears
   (one-time, valid 10 minutes).
2. Station phone: scan the QR with the camera app → it opens `/station` and pairs (no login on the phone).
3. Tap **Start recording**, allow the camera, then mount the phone on the charger. It records the rear
   camera (no audio) in 60-second chunks and keeps the last 45 minutes on the phone.
4. When an order prepared at that station is marked READY, the phone uploads only the footage from
   "Start" − 10 s to "Ready" + 5 s.

Camera access needs a secure origin: `http://localhost` works on the dev machine itself; a phone on the
LAN needs HTTPS (a tunnel such as `cloudflared tunnel --url http://localhost:3000`, or a deployment). The
backend must also be reachable from the phone (set `NEXT_PUBLIC_API_URL` and `CORS_ALLOWED_ORIGINS`).
The backend sets a CORS rule on the storage buckets at startup so phones can upload directly; on real S3
without `S3_CREATE_BUCKETS`, configure that rule yourself.

### Kitchen clips (processing)

After the phone uploads, a background job renders the customer's clip with FFmpeg:

1. Joins the raw chunks and cuts exactly the order's window.
2. If the window is longer than 50 s, it becomes a 45 s timelapse: everything but the last 5 s (plating) is sped up to 40 s.
3. Burns in the restaurant name and logo, "Order #N", the chef's display name and the date, plus a platform watermark.
4. Encodes 720p H.264 MP4 (faststart) and a JPG thumbnail.
5. Uploads to `clips/{restaurantId}/{orderId}.mp4` in the clips bucket.

The raw chunks are then deleted. The order page shows the video when it's READY (pushed live). Jobs live
in the Postgres `jobs` table and retry with backoff (`JOB_MAX_ATTEMPTS`). `JOB_WORKER_THREADS` sets how many
run in this instance; set it to `0` on API nodes once workers run separately. Clips are deleted after
`CLIP_RETENTION` (default `30d`). Unprocessed raw footage is deleted after `CLIP_RAW_RETENTION` (`24h`).

Running from source needs `ffmpeg` and `ffprobe` on `PATH` (or `FFMPEG_PATH` / `FFPROBE_PATH`). The Docker image
includes them:

```bash
cd backend
docker build -t virtual-kitchen-backend .
docker run --rm --env-file .env -p 8080:8080 virtual-kitchen-backend
```

### 4. AI Service (later milestone)

```bash
cd ai-service
cp .env.example .env
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8001
```

API docs at `http://localhost:8001/docs`.

---

## Running Tests

```bash
# Backend: *Test = unit tests (surefire), *IT = integration tests on Testcontainers Postgres (failsafe).
# Docker must be running.
cd backend && ./mvnw verify

# Web: unit tests, typecheck, lint, production build
cd web && npm test && npm run typecheck && npm run lint && npm run build

# Web end-to-end (Playwright, uses your installed Google Chrome). Needs the stack running with the dev-seed
# profile; E2E_BASE_URL / E2E_API_URL default to http://localhost:3000 / http://localhost:8080.
cd web && DEV_SEED_PASSWORD=... npm run test:e2e

# AI service
cd ai-service && pytest
```

---

## Environment Variables

Each sub-project ships a `.env.example`. Copy it and fill in the values:

| File | Used by |
|------|---------|
| `infra/.env.example` | Docker Compose (Postgres password, MinIO keys) |
| `backend/.env.example` | Spring Boot (DB URL, JWT secret, MinIO keys, etc.) |
| `web/.env.example` | Next.js (`NEXT_PUBLIC_API_URL`, etc.) |
| `ai-service/.env.example` | FastAPI (model path, S3 keys, etc.) |

---

## Project Structure

```
virtual-live-kitchen/
├── backend/        Spring Boot 3, Java 21 — REST API
├── web/            Next.js App Router — customer + restaurant + kitchen UIs
├── ai-service/     Python FastAPI — hygiene scoring (later milestone)
└── infra/          docker-compose.yml
```
