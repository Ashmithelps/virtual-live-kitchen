#!/usr/bin/env bash
# Restore drill, safe to run any time from any machine with Docker: restores the latest (or a given) backup into a
# throwaway Postgres container, prints sanity counts, then deletes the container. Production is never touched.
#   infra/backup/restore-test.sh <backup.env> [latest|<backup key>] [backup image]
# The image defaults to the one CI publishes (ghcr.io/<owner>/vlk-backup:main); build locally with
#   docker build -t vlk-backup infra/backup
set -euo pipefail
envfile="${1:?usage: restore-test.sh <backup.env> [latest|<key>] [image]}"
which="${2:-latest}"
image="${3:-${BACKUP_IMAGE:-vlk-backup}}"
pg_image="${PG_IMAGE:-postgres:16.15-alpine3.24}"
name="vlk-restore-drill-$$"
net="$name-net"
password="$(openssl rand -hex 16)"

cleanup() { docker rm -f "$name" >/dev/null 2>&1 || true; docker network rm "$net" >/dev/null 2>&1 || true; }
trap cleanup EXIT
docker network create "$net" >/dev/null
docker run -d --name "$name" --network "$net" -e POSTGRES_PASSWORD="$password" -e POSTGRES_DB=drill "$pg_image" >/dev/null
for _ in $(seq 1 60); do docker exec "$name" pg_isready -U postgres -d drill >/dev/null 2>&1 && break; sleep 1; done

started="$(date +%s)"
identity=()
if [ -n "${BACKUP_AGE_IDENTITY_FILE:-}" ]; then
  identity=(-v "$BACKUP_AGE_IDENTITY_FILE:/run/age-identity:ro" -e BACKUP_AGE_IDENTITY_FILE=/run/age-identity)
fi
docker run --rm --network "$net" --env-file "$envfile" ${identity[@]+"${identity[@]}"} "$image" \
  restore "postgresql://postgres:$password@$name:5432/drill" "$which"
echo "Restore took $(( $(date +%s) - started )) s. Sanity checks:"
docker exec "$name" psql -U postgres -d drill -c "
  select (select max(version::int) from flyway_schema_history where success) as flyway_version,
         (select count(*) from flyway_schema_history where not success)       as failed_migrations,
         (select count(*) from users)       as users,
         (select count(*) from restaurants) as restaurants,
         (select count(*) from orders)      as orders,
         (select max(placed_at) from orders) as newest_order,
         (select count(*) from invoices)    as invoices"
echo "Drill OK. The throwaway database is removed now."
