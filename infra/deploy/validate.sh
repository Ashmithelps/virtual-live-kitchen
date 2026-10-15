#!/usr/bin/env bash
# Checks infra/docker-compose.prod.yml and the Caddyfile with the example env files (CI and local).
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
cp "$root/docker-compose.prod.yml" "$root/caddy/Caddyfile" "$tmp/"
cp "$root/prod/compose.env.example" "$tmp/.env"
cp "$root/prod/backend.env.example" "$tmp/backend.env"
cp "$root/prod/ai.env.example" "$tmp/ai.env"
cp "$root/prod/backup.env.example" "$tmp/backup.env"
(cd "$tmp" && docker compose -f docker-compose.prod.yml config --quiet)
echo "✓ docker-compose.prod.yml"
docker run --rm -e API_DOMAIN=api.example.in -e ACME_EMAIL=ops@example.in -v "$tmp/Caddyfile:/etc/caddy/Caddyfile:ro" \
  caddy:2.11.4-alpine caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile 2>&1 | tail -n 1
echo "✓ Caddyfile"
