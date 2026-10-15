# shellcheck shell=bash
# Shared settings for backup.sh / restore.sh. Database: standard libpq variables (PGHOST, PGPORT, PGUSER,
# PGPASSWORD, PGDATABASE, PGSSLMODE). Storage: any S3 API (Cloudflare R2 in production).
set -Eeuo pipefail

: "${BACKUP_S3_ENDPOINT:?BACKUP_S3_ENDPOINT is required (https://<account-id>.r2.cloudflarestorage.com)}"
: "${BACKUP_S3_BUCKET:?BACKUP_S3_BUCKET is required}"
: "${AWS_ACCESS_KEY_ID:?AWS_ACCESS_KEY_ID is required}"
: "${AWS_SECRET_ACCESS_KEY:?AWS_SECRET_ACCESS_KEY is required}"
export AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION:-auto}"
# R2 rejects the CRC checksums newer AWS CLIs add by default.
export AWS_REQUEST_CHECKSUM_CALCULATION=when_required
export AWS_RESPONSE_CHECKSUM_VALIDATION=when_required
BACKUP_S3_PREFIX="${BACKUP_S3_PREFIX:-postgres}"
BACKUP_S3_PREFIX="${BACKUP_S3_PREFIX%/}"

s3() { aws --endpoint-url "$BACKUP_S3_ENDPOINT" "$@"; }
log() { printf '{"@timestamp":"%s","log.level":"%s","service.name":"vlk-backup","message":"%s"}\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$1" "$2"; }

# Object keys of all backups, oldest first (names sort by time: vlk-YYYYMMDDTHHMMSSZ.dump[.age]).
list_backups() {
  s3 s3 ls "s3://$BACKUP_S3_BUCKET/$BACKUP_S3_PREFIX/" | awk '{print $4}' | grep -E '^vlk-[0-9]{8}T[0-9]{6}Z\.dump(\.age)?$' | sort || true
}
