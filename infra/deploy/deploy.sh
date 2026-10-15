#!/usr/bin/env bash
# Runs ON THE SERVER (CI copies it to /opt/vlk and calls it over SSH; you can run it by hand too):
#   /opt/vlk/deploy.sh <image tag>        e.g. the git SHA CI pushed; also used to roll back to an older tag
# 1. backs up the database (SKIP_PREDEPLOY_BACKUP=1 to skip), 2. pulls the images, 3. restarts the stack
# (Flyway migrates on backend startup), 4. waits until /actuator/health is UP, else 5. returns to the previous tag.
set -euo pipefail
cd "$(dirname "$0")"
tag="${1:?usage: deploy.sh <image tag>}"
compose=(docker compose -f docker-compose.prod.yml)
health_timeout="${HEALTH_TIMEOUT:-240}"

log() { printf '[deploy %s] %s\n' "$(date -u +%H:%M:%S)" "$*"; }
set_tag() {
  if grep -q '^IMAGE_TAG=' .env; then sed -i.bak "s/^IMAGE_TAG=.*/IMAGE_TAG=$1/" .env && rm -f .env.bak; else echo "IMAGE_TAG=$1" >> .env; fi
}
healthy() {
  local deadline=$(( $(date +%s) + health_timeout ))
  while [ "$(date +%s)" -lt "$deadline" ]; do
    if "${compose[@]}" exec -T backend curl -fsS http://127.0.0.1:8080/actuator/health 2>/dev/null | grep -q '"status":"UP"'; then
      return 0
    fi
    sleep 5
  done
  return 1
}

previous="$(grep '^IMAGE_TAG=' .env | cut -d= -f2 || true)"
log "deploying $tag (previous: ${previous:-none})"

if [ -n "$previous" ] && [ -z "${SKIP_PREDEPLOY_BACKUP:-}" ]; then
  log "pre-deploy database backup"
  "${compose[@]}" run --rm --no-deps backup backup-now
fi

# Pull first (the variable overrides .env), so a missing image leaves .env and the running stack untouched.
if ! IMAGE_TAG="$tag" "${compose[@]}" pull --quiet backend ai-service backup; then
  log "could not pull images for $tag; nothing changed"
  exit 1
fi
set_tag "$tag"
"${compose[@]}" up -d --remove-orphans

if healthy; then
  log "backend is UP on $tag"
  echo "$tag" >> .deploy-history
  docker image prune -f >/dev/null
  exit 0
fi

log "backend did not become healthy within ${health_timeout}s; last log lines:"
"${compose[@]}" logs --tail 60 backend || true
if [ -n "$previous" ] && [ "$previous" != "$tag" ]; then
  log "rolling back to $previous"
  set_tag "$previous"
  "${compose[@]}" up -d --remove-orphans
  if healthy; then log "rolled back to $previous"; else log "ROLLBACK ALSO UNHEALTHY: investigate now"; fi
  log "if a new migration ran, the old code is now on the newer schema; see PRODUCTION.md → Rollback"
fi
exit 1
