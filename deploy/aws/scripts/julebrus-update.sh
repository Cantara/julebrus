#!/usr/bin/env bash
# Pull deploy for Julebrus. Runs on the box from julebrus-update.timer as root.
# Reads s3://<artifacts>/latest.json {version, sha256, key}, verifies the jar's sha256, installs it under
# /opt/julebrus/releases/<version>/, repoints /opt/julebrus/current, restarts the app and checks /actuator/health.
# On a failed health check it points `current` back at the previous release and restarts, remembers the failed
# release (so the timer does not restart the app every 5 minutes for the same broken jar) and fails loudly.
# Never touches the database or /etc/julebrus.
set -euo pipefail
# Overridable only so the swap and rollback logic can be tested locally with fake commands.
BUCKET="${JULEBRUS_BUCKET:-$(cat /etc/julebrus/artifacts-bucket)}"
ROOT="${JULEBRUS_ROOT:-/opt/julebrus}"; STATE="${JULEBRUS_STATE_DIR:-/var/lib/julebrus}"
HEALTH_URL="${JULEBRUS_HEALTH_URL:-http://127.0.0.1:8080/actuator/health}"
TRIES="${JULEBRUS_HEALTH_TRIES:-30}"; PAUSE="${JULEBRUS_HEALTH_PAUSE:-4}"
log() { echo "julebrus-update: $*"; }
die() { log "ERROR: $*"; exit 1; }
own=(); [ "$(id -u)" = 0 ] && own=(-o julebrus -g julebrus)
work="$(mktemp -d)"; trap 'rm -rf "$work"' EXIT
mkdir -p "$STATE"
aws s3 cp "s3://$BUCKET/latest.json" "$work/latest.json" --quiet || die "cannot read latest.json"
want="$(jq -r '.version // empty' "$work/latest.json")"; [ -n "$want" ] || die "latest.json has no version"
[[ "$want" =~ ^[0-9A-Za-z._-]+$ ]] || die "latest.json has an odd version: refusing"
key="$(jq -r '.key // empty' "$work/latest.json")"; sha="$(jq -r '.sha256 // empty' "$work/latest.json")"
[ -n "$key" ] && [ -n "$sha" ] || die "latest.json lacks key or sha256"
have="$(basename "$(readlink -f "$ROOT/current" 2>/dev/null || true)" 2>/dev/null || true)"
[ "$want" = "$have" ] && { log "already on $want"; exit 0; }
if [ "$(cat "$STATE/failed-$want" 2>/dev/null || true)" = "$sha" ]; then
  log "$want failed its health check before (same sha256); staying on ${have:-nothing}. Publish a new release to retry."; exit 0
fi
aws s3 cp "s3://$BUCKET/$key" "$work/app.jar" --quiet || die "download failed: $key"
[ "$(sha256sum "$work/app.jar" | cut -d' ' -f1)" = "$sha" ] || die "sha256 mismatch: refusing to install $want"
install -d "${own[@]}" "$ROOT/releases/$want"
install "${own[@]}" -m 0644 "$work/app.jar" "$ROOT/releases/$want/app.jar"
prev="$(readlink -f "$ROOT/current" 2>/dev/null || true)"
ln -sfn "$ROOT/releases/$want" "$ROOT/current"
systemctl restart julebrus.service
for _ in $(seq 1 "$TRIES"); do
  if curl -fsS -m 3 "$HEALTH_URL" >/dev/null 2>&1; then
    log "now on $want"
    # Keep the five newest releases, never the one in use.
    ls -1dt "$ROOT"/releases/* | tail -n +6 | while read -r d; do [ "$d" = "$(readlink -f "$ROOT/current")" ] || rm -rf "$d"; done
    exit 0
  fi
  sleep "$PAUSE"
done
log "health check failed on $want"
printf '%s' "$sha" > "$STATE/failed-$want"
if [ -n "$prev" ] && [ -d "$prev" ]; then ln -sfn "$prev" "$ROOT/current"; systemctl restart julebrus.service; die "rolled back to $(basename "$prev")"; fi
die "failed and there is no previous release"
