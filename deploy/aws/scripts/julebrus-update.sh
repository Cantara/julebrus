#!/usr/bin/env bash
# Pull deploy for Julebrus. Runs on the box from julebrus-update.timer as root.
# Reads s3://<artifacts>/latest.json {version, sha256, key}, verifies the jar's sha256, installs it under
# /opt/julebrus/releases/<version>/, repoints /opt/julebrus/current, restarts the app and checks /actuator/health.
# If the restart fails (for example the pre-start cannot read a secret) or the health check fails, it points `current`
# back at the previous release and restarts, remembers the failed release (so the timer does not restart the app every
# 5 minutes for the same broken jar) and fails loudly. On a first install with nothing to go back to it removes the
# `current` link and stops the service. It never goes to a LOWER version than the installed one unless
# /var/lib/julebrus/allow-downgrade contains that exact version. Only one run at a time (flock).
# Never touches the database or /etc/julebrus. A failure that appears LATER, after a release passed its health check, is not rolled back.
set -euo pipefail
# Overridable only so the swap and rollback logic can be tested locally with fake commands.
BUCKET="${JULEBRUS_BUCKET:-$(cat /etc/julebrus/artifacts-bucket)}"
ROOT="${JULEBRUS_ROOT:-/opt/julebrus}"; STATE="${JULEBRUS_STATE_DIR:-/var/lib/julebrus}"
HEALTH_URL="${JULEBRUS_HEALTH_URL:-http://127.0.0.1:8080/actuator/health}"
TRIES="${JULEBRUS_HEALTH_TRIES:-30}"; PAUSE="${JULEBRUS_HEALTH_PAUSE:-4}"
log() { echo "julebrus-update: $*"; }
die() { log "ERROR: $*"; exit 1; }
mkdir -p "$STATE"
exec 9>"$STATE/update.lock"
flock -n 9 || { log "another update is running; skipping"; exit 0; }
work="$(mktemp -d)"; trap 'rm -rf "$work"' EXIT
aws s3 cp "s3://$BUCKET/latest.json" "$work/latest.json" --quiet || die "cannot read latest.json"
want="$(jq -r '.version // empty' "$work/latest.json")"; [ -n "$want" ] || die "latest.json has no version"
[[ "$want" =~ ^[0-9A-Za-z._-]+$ ]] || die "latest.json has an odd version: refusing"
key="$(jq -r '.key // empty' "$work/latest.json")"; sha="$(jq -r '.sha256 // empty' "$work/latest.json")"
[ -n "$key" ] && [ -n "$sha" ] || die "latest.json lacks key or sha256"
have=""; [ ! -d "$ROOT/current" ] || have="$(basename "$(readlink -f "$ROOT/current")")"   # empty on a first install
[ "$want" = "$have" ] && { log "already on $want"; exit 0; }
if [ "$(cat "$STATE/failed-$want" 2>/dev/null || true)" = "$sha" ]; then
  log "$want failed before (same sha256); staying on ${have:-nothing}. Publish a new release to retry."; exit 0
fi
if [ -n "$have" ] && [ "$(printf '%s\n%s\n' "$want" "$have" | sort -V | head -1)" = "$want" ]; then
  if [ "$(cat "$STATE/allow-downgrade" 2>/dev/null || true)" = "$want" ]; then log "downgrade to $want allowed by $STATE/allow-downgrade"
  else die "latest.json names $want, lower than the installed $have: refusing (write $want into $STATE/allow-downgrade to allow it once)"; fi
fi
aws s3 cp "s3://$BUCKET/$key" "$work/app.jar" --quiet || die "download failed: $key"
[ "$(sha256sum "$work/app.jar" | cut -d' ' -f1)" = "$sha" ] || die "sha256 mismatch: refusing to install $want"
# Root-owned; the service user only needs to read the jar.
install -d -m 0755 "$ROOT/releases/$want"
install -m 0644 "$work/app.jar" "$ROOT/releases/$want/app.jar"
prev="$(readlink -f "$ROOT/current" 2>/dev/null || true)"
ln -sfn "$ROOT/releases/$want" "$ROOT/current"
healthy=0
if systemctl restart julebrus.service; then
  for _ in $(seq 1 "$TRIES"); do
    if curl -fsS -m 3 "$HEALTH_URL" >/dev/null 2>&1; then healthy=1; break; fi
    sleep "$PAUSE"
  done
  [ "$healthy" = 1 ] || log "health check failed on $want"
else
  log "the service did not start on $want (see: journalctl -u julebrus)"
fi
if [ "$healthy" = 1 ]; then
  log "now on $want"; rm -f "$STATE/allow-downgrade"
  # Keep the five newest releases, never the one in use.
  ls -1dt "$ROOT"/releases/* | tail -n +6 | while read -r d; do [ "$d" = "$(readlink -f "$ROOT/current")" ] || rm -rf "$d"; done
  exit 0
fi
printf '%s' "$sha" > "$STATE/failed-$want"
# reset-failed: after a failed start the unit may be at its start limit, which would refuse the restart below.
systemctl reset-failed julebrus.service 2>/dev/null || true
if [ -n "$prev" ] && [[ "$prev" == "$ROOT/releases/"* ]] && [ -d "$prev" ]; then
  ln -sfn "$prev" "$ROOT/current"; systemctl restart julebrus.service || true; die "rolled back to $(basename "$prev")"
fi
rm -f "$ROOT/current"; systemctl stop julebrus.service || true
die "failed and there is no previous release: removed the current link and stopped the service"
