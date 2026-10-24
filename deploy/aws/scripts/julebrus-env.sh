#!/usr/bin/env bash
# Edit the NON-SECRET app settings in /etc/julebrus/app.env. Runs on the box as root (installed by box-setup.sh).
#   julebrus-env.sh keys            list the allowed keys and whether each is set (never the values, never the file)
#   julebrus-env.sh set KEY=VALUE   change one allowed key; then restart the app yourself (julebrus.sh env does it)
# Only the keys below can be changed; the value may contain letters, digits and @ . _ , + : / - only.
set -euo pipefail
FILE="${JULEBRUS_APP_ENV:-/etc/julebrus/app.env}"
ALLOWED=(JULEBRUS_ALLOWED_DOMAINS JULEBRUS_ADMINS JULEBRUS_CONTACT_EMAIL)
case "${1:-}" in
  keys)
    for k in "${ALLOWED[@]}"; do
      if grep -q "^$k=." "$FILE"; then echo "$k: set"; else echo "$k: empty"; fi
    done ;;
  set)
    kv="${2:?usage: julebrus-env.sh set KEY=VALUE}"; k="${kv%%=*}"; v="${kv#*=}"
    [[ "$kv" == *=* ]] || { echo "expected KEY=VALUE" >&2; exit 1; }
    ok=0; for a in "${ALLOWED[@]}"; do [ "$a" = "$k" ] && ok=1; done
    [ "$ok" = 1 ] || { echo "key not allowed: $k (allowed: ${ALLOWED[*]})" >&2; exit 1; }
    [[ "$v" =~ ^[A-Za-z0-9@._,+:/-]*$ ]] || { echo "value has characters outside A-Za-z0-9 @ . _ , + : / -" >&2; exit 1; }
    tmp="$(mktemp "$FILE.XXXXXX")"
    { grep -v "^$k=" "$FILE" || true; printf '%s=%s\n' "$k" "$v"; } > "$tmp"
    chmod --reference="$FILE" "$tmp"; chown --reference="$FILE" "$tmp" 2>/dev/null || true
    mv "$tmp" "$FILE"; echo "$k updated" ;;
  *) sed -n 2,6p "$0"; exit 1 ;;
esac
