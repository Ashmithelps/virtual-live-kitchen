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
cp .env.example .env.local    # fill in API base URL, etc.
npm install
npm run dev
```

- Customer site: `http://localhost:3000`
- Restaurant dashboard: `http://localhost:3000/restaurant`
- Kitchen screen: `http://localhost:3000/kitchen`

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

# Web
cd web && npm test

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
