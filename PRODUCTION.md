# Production checklist

How Virtual Live Kitchen runs in production, and everything to do (once) before going live. Work through the
sections in order. Items marked **☐** are things you do outside this repository. The final go-live list is at the
end.

```
 customers / owners / chefs / station phones
        │ https://example.in                      │ https://api.example.in  (REST + WebSocket /ws)
        ▼                                         ▼
  Vercel (web/, region bom1) ── server calls ──▶ VM in an Indian region: Caddy (TLS) → backend → ai-service
                                                  │  backup job (daily pg_dump → R2)
                                                  ├──▶ Managed Postgres (same region)
                                                  └──▶ Cloudflare R2 (media, clips, db-backups)
  Browsers PUT uploads and GET clips straight from R2 with presigned URLs.
```

| Piece | Where | Defined in |
|---|---|---|
| Web app | Vercel | `web/`, `web/vercel.json` |
| Backend (Spring Boot + FFmpeg) | VM, container `backend` | `backend/Dockerfile`, profile `prod` (`application-prod.yml`) |
| AI hygiene service | VM, container `ai-service` (internal only) | `ai-service/Dockerfile` |
| HTTPS + reverse proxy | VM, container `caddy` | `infra/caddy/Caddyfile` |
| Daily DB backups | VM, container `backup` | `infra/backup/` |
| Stack | VM, `/opt/vlk` | `infra/docker-compose.prod.yml`, `infra/prod/*.env.example` |
| CI/CD | GitHub Actions | `.github/workflows/ci.yml`, `deploy.yml`, `infra/deploy/deploy.sh` |

---

## 1. Choose the providers

### VM (Indian region)

Any Linux VM with Docker works. Suggested size: **4 vCPU, 8 GB RAM, 80 GB disk**. FFmpeg renders clips on the CPU,
and the AI service needs about 2 GB while it analyses a clip. Pick the same region as the database.

| Provider | Region | Notes |
|---|---|---|
| AWS EC2 | `ap-south-1` Mumbai (`ap-south-2` Hyderabad) | Pairs with RDS; use an Elastic IP |
| Google Compute Engine | `asia-south1` Mumbai (`asia-south2` Delhi) | Pairs with Cloud SQL; reserve a static IP |
| Azure VM | Central India (Pune) | Pairs with Azure Database for PostgreSQL |
| DigitalOcean Droplet | `BLR1` Bengaluru | Simplest; pairs with DO Managed PostgreSQL in BLR1 |

### Managed Postgres (16)

| Option | Backups / PITR | Private networking with the VM | Notes |
|---|---|---|---|
| AWS RDS for PostgreSQL (ap-south-1) | Automated snapshots + point-in-time restore | VPC security group | Most mature; Multi-AZ optional |
| Google Cloud SQL (asia-south1) | Automated + PITR | Private IP | Good if the VM is on GCP |
| Azure Database for PostgreSQL Flexible Server (Central India) | Automated + PITR | VNet | Good if the VM is on Azure |
| DigitalOcean Managed PostgreSQL (BLR1) | Daily + PITR (7 days) | VPC + "trusted sources" | Cheapest managed option in India |
| Neon / Supabase (AWS Mumbai) | Provider-managed, branching | Public endpoint + TLS | Serverless; watch connection limits (pool size 10 per backend) |

Whichever you choose:
- ☐ Turn on the provider's automated backups and point-in-time recovery. The daily `pg_dump` (section 6) is a
  second, provider-independent copy, not a replacement.
- ☐ Allow connections only from the VM (private network or IP allow-list), and require TLS.
- ☐ Create the database and two roles (as the admin user):
  ```sql
  CREATE DATABASE virtualkitchen;
  CREATE ROLE vlk_app LOGIN PASSWORD '<openssl rand -hex 24>';
  ALTER DATABASE virtualkitchen OWNER TO vlk_app;          -- Flyway creates and migrates the schema
  CREATE ROLE vlk_backup LOGIN PASSWORD '<another one>';
  GRANT pg_read_all_data TO vlk_backup;                     -- read-only, for pg_dump
  ```
- ☐ Keep `infra/backup/Dockerfile`'s `PG_IMAGE` major version equal to (or newer than) the server's.

---

## 2. Domain and DNS

- ☐ Register the domain (e.g. `example.in`).
- ☐ DNS records:

  | Name | Type | Value | Proxy (Cloudflare) |
  |---|---|---|---|
  | `example.in`, `www` | as Vercel shows (A `76.76.21.21` / CNAME `cname.vercel-dns.com`) | | DNS only |
  | `api` | A (and AAAA if the VM has IPv6) | VM's static IP | **DNS only** (grey cloud) |

  Keep `api` unproxied. Caddy gets its own Let's Encrypt certificate, and the backend's per-IP rate limits need the
  visitor's real IP. Behind Cloudflare's proxy, every visitor would appear as a Cloudflare address. Optionally add
  a CAA record allowing `letsencrypt.org` (plus Vercel's CA for the apex).
- ☐ Decide the canonical web origin (`https://example.in` and/or `https://www.example.in`). Every origin users can
  open must be in the backend's `CORS_ALLOWED_ORIGINS` **and** in the R2 CORS rules (section 4).

---

## 3. The VM

- ☐ Ubuntu 24.04 LTS. Create a `deploy` user with SSH-key login only (`PasswordAuthentication no`,
  `PermitRootLogin no`), and enable `unattended-upgrades`.
- ☐ Install Docker Engine + the compose plugin, then `usermod -aG docker deploy`.
- ☐ Firewall: allow 22/tcp (ideally only from your IP), 80/tcp, 443/tcp and 443/udp (HTTP/3). Nothing else; the
  backend and AI service publish no ports.
- ☐ Create the stack directory:
  ```bash
  sudo install -d -o deploy -g deploy -m 750 /opt/vlk /opt/vlk/models
  cd /opt/vlk
  # copy from the repo: infra/prod/compose.env.example → .env, backend.env.example → backend.env,
  #                     ai.env.example → ai.env, backup.env.example → backup.env   — then fill them in
  chmod 600 .env backend.env ai.env backup.env
  ```
  CI copies `docker-compose.prod.yml`, `Caddyfile` and `deploy.sh` there on every deploy.
- ☐ Fill in `backend.env`. Generate secrets with `openssl rand -hex 32` (JWT: `openssl rand -base64 48`). The
  backend refuses to start under the `prod` profile (`ProductionGuard`) if any of these hold:
  - the payment provider is `mock`, or the Razorpay key is a test key;
  - the webhook secret is empty;
  - a CORS origin or `WEB_BASE_URL` isn't `https://`;
  - storage isn't `https://`;
  - `BACKEND_PROXY_SECRET` is shorter than 32 characters;
  - rate limiting is off;
  - `dev-seed` is active.

  For a staging rehearsal with test keys, set `PRODUCTION_GUARD_ALLOW=razorpay-test-keys`.
- ☐ AI hygiene scoring (optional; off until you have validated PPE weights; see `ai-service/README.md`):
  - put `hygiene.pt` and `class_map.yaml` in `/opt/vlk/models`;
  - set `HYGIENE_ENABLED=true` and the same `AI_SERVICE_API_KEY` in `backend.env` and `ai.env`;
  - set `ALLOWED_VIDEO_HOSTS` to the R2 host.

  Without weights the service runs and answers 503, and no clips are scored.

---

## 4. Cloudflare R2

- ☐ Create three buckets. Location hint: **Asia-Pacific**. R2 has no India jurisdiction; mention storage outside
  India in the privacy policy, which already does.
  - `vlk-media`: restaurant logos, dish and chef photos, FSSAI licences.
  - `vlk-clips`: raw footage and rendered clips.
  - `vlk-db-backups`: database dumps.

  Keep all three **private**: no public bucket and no r2.dev URL. The app hands out presigned URLs.
- ☐ API tokens (R2 → Manage API tokens), each scoped to specific buckets:
  - backend: *Object Read & Write* on `vlk-media` + `vlk-clips` → `S3_ACCESS_KEY` / `S3_SECRET_KEY`;
  - backup: *Object Read & Write* on `vlk-db-backups` only → `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` in
    `backup.env`.

  The S3 endpoint is `https://<account-id>.r2.cloudflarestorage.com`. Use `S3_REGION=auto` and
  `S3_CREATE_BUCKETS=false`, since tokens can't create buckets or set CORS.
- ☐ **CORS** on `vlk-media` and `vlk-clips`. Browsers PUT uploads directly: station phones upload footage, owners
  upload images. Paste this under Settings → CORS policy, with your real origins:
  ```json
  [
    {
      "AllowedOrigins": ["https://example.in", "https://www.example.in"],
      "AllowedMethods": ["PUT", "GET", "HEAD"],
      "AllowedHeaders": ["*"],
      "ExposeHeaders": ["ETag"],
      "MaxAgeSeconds": 3600
    }
  ]
  ```
  Without it, uploads fail with a CORS error in the browser console.
- ☐ Lifecycle rules (safety nets; the app and the backup job already delete on schedule):
  - `vlk-clips`, prefix `raw/`: delete after 2 days;
  - `vlk-db-backups`: delete after 35 days.

---

## 5. Razorpay (live)

- ☐ Complete account activation (KYC and website review). Razorpay's review looks for terms, privacy, cancellation and
  refund rules, and contact details on the site. `/terms` covers refunds and cancellations, and `/privacy` has the
  contact and grievance details; check them against Razorpay's current checklist.
- ☐ Ask Razorpay to enable **Route** (chef tips go to linked accounts) and **Subscriptions** (restaurant billing) on
  the live account.
- ☐ Live Mode → Account & Settings → API Keys → `RAZORPAY_KEY_ID` (`rzp_live_…`) / `RAZORPAY_KEY_SECRET`.
- ☐ Live Mode → Webhooks → add `https://api.example.in/api/payments/razorpay/webhook` with a new secret
  (→ `RAZORPAY_WEBHOOK_SECRET`) and these events:
  - Payments: `payment.captured`, `payment.failed`, `refund.processed`.
  - Route: `transfer.processed`, `transfer.failed`, and the `product.route.*` activation events.
  - Subscriptions: `subscription.authenticated`, `subscription.activated`, `subscription.charged`,
    `subscription.pending`, `subscription.halted`, `subscription.cancelled`, `subscription.completed`.
- ☐ Verify `RAZORPAY_ROUTE_CATEGORY` / `RAZORPAY_ROUTE_SUBCATEGORY` (default `food` / `restaurant`) against
  Razorpay's list for linked accounts.
- ☐ Fill in the GST invoice details (`BILLING_SUPPLIER_*`) and confirm the SAC code (`BILLING_SAC_CODE`, default
  998315) with your accountant.
- ☐ After launch, make one real ₹1-ish food order and refund it; tip a chef; take one subscription payment. Check
  each webhook delivery shows **200** in the Razorpay dashboard.

---

## 6. Backups and restore

- **Automatic:** the `backup` container runs `pg_dump` every day at `BACKUP_AT` (UTC; default 21:00 = 02:30 IST),
  checks that the dump lists, optionally encrypts it with age, uploads `vlk-<timestamp>.dump` to R2, and deletes
  dumps older than `BACKUP_RETENTION_DAYS` (30). `deploy.sh` also takes one before every deploy.
- ☐ **Monitoring:** create a free check at healthchecks.io (daily, 2 h grace) and set `BACKUP_HEARTBEAT_URL`. It
  emails you when a backup fails or doesn't run.
- ☐ **Encryption (recommended):**
  1. Run `age-keygen -o vlk-backup.key` **on your laptop**.
  2. Put the public key (`age1…`) in `BACKUP_AGE_RECIPIENT`.
  3. Store `vlk-backup.key` in your password manager, **not** on the server. Without it, encrypted backups can't
     be restored.
- **Manual backup:** `cd /opt/vlk && docker compose -f docker-compose.prod.yml run --rm backup backup-now`
- ☐ **Restore drill (do it now, then monthly).** It restores into a throwaway local container and never touches
  production:
  ```bash
  # on your laptop, with Docker; backup.env = a copy of the server's (read access to the bucket is enough)
  docker login ghcr.io                                  # once; or: docker build -t vlk-backup infra/backup
  BACKUP_IMAGE=ghcr.io/<owner>/vlk-backup:main \
  BACKUP_AGE_IDENTITY_FILE=~/vlk-backup.key \
    infra/backup/restore-test.sh path/to/backup.env latest
  ```
  It prints the Flyway version and row counts, then deletes the container. Write down how long it took.
- **Real restore** (disaster), into a **new** empty database (fresh instance or new database name):
  ```bash
  docker compose -f docker-compose.prod.yml run --rm -v ~/vlk-backup.key:/k:ro -e BACKUP_AGE_IDENTITY_FILE=/k \
    backup restore "postgresql://vlk_app:<pw>@<new-host>:5432/virtualkitchen?sslmode=require" latest
  ```
  Then point `DB_URL` at it and run `./deploy.sh <current tag>`. The script refuses a non-empty target unless you add
  `--overwrite`. Prefer the provider's point-in-time restore when you only need to undo the last few hours.

---

## 7. Error reporting (Sentry)

- ☐ Create two Sentry projects: **Spring Boot** (backend) and **Next.js** (web). Pick the EU or US data region;
  either way the data leaves India, which the privacy policy discloses.
- ☐ Backend: put `SENTRY_DSN` and `SENTRY_ENVIRONMENT=production` in `backend.env`. ERROR log lines and unhandled
  exceptions become events, with no cookies, IPs or request bodies (`send-default-pii: false`). The release is the
  git SHA.
- ☐ Web (Vercel env vars):
  - `NEXT_PUBLIC_SENTRY_DSN` and `SENTRY_DSN`;
  - `NEXT_PUBLIC_SENTRY_ENVIRONMENT=production` and `SENTRY_ENVIRONMENT=production`;
  - for readable stack traces: `SENTRY_ORG`, `SENTRY_PROJECT` and `SENTRY_AUTH_TOKEN` (an org token with
    `project:releases`).
- ☐ Alerts: email or Slack on new issues, plus a spike alert.
- Note: crashes **during startup** aren't sent (Spring closes Sentry first). The deploy health check catches those,
  and their logs stay in `docker compose logs backend`.

---

## 8. Vercel (web)

- ☐ Import the GitHub repo. Set **Root Directory** to `web`, Framework to Next.js, Node.js 22. `vercel.json` pins the
  functions to Mumbai (`bom1`) and turns off Git deploys of `main`: production deploys only from the Deploy workflow,
  after tests pass. Pull requests still get preview deployments.
- ☐ Production environment variables:

  | Variable | Value |
  |---|---|
  | `NEXT_PUBLIC_API_URL`, `API_URL` | `https://api.example.in` |
  | `SITE_URL` | `https://example.in` (must equal the backend's `WEB_BASE_URL`) |
  | `BACKEND_PROXY_SECRET` | same value as in `backend.env` |
  | Sentry variables | see section 7 |
  | `LEGAL_*`, `GRIEVANCE_OFFICER_*` | see section 10 |

  Never set `DEV_SEED_PASSWORD` on Vercel (the dev login route answers 404 in production anyway).
- ☐ Add the domains (`example.in`, `www.example.in`) and pick one as primary (redirect the other).

---

## 9. CI/CD (GitHub Actions)

- **`ci.yml`** runs on every pull request:
  - backend tests (Testcontainers);
  - an API-contract drift check;
  - web tests, typecheck, lint and build;
  - AI service tests;
  - shellcheck, compose/Caddy validation and the backup roundtrip.
- **`deploy.yml`** runs on push to `main`:
  1. the same tests;
  2. builds and pushes `ghcr.io/<owner>/vlk-{backend,ai,backup}:<sha>` (and `:main`);
  3. copies the stack files to the VM over SSH and runs `deploy.sh <sha>`: pre-deploy backup → pull → restart →
     wait for `/actuator/health` UP, or roll back to the previous tag automatically;
  4. checks `https://api…/actuator/health` from outside;
  5. deploys the web app to Vercel.

Setup:
- ☐ Settings → Environments → **production**. Optionally add yourself as a required reviewer, so every deploy waits
  for your click.
- ☐ Environment secrets:

  | Secret | Value |
  |---|---|
  | `DEPLOY_HOST` | VM IP or `api.example.in` |
  | `DEPLOY_USER` | `deploy` |
  | `DEPLOY_SSH_KEY` | private key of a key pair made for CI only (`ssh-keygen -t ed25519 -C github-deploy`); public half in `~deploy/.ssh/authorized_keys` |
  | `DEPLOY_KNOWN_HOSTS` | output of `ssh-keyscan -t ed25519 <host>`, **checked against the server's fingerprint** |
  | `VERCEL_TOKEN`, `VERCEL_ORG_ID`, `VERCEL_PROJECT_ID` | Vercel → Account → Tokens; IDs from `vercel link` (`web/.vercel/project.json`) |

- ☐ Environment variable (not secret): `API_DOMAIN` = `api.example.in`.
- ☐ Branch protection on `main`: require the CI checks to pass and require pull requests.
- The VM pulls images with the job's short-lived `GITHUB_TOKEN`, so no long-lived registry credential stays on the
  server. Packages stay private.
- **Rollback:**
  - re-run an older successful Deploy run, or on the VM run `/opt/vlk/deploy.sh <older sha>`;
  - `.deploy-history` lists deployed tags.

  Migrations only go forward. Write them so the previous release still works on the new schema (expand → deploy →
  contract later). If a migration must be undone, add a new migration, or restore the pre-deploy backup.

---

## 10. Legal, privacy and compliance

The pages `/privacy`, `/terms` and `/recording-consent` exist and are linked from every page footer and the sign-up
forms. Their text is a **draft**: a yellow banner says so until `LEGAL_PAGES_REVIEWED=true`.

- ☐ Fill in the Vercel variables:
  - `LEGAL_ENTITY_NAME`, `LEGAL_ADDRESS`, `LEGAL_SUPPORT_EMAIL`, `LEGAL_JURISDICTION_CITY`, `LEGAL_EFFECTIVE_DATE`;
  - the DPDP grievance officer: `GRIEVANCE_OFFICER_NAME`, `GRIEVANCE_OFFICER_EMAIL`, `GRIEVANCE_OFFICER_PHONE`.

  Missing values show as highlighted placeholders.
- ☐ Have a lawyer review the privacy policy (`web/app/(customer)/privacy/page.tsx`) and terms
  (`web/app/(customer)/terms/page.tsx`) against the DPDP Act 2023 and Rules, the IT Act / IT Rules 2021 (grievance
  officer), consumer-protection e-commerce rules, and your Razorpay agreement. Then set `LEGAL_PAGES_REVIEWED=true`.
- ☐ **Chef recording consent:** the text in `backend/src/main/resources/consent/recording-consent-v1.txt` is a
  DRAFT.
  - After legal review, save the approved text as a **new** file `recording-consent-v2.txt` and set
    `app.chef-consent.current-version: v2`. Every chef then re-accepts; old versions must stay, because acceptances
    store their hash.
  - `/recording-consent` shows the current version publicly.
  - Restaurants must also put up a visible notice at recorded stations.
- ☐ **DPDP grievance contact:**
  - monitor the grievance email;
  - answer requests (access, correction, erasure, nomination) within the time the DPDP Rules set;
  - keep a log of requests.

  Account deletion is a manual admin task today (see the backlog in `CLAUDE.md`).
- ☐ **Breach response:** write a one-page plan covering:
  - who decides;
  - how to notify the Data Protection Board and affected users within the DPDP Rules' timelines (confirm the current
    deadlines with counsel);
  - CERT-In's 6-hour reporting of cyber incidents.
- ☐ GST registration (for the invoices you issue to restaurants) and your own FSSAI/food-business obligations, if
  any, confirmed with your CA.

---

## 11. First deploy and first admin

1. ☐ Sections 1–9 done. On the VM: `/opt/vlk/.env` with `API_DOMAIN`, `ACME_EMAIL`, and `IMAGE_REGISTRY` =
   `ghcr.io/<owner, lowercase>`.
2. ☐ In `backend.env`, set `ADMIN_PHONE` and a strong `ADMIN_PASSWORD`, saved in your password manager.
3. ☐ Push to `main`, or Actions → Deploy → Run workflow. Watch the run.
   - Caddy obtains the certificate on the first request to `https://api.example.in`.
   - Flyway creates the schema on first start.
4. ☐ Log in at `https://example.in/login` with the admin phone. Then **remove `ADMIN_PASSWORD` from `backend.env`**:
   the seeder only creates the admin when the phone doesn't exist yet. Restart with `./deploy.sh <current tag>`.
5. ☐ Admin → Billing: check plan prices, trial days and grace days.
6. ☐ Register your first restaurant owner from `/register/owner`, approve them, and walk through the owner, chef,
   station and customer flows on real phones (HTTPS is required for the station camera).

---

## 12. Operating it

| Task | How |
|---|---|
| Health | `https://api.example.in/actuator/health` (add it to an uptime monitor, e.g. UptimeRobot/Better Stack, 1-min interval) |
| Logs | `cd /opt/vlk && docker compose -f docker-compose.prod.yml logs -f backend` (JSON lines; also `caddy`, `ai-service`, `backup`). Rotated at 5 × 20 MB per container. |
| Deploy / roll back | merge to `main` / `./deploy.sh <sha>` |
| Backup now | `docker compose -f docker-compose.prod.yml run --rm backup backup-now` |
| Restore drill | monthly, `infra/backup/restore-test.sh` (section 6) |
| Rotate `BACKEND_PROXY_SECRET` | set the new value in Vercel **and** `backend.env`, then deploy both. Between the two steps, logins through the web share one IP budget (30/min). |
| Rotate `JWT_SECRET` | logs everybody out; only after a suspected leak |
| Rotate the Razorpay webhook secret | add the new secret in Razorpay, update `backend.env`, redeploy |
| Disk | `docker system df`; `deploy.sh` prunes dangling images |

Limits of the single-VM setup (see `CLAUDE.md` → Known gaps): rate limits, login limits and the WebSocket broker are
in memory, so a second backend instance needs Redis and a broker relay first. Clip rendering competes with API
requests for CPU (`JOB_WORKER_THREADS`).

---

## Go-live checklist

- ☐ VM in an Indian region, firewall set, SSH keys only, unattended upgrades
- ☐ Managed Postgres: TLS, VM-only access, provider backups + PITR on, `vlk_app` + `vlk_backup` roles
- ☐ DNS: `api` → VM (DNS only), apex/www → Vercel; HTTPS works on both
- ☐ R2: three private buckets, scoped tokens, CORS on media + clips, lifecycle rules
- ☐ `backend.env`, `ai.env`, `backup.env`, `.env` filled in (`chmod 600`); the `ProductionGuard` passes
- ☐ Razorpay live keys, webhook URL + all events, Route + Subscriptions enabled
- ☐ Sentry DSNs (backend + web), alerts configured
- ☐ Vercel: root `web`, env vars, domains; GitHub `production` environment secrets + branch protection
- ☐ First deploy green; `https://api.example.in/actuator/health` UP; web loads with no console errors
- ☐ First admin created, then `ADMIN_PASSWORD` removed
- ☐ Backup ran (R2 shows a dump), heartbeat configured, restore drill passed, age key in the password manager
- ☐ Privacy policy, terms and recording consent reviewed by a lawyer; legal env vars set; draft banner gone
- ☐ DPDP grievance officer published and the mailbox monitored; breach-response plan written
- ☐ Real payment + refund, tip and subscription charge tested end to end on live keys
