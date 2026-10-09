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
