#!/usr/bin/env bash
# End-to-end test of the backup image (run locally or in CI): S3-compatible storage (RustFS) + two Postgres
# databases. Checks backup → restore (plain and age-encrypted), refusal to overwrite, and retention pruning.
set -euo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"
id="vlk-backup-test-$$"
net="$id-net"
image="$id-image"
cleanup() {
  docker rm -f "$id-src" "$id-dst" "$id-s3" >/dev/null 2>&1 || true
  docker network rm "$net" >/dev/null 2>&1 || true
  docker rmi "$image" >/dev/null 2>&1 || true
  rm -rf "$tmp"
}
tmp="$(mktemp -d)"
trap cleanup EXIT

docker build -q -t "$image" "$here" >/dev/null
docker network create "$net" >/dev/null
docker run -d --name "$id-src" --network "$net" -e POSTGRES_PASSWORD=srcpw -e POSTGRES_DB=vk postgres:16.15-alpine3.24 >/dev/null
docker run -d --name "$id-dst" --network "$net" -e POSTGRES_PASSWORD=dstpw -e POSTGRES_DB=drill postgres:16.15-alpine3.24 >/dev/null
docker run -d --name "$id-s3" --network "$net" -e RUSTFS_ACCESS_KEY=testkey -e RUSTFS_SECRET_KEY=testsecret12345 \
  -e RUSTFS_VOLUMES=/data -e RUSTFS_ADDRESS=0.0.0.0:9000 rustfs/rustfs:1.0.0 >/dev/null
for c in src dst; do for _ in $(seq 1 60); do docker exec "$id-$c" pg_isready -U postgres >/dev/null 2>&1 && break; sleep 1; done; done
sleep 2

docker exec -i "$id-src" psql -q -U postgres -d vk <<'SQL'
create table flyway_schema_history (installed_rank int primary key, version varchar(50), success boolean not null);
insert into flyway_schema_history values (1, '1', true), (2, '2', true);
create table users (id serial primary key, phone text);
create table restaurants (id serial primary key, name text);
create table orders (id serial primary key, total_paise bigint, created_at timestamptz default now());
insert into users (phone) select '+9190000' || g from generate_series(1, 25) g;
insert into restaurants (name) values ('Dev Spice Kitchen');
insert into orders (total_paise) select g * 100 from generate_series(1, 40) g;
SQL

cat > "$tmp/backup.env" <<ENV
PGHOST=$id-src
PGUSER=postgres
PGPASSWORD=srcpw
PGDATABASE=vk
PGSSLMODE=disable
BACKUP_S3_ENDPOINT=http://$id-s3:9000
BACKUP_S3_BUCKET=backups
AWS_ACCESS_KEY_ID=testkey
AWS_SECRET_ACCESS_KEY=testsecret12345
AWS_DEFAULT_REGION=us-east-1
BACKUP_RETENTION_DAYS=30
ENV
run() { docker run --rm --network "$net" --env-file "$tmp/backup.env" "$@"; }
# shellcheck disable=SC2016 # expanded inside the container
run "$image" sh -c 'aws --endpoint-url "$BACKUP_S3_ENDPOINT" s3 mb s3://backups >/dev/null'
# An expired backup that retention must remove.
# shellcheck disable=SC2016
run "$image" sh -c 'echo old > /tmp/x && aws --endpoint-url "$BACKUP_S3_ENDPOINT" s3 cp --only-show-errors /tmp/x s3://backups/postgres/vlk-20200101T000000Z.dump'

run "$image" backup-now | tee "$tmp/backup.log"
grep -q "pruned vlk-20200101T000000Z.dump" "$tmp/backup.log"
target="postgresql://postgres:dstpw@$id-dst:5432/drill"
run "$image" restore "$target" latest | tee "$tmp/restore.log"
grep -q "flyway=V2" "$tmp/restore.log"
[ "$(docker exec "$id-dst" psql -U postgres -d drill -Atc 'select count(*) from orders')" = 40 ]
[ "$(docker exec "$id-dst" psql -U postgres -d drill -Atc 'select count(*) from users')" = 25 ]
echo "✓ plain backup restored (40 orders, 25 users)"

# Refuses a non-empty target without --overwrite; replaces it with.
if run "$image" restore "$target" latest >/dev/null 2>&1; then echo "✗ restore into a non-empty database was allowed"; exit 1; fi
docker exec "$id-src" psql -q -U postgres -d vk -c "insert into orders (total_paise) values (1)"
sleep 1
run "$image" backup-now >/dev/null
run "$image" restore "$target" latest --overwrite >/dev/null
[ "$(docker exec "$id-dst" psql -U postgres -d drill -Atc 'select count(*) from orders')" = 41 ]
echo "✓ non-empty target refused; --overwrite replaced it (41 orders)"

# age-encrypted backups.
docker run --rm "$image" age-keygen 2>/dev/null > "$tmp/age.key"
recipient="$(grep -o 'age1[0-9a-z]*' "$tmp/age.key")"
chmod 644 "$tmp/age.key"
sleep 1
run -e BACKUP_AGE_RECIPIENT="$recipient" "$image" backup-now | grep '\.dump\.age' >/dev/null
docker exec "$id-dst" psql -q -U postgres -c "drop database drill" -c "create database drill"
run -v "$tmp/age.key:/run/age.key:ro" -e BACKUP_AGE_IDENTITY_FILE=/run/age.key "$image" restore "$target" latest | grep "\.dump\.age flyway=V2" >/dev/null
[ "$(docker exec "$id-dst" psql -U postgres -d drill -Atc 'select count(*) from orders')" = 41 ]
echo "✓ age-encrypted backup restored"
echo "backup roundtrip OK"
