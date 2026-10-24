#!/usr/bin/env bash
# Edit the NON-SECRET app settings in /etc/julebrus/app.env. Runs on the box as root (installed by box-setup.sh).
#   julebrus-env.sh keys             list the allowed keys and whether each is set (never the values, never the file)
#   julebrus-env.sh set KEY=VALUE    change one allowed key (no restart)
#   julebrus-env.sh apply KEY=VALUE  set, then restart the app and wait for /actuator/health. With no release installed
#                                    yet it only saves the setting (it takes effect at the first release).
# Only the keys below can be changed; the value may contain letters, digits and @ . _ , + : / - only.
# `apply` ends with one OK: or FAILED: line and exits non-zero on failure.
set -euo pipefail
FILE="${JULEBRUS_APP_ENV:-/etc/julebrus/app.env}"; ROOT="${JULEBRUS_ROOT:-/opt/julebrus}"
HEALTH_URL="${JULEBRUS_HEALTH_URL:-http://127.0.0.1:8080/actuator/health}"
TRIES="${JULEBRUS_HEALTH_TRIES:-40}"; PAUSE="${JULEBRUS_HEALTH_PAUSE:-5}"
ALLOWED=(JULEBRUS_ALLOWED_DOMAINS JULEBRUS_ADMINS JULEBRUS_CONTACT_EMAIL)
do_set() {
  local kv="$1" k v tmp ok=0 a
  [[ "$kv" == *=* ]] || { echo "expected KEY=VALUE" >&2; return 1; }
  k="${kv%%=*}"; v="${kv#*=}"
  for a in "${ALLOWED[@]}"; do [ "$a" = "$k" ] && ok=1; done
  [ "$ok" = 1 ] || { echo "key not allowed: $k (allowed: ${ALLOWED[*]})" >&2; return 1; }
  [[ "$v" =~ ^[A-Za-z0-9@._,+:/-]*$ ]] || { echo "value has characters outside A-Za-z0-9 @ . _ , + : / -" >&2; return 1; }
  tmp="$(mktemp "$FILE.XXXXXX")"
  { grep -v "^$k=" "$FILE" || true; printf '%s=%s\n' "$k" "$v"; } > "$tmp"
  chmod --reference="$FILE" "$tmp"; chown --reference="$FILE" "$tmp" 2>/dev/null || true
  mv "$tmp" "$FILE"; echo "$k updated"
}
case "${1:-}" in
  keys)
    for k in "${ALLOWED[@]}"; do
      if grep -q "^$k=." "$FILE"; then echo "$k: set"; else echo "$k: empty"; fi
    done ;;
  set) do_set "${2:?usage: julebrus-env.sh set KEY=VALUE}" ;;
  apply)
    do_set "${2:?usage: julebrus-env.sh apply KEY=VALUE}" || { echo "FAILED: the setting was not saved"; exit 1; }
    if [ ! -d "$ROOT/current" ]; then
      echo "OK: saved. No release is installed yet, so the app was not restarted; the setting takes effect at the first release."; exit 0
    fi
    systemctl reset-failed julebrus.service 2>/dev/null || true
    systemctl restart julebrus.service || { echo "FAILED: saved, but the restart failed (see: journalctl -u julebrus)"; exit 1; }
    for i in $(seq 1 "$TRIES"); do
      if curl -fsS -m 3 "$HEALTH_URL" >/dev/null 2>&1; then echo "OK: saved, restarted, healthy"; exit 0; fi
      sleep "$PAUSE"
    done
    echo "FAILED: saved and restarted, but the app is not healthy (see: journalctl -u julebrus)"; exit 1 ;;
  *) sed -n 2,9p "$0"; exit 1 ;;
esac
