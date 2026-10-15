#!/usr/bin/env bash
# Restores a backup into a database.
#   restore <target-postgres-url> [latest|vlk-YYYYMMDDTHHMMSSZ.dump[.age]] [--overwrite]
# The target must be empty unless --overwrite is given (then existing objects are dropped first).
# Encrypted backups need BACKUP_AGE_IDENTITY_FILE (the age private key file, mounted read-only).
# shellcheck source=common.sh disable=SC1091
source /usr/local/bin/common.sh

target="${1:?usage: restore <target-postgres-url> [latest|<backup key>] [--overwrite]}"
which="${2:-latest}"
overwrite="${3:-}"

# The target URL must say everything (host, credentials, sslmode); never fall back to the PG* variables, which
# point at the production database this container backs up.
unset PGHOST PGPORT PGUSER PGPASSWORD PGDATABASE PGSSLMODE PGSERVICE

if [ "$which" = latest ]; then
  which="$(list_backups | tail -n 1)"
  [ -n "$which" ] || { log ERROR "no backups under s3://$BACKUP_S3_BUCKET/$BACKUP_S3_PREFIX/"; exit 1; }
fi
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
log INFO "downloading $which"
s3 s3 cp --only-show-errors "s3://$BACKUP_S3_BUCKET/$BACKUP_S3_PREFIX/$which" "$work/$which"
dump="$work/$which"
if [[ "$which" == *.age ]]; then
  : "${BACKUP_AGE_IDENTITY_FILE:?this backup is encrypted; set BACKUP_AGE_IDENTITY_FILE}"
  age --decrypt --identity "$BACKUP_AGE_IDENTITY_FILE" --output "${dump%.age}" "$dump"
  dump="${dump%.age}"
fi

tables="$(psql "$target" -Atc "select count(*) from information_schema.tables where table_schema = 'public'")"
if [ "$tables" != 0 ] && [ "$overwrite" != --overwrite ]; then
  log ERROR "target database already has $tables tables; pass --overwrite to replace them"
  exit 2
fi
log INFO "restoring $which"
pg_restore --no-owner --no-privileges --exit-on-error ${overwrite:+--clean --if-exists} --dbname "$target" "$dump"
version="$(psql "$target" -Atc "select max(version::int) from flyway_schema_history where success")"
log INFO "restored $which; schema at Flyway V$version"
echo "RESTORED $which flyway=V$version"
