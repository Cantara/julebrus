#!/usr/bin/env bash
# Edit the NON-SECRET app settings in /etc/julebrus/app.env. Runs on the box as root (installed by box-setup.sh).
#   julebrus-env.sh keys             list the allowed keys and whether each is set (never the values, never the file)
#   julebrus-env.sh set KEY=VALUE    change one allowed key (no restart)
#   julebrus-env.sh apply KEY=VALUE  set, then restart the app and wait for /actuator/health. With no release installed
#                                    yet it only saves the setting (it takes effect at the first release). If the app is
#                                    not healthy afterwards the previous value is restored and the app restarted again.
# Only the keys below can be changed; the value may contain letters, digits and @ . _ , + : / - only.
# `set` and `apply` leave app.env untouched on any failure and exit non-zero; `apply` ends with one OK: or FAILED: line.
# `apply` takes the updater's lock (waits up to 120 s) so the updater and an env change never restart the app at once.
set -euo pipefail
FILE="${JULEBRUS_APP_ENV:-/etc/julebrus/app.env}"; ROOT="${JULEBRUS_ROOT:-/opt/julebrus}"; STATE="${JULEBRUS_STATE_DIR:-/var/lib/julebrus}"
HEALTH_URL="${JULEBRUS_HEALTH_URL:-http://127.0.0.1:8080/actuator/health}"
TRIES="${JULEBRUS_HEALTH_TRIES:-40}"; PAUSE="${JULEBRUS_HEALTH_PAUSE:-5}"; LOCK_WAIT="${JULEBRUS_LOCK_WAIT:-120}"
ALLOWED=(JULEBRUS_ALLOWED_DOMAINS JULEBRUS_ADMINS JULEBRUS_CONTACT_EMAIL)
TMPF=""; trap 'rm -f "$TMPF"' EXIT
bad() { echo "FAILED: $1" >&2; return 1; }
value_of() { grep "^$1=" "$FILE" | head -1 | cut -d= -f2- || true; }
# Every step is checked. The original is replaced only by a temp file that was written completely and verified.
do_set() {
  local kv="$1" k v a ok=0 dir orig oldl expect
  [[ "$kv" == *=* ]] || { bad "expected KEY=VALUE"; return 1; }
  k="${kv%%=*}"; v="${kv#*=}"
  for a in "${ALLOWED[@]}"; do [ "$a" = "$k" ] && ok=1; done
  [ "$ok" = 1 ] || { bad "key not allowed: $k (allowed: ${ALLOWED[*]})"; return 1; }
  [[ "$v" =~ ^[A-Za-z0-9@._,+:/-]*$ ]] || { bad "value has characters outside A-Za-z0-9 @ . _ , + : / -"; return 1; }
  dir="$(dirname "$FILE")"
  [ -f "$FILE" ] && [ -w "$FILE" ] && [ -w "$dir" ] || { bad "cannot write $FILE or its directory"; return 1; }
  TMPF="$(mktemp "$FILE.XXXXXX")" || { bad "cannot create a temp file next to $FILE"; TMPF=""; return 1; }
  orig="$(wc -c < "$FILE")" || { bad "cannot read $FILE"; return 1; }
  oldl="$(grep "^$k=" "$FILE" | wc -c || true)"
  { grep -v "^$k=" "$FILE" > "$TMPF" || [ $? -eq 1 ]; } || { bad "could not copy the other settings"; return 1; }
  printf '%s=%s\n' "$k" "$v" >> "$TMPF" || { bad "could not write the new line (disk full?)"; return 1; }
  expect=$(( orig - oldl + ${#k} + ${#v} + 2 ))
  [ "$(wc -c < "$TMPF")" -eq "$expect" ] || { bad "the new file has the wrong size (expected $expect bytes): not replacing $FILE"; return 1; }
  [ "$(grep -c "^$k=" "$TMPF")" = 1 ] && grep -qx "$k=$v" "$TMPF" || { bad "the new file does not hold $k exactly once: not replacing $FILE"; return 1; }
  chmod --reference="$FILE" "$TMPF" || { bad "chmod failed"; return 1; }
  if [ "$(id -u)" = 0 ]; then chown --reference="$FILE" "$TMPF" || { bad "chown failed"; return 1; }; fi
  cp -p "$FILE" "$FILE.prev" || { bad "could not keep a copy of the old file"; return 1; }
  mv "$TMPF" "$FILE" || { bad "mv failed: $FILE is unchanged"; return 1; }
  TMPF=""
  if ! { [ "$(grep -c "^$k=" "$FILE")" = 1 ] && grep -qx "$k=$v" "$FILE"; }; then
    cp -p "$FILE.prev" "$FILE" || true; bad "the file does not read back with the new value; restored the old file"; return 1
  fi
  echo "$k updated"
}
wait_healthy() { local i; for i in $(seq 1 "$TRIES"); do if curl -fsS -m 3 "$HEALTH_URL" >/dev/null 2>&1; then return 0; fi; sleep "$PAUSE"; done; return 1; }
restart_app() { systemctl reset-failed julebrus.service 2>/dev/null || true; systemctl restart julebrus.service; }
case "${1:-}" in
  keys)
    for k in "${ALLOWED[@]}"; do
      if grep -q "^$k=." "$FILE"; then echo "$k: set"; else echo "$k: empty"; fi
    done ;;
  set) do_set "${2:?usage: julebrus-env.sh set KEY=VALUE}" || exit 1 ;;
  apply)
    kv="${2:?usage: julebrus-env.sh apply KEY=VALUE}"; k="${kv%%=*}"
    mkdir -p "$STATE"; exec 9>"$STATE/update.lock"
    flock -w "$LOCK_WAIT" 9 || { echo "FAILED: the updater has held its lock for $LOCK_WAIT s; nothing was changed. Try again shortly."; exit 1; }
    old="$(value_of "$k")"
    do_set "$kv" || { echo "FAILED: the setting was not saved; $FILE is unchanged"; exit 1; }
    if [ ! -d "$ROOT/current" ]; then
      echo "OK: saved. No release is installed yet, so the app was not restarted; the setting takes effect at the first release."; exit 0
    fi
    if restart_app && wait_healthy; then echo "OK: saved, restarted, healthy"; exit 0; fi
    echo "The app is not healthy with $k=[${kv#*=}] (it was [$old]). Restoring the previous value."
    if do_set "$k=$old" && restart_app && wait_healthy; then
      echo "FAILED: $k=[${kv#*=}] made the app unhealthy; restored $k=[$old] and the app is healthy again"; exit 1
    fi
    echo "FAILED: $k=[${kv#*=}] made the app unhealthy and restoring $k=[$old] did not bring it back either (see: journalctl -u julebrus)"; exit 1 ;;
  *) sed -n 2,11p "$0"; exit 1 ;;
esac
