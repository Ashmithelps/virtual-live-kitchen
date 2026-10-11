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
- Kitchen display (chefs and owners, tablet/landscape): `http://localhost:3000/kitchen`. To install it as an app
  the site must be served over HTTPS (or opened on the same machine via localhost).
- Swagger UI for the backend: `http://localhost:8080/swagger-ui.html`

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
