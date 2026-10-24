#!/usr/bin/env bash
# Runs as root before every start of julebrus.service (ExecStartPre=+). Builds /run/julebrus/secrets.env
# (tmpfs, 0640 root:julebrus) from Secrets Manager: the app only reads plain environment variables.
#   - JULEBRUS_DB_USER / JULEBRUS_DB_PASSWORD from the RDS-managed master secret.
#   - GOOGLE_CLIENT_ID / GOOGLE_CLIENT_SECRET from the secret julebrus/google-oauth ({client-id, client-secret}).
# Either secret missing or unreadable stops the start with a clear line in the journal (the app cannot start
# without a Google client id). Values are never printed.
set -euo pipefail
# The directories are overridable only so the script can be tested without root.
CONF="${JULEBRUS_CONF_DIR:-/etc/julebrus}"; RUN="${JULEBRUS_RUN_DIR:-/run/julebrus}"
. "$CONF/secrets.conf"
. "$CONF/app.env" 2>/dev/null || true
REGION="${AWS_REGION:-eu-north-1}"
install -d -m 0750 "$RUN"; [ "$(id -u)" = 0 ] && chgrp julebrus "$RUN"
out="$(mktemp -p "$RUN")"; chmod 0640 "$out"; [ "$(id -u)" = 0 ] && chgrp julebrus "$out"
trap 'rm -f "$out"' ERR
sm() { aws secretsmanager get-secret-value --region "$REGION" --secret-id "$1" --query SecretString --output text 2>/dev/null; }
# systemd EnvironmentFile: double-quoted value, backslash and double quote escaped; no $ or % expansion happens there.
q() { local v="$1"; v="${v//\\/\\\\}"; v="${v//\"/\\\"}"; printf '"%s"' "$v"; }

db="$(sm "$DB_SECRET_ARN")" || { echo "prestart: cannot read the database secret" >&2; exit 1; }
g="$(sm "$GOOGLE_SECRET_ID")" || { echo "prestart: cannot read the secret $GOOGLE_SECRET_ID (create it with google-oauth.sh create)" >&2; exit 1; }
gid="$(jq -r '."client-id" // empty' <<<"$g")"; gsec="$(jq -r '."client-secret" // empty' <<<"$g")"
[ -n "$gid" ] && [ -n "$gsec" ] || { echo "prestart: $GOOGLE_SECRET_ID lacks client-id or client-secret" >&2; exit 1; }
{
  printf 'JULEBRUS_DB_USER=%s\n' "$(q "$(jq -r .username <<<"$db")")"
  printf 'JULEBRUS_DB_PASSWORD=%s\n' "$(q "$(jq -r .password <<<"$db")")"
  printf 'GOOGLE_CLIENT_ID=%s\n' "$(q "$gid")"
  printf 'GOOGLE_CLIENT_SECRET=%s\n' "$(q "$gsec")"
} >> "$out"
mv "$out" "$RUN/secrets.env"
