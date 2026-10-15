#!/usr/bin/env bash
# Runs backup.sh every day at BACKUP_AT (UTC, HH:MM; default 21:00 = 02:30 IST). A failed run is logged (and
# reported to the heartbeat URL) and retried the next day; the container keeps running.
set -euo pipefail
at="${BACKUP_AT:-21:00}"
hh="${at%%:*}"; mm="${at##*:}"
offset=$(( 10#$hh * 3600 + 10#$mm * 60 ))
while true; do
  now="$(date -u +%s)"
  next=$(( now - now % 86400 + offset ))
  [ "$next" -le "$now" ] && next=$(( next + 86400 ))
  printf '{"@timestamp":"%s","log.level":"INFO","service.name":"vlk-backup","message":"next backup at %s"}\n' \
    "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$(date -u -d "@$next" +%Y-%m-%dT%H:%M:%SZ)"
  sleep $(( next - now ))
  /usr/local/bin/backup.sh || true
done
