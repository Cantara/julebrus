#!/usr/bin/env bash
# Pull deploy for Julebrus. Runs on the box from julebrus-update.timer as root.
# Reads s3://<artifacts>/latest.json {version, sha256, key}, verifies the jar's sha256, installs it under
# /opt/julebrus/releases/<version>/, repoints /opt/julebrus/current, restarts the app and checks /actuator/health.
# Before touching anything it runs the pre-start script (secrets readable?): if that fails it installs nothing, writes no
# failure marker (a missing secret is not the release's fault) and exits non-zero; the next run retries by itself.
# If the restart fails or the health check fails, it points `current` back at the previous release and restarts,
# remembers the failed release (failed-<version> in /var/lib/julebrus, so the timer does not restart the app every
# 5 minutes for the same broken jar; `julebrus.sh retry <version>` clears it) and fails loudly. On a first install with
# nothing to go back to it removes the `current` link and stops the service.
# Output ends with one RESULT line: installed, unchanged, skipped-failed-before, skipped-locked, or an ERROR line. It never goes to a LOWER version than the installed one unless
# /var/lib/julebrus/allow-downgrade contains that exact version. Only one run at a time (flock).
# Never touches the database or /etc/julebrus. A failure that appears LATER, after a release passed its health check, is not rolled back.
set -euo pipefail
# Overridable only so the swap and rollback logic can be tested locally with fake commands.
BUCKET="${JULEBRUS_BUCKET:-$(cat /etc/julebrus/artifacts-bucket)}"
ROOT="${JULEBRUS_ROOT:-/opt/julebrus}"; STATE="${JULEBRUS_STATE_DIR:-/var/lib/julebrus}"
CONF="${JULEBRUS_CONF_DIR:-/etc/julebrus}"; PRESTART="${JULEBRUS_PRESTART:-$ROOT/julebrus-prestart.sh}"
# The aws calls need the region; take it from app.env like the pre-start script does.
r="$(sed -n 's/^AWS_REGION=//p' "$CONF/app.env" 2>/dev/null | head -1 || true)"; export AWS_REGION="${r:-eu-north-1}"
HEALTH_URL="${JULEBRUS_HEALTH_URL:-http://127.0.0.1:8080/actuator/health}"
TRIES="${JULEBRUS_HEALTH_TRIES:-30}"; PAUSE="${JULEBRUS_HEALTH_PAUSE:-4}"
log() { echo "julebrus-update: $*"; }
die() { log "ERROR: $*"; exit 1; }
mkdir -p "$STATE"
exec 9>"$STATE/update.lock"
flock -n 9 || { log "another update is running; skipping"; log "RESULT: skipped-locked"; exit 0; }
work="$(mktemp -d)"; trap 'rm -rf "$work"' EXIT
aws s3 cp "s3://$BUCKET/latest.json" "$work/latest.json" --quiet || die "cannot read latest.json"
want="$(jq -r '.version // empty' "$work/latest.json")"; [ -n "$want" ] || die "latest.json has no version"
# Same shape as the release workflow's tag regex (no pre-release suffixes), so sort -V orders versions correctly.
[[ "$want" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || die "latest.json has a version that is not N.N.N: refusing"
key="$(jq -r '.key // empty' "$work/latest.json")"; sha="$(jq -r '.sha256 // empty' "$work/latest.json")"
[ -n "$key" ] && [ -n "$sha" ] || die "latest.json lacks key or sha256"
have=""; [ ! -d "$ROOT/current" ] || have="$(basename "$(readlink -f "$ROOT/current")")"   # empty on a first install
[ "$want" = "$have" ] && { log "already on $want"; log "RESULT: unchanged $want"; exit 0; }
if [ "$(cat "$STATE/failed-$want" 2>/dev/null || true)" = "$sha" ]; then
  log "$want failed before (same sha256); staying on ${have:-nothing}. Publish a new release to retry (or: julebrus.sh retry $want)."; log "RESULT: skipped-failed-before $want"; exit 0
fi
if [ -n "$have" ] && [ "$(printf '%s\n%s\n' "$want" "$have" | sort -V | head -1)" = "$want" ]; then
  if [ "$(cat "$STATE/allow-downgrade" 2>/dev/null || true)" = "$want" ]; then log "downgrade to $want allowed by $STATE/allow-downgrade"
  else die "latest.json names $want, lower than the installed $have: refusing (write $want into $STATE/allow-downgrade to allow it once)"; fi
fi
aws s3 cp "s3://$BUCKET/$key" "$work/app.jar" --quiet || die "download failed: $key"
[ "$(sha256sum "$work/app.jar" | cut -d' ' -f1)" = "$sha" ] || die "sha256 mismatch: refusing to install $want"
# Can the app get its secrets at all? If not, install nothing: no swap, no marker, nothing to roll back.
if [ -x "$PRESTART" ] && ! "$PRESTART" 2> "$work/prestart.err"; then
  log "the pre-start script failed, so $want is NOT installed: $(tr '\n' ' ' < "$work/prestart.err")"
  die "fix the secret named above; the next run installs $want"
fi
# Root-owned; the service user only needs to read the jar.
install -d -m 0755 "$ROOT/releases/$want"
install -m 0644 "$work/app.jar" "$ROOT/releases/$want/app.jar"
prev="$(readlink -f "$ROOT/current" 2>/dev/null || true)"
ln -sfn "$ROOT/releases/$want" "$ROOT/current"
wait_healthy() { local i; for i in $(seq 1 "$TRIES"); do if curl -fsS -m 3 "$HEALTH_URL" >/dev/null 2>&1; then return 0; fi; sleep "$PAUSE"; done; return 1; }
healthy=0
# reset-failed first: earlier failed starts can leave the unit at its start limit, which would refuse this restart.
systemctl reset-failed julebrus.service 2>/dev/null || true
if systemctl restart julebrus.service; then
  if wait_healthy; then healthy=1; else log "health check failed on $want"; fi
else
  log "the service did not start, or died while starting, on $want (see: journalctl -u julebrus)"
fi
if [ "$healthy" = 1 ]; then
  log "now on $want"; log "RESULT: installed $want"; rm -f "$STATE/allow-downgrade"
  # Keep the five newest GOOD releases, never the one in use nor the one we came from. Releases that failed do not count
  # toward the five; their directories are kept for 14 days (for a look at them) and then removed.
  curd="$(readlink -f "$ROOT/current")"
  ls -1dt "$ROOT"/releases/* | while read -r d; do [ -e "$STATE/failed-$(basename "$d")" ] || echo "$d"; done | tail -n +6 \
    | while read -r d; do if [ "$d" != "$curd" ] && [ "$d" != "$prev" ]; then rm -rf "$d"; fi; done || true
  find "$STATE" -maxdepth 1 -name 'failed-*' -mtime +14 | while read -r f; do rm -rf "$ROOT/releases/${f##*/failed-}"; done || true
  exit 0
fi
printf '%s' "$sha" > "$STATE/failed-$want"
log "NOTE: $want is now marked as failed and will not be retried by itself. If the cause was outside the release (for example the database was unreachable), clear the marker with: julebrus.sh retry $want"
# reset-failed: after a failed start the unit may be at its start limit, which would refuse the restart below.
systemctl reset-failed julebrus.service 2>/dev/null || true
if [ -n "$prev" ] && [[ "$prev" == "$ROOT/releases/"* ]] && [ -d "$prev" ]; then
  ln -sfn "$prev" "$ROOT/current"
  if systemctl restart julebrus.service && wait_healthy; then die "rolled back to $(basename "$prev") (healthy)"; fi
  die "rolled back to $(basename "$prev") BUT UNHEALTHY: the previous release does not come up either (see: journalctl -u julebrus)"
fi
rm -f "$ROOT/current"; systemctl stop julebrus.service || true
die "failed and there is no previous release: removed the current link and stopped the service"
