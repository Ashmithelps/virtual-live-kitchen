#!/usr/bin/env bash
# One backup: pg_dump (custom format) → verify it lists → (optionally encrypt with age) → upload → prune old ones.
#   BACKUP_RETENTION_DAYS (default 30), BACKUP_AGE_RECIPIENT (age public key; empty = rely on R2 encryption at rest),
#   BACKUP_HEARTBEAT_URL (optional, e.g. a healthchecks.io check: pinged on success, <url>/fail on failure).
# shellcheck source=common.sh disable=SC1091
source /usr/local/bin/common.sh

heartbeat() { [ -n "${BACKUP_HEARTBEAT_URL:-}" ] && wget -q -T 10 -O /dev/null "$BACKUP_HEARTBEAT_URL$1" || true; }
trap 'log ERROR "backup failed (line $LINENO)"; heartbeat /fail' ERR

: "${PGHOST:?PGHOST is required}" "${PGDATABASE:?PGDATABASE is required}"
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
dump="$work/vlk-$stamp.dump"

log INFO "dumping $PGDATABASE on $PGHOST"
pg_dump --format=custom --compress=6 --no-owner --no-privileges --file "$dump"
# A dump that can't be listed can't be restored; fail now rather than discover it later.
pg_restore --list "$dump" > /dev/null
file="$dump"
if [ -n "${BACKUP_AGE_RECIPIENT:-}" ]; then
  age --encrypt --recipient "$BACKUP_AGE_RECIPIENT" --output "$dump.age" "$dump"
  file="$dump.age"
fi
size="$(stat -c %s "$file")"
s3 s3 cp --only-show-errors "$file" "s3://$BACKUP_S3_BUCKET/$BACKUP_S3_PREFIX/$(basename "$file")"
log INFO "uploaded $(basename "$file") ($size bytes)"

# Prune by the timestamp in the name (R2 lifecycle rules can do this too; see PRODUCTION.md).
retention="${BACKUP_RETENTION_DAYS:-30}"
cutoff="$(date -u -d "@$(( $(date -u +%s) - retention * 86400 ))" +%Y%m%dT%H%M%SZ)"
kept=0
for key in $(list_backups); do
  ts="${key#vlk-}"; ts="${ts%%.*}"
  if [[ "$ts" < "$cutoff" ]]; then
    s3 s3 rm --only-show-errors "s3://$BACKUP_S3_BUCKET/$BACKUP_S3_PREFIX/$key"
    log INFO "pruned $key (older than $retention days)"
  else
    kept=$((kept + 1))
  fi
done
log INFO "backup complete; $kept backups kept"
heartbeat ""
