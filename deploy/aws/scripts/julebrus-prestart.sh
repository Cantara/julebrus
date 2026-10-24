#!/usr/bin/env bash
# Runs as root before every start of julebrus.service (ExecStartPre=+). Builds /run/julebrus/secrets.env
# (tmpfs, root-only 0600; systemd reads EnvironmentFile as root) from Secrets Manager: the app only reads plain environment variables.
#   - JULEBRUS_DB_USER / JULEBRUS_DB_PASSWORD from the secret julebrus/db ({username, password}; stable, not rotated).
#   - GOOGLE_CLIENT_ID / GOOGLE_CLIENT_SECRET from the secret julebrus/google-oauth ({client-id, client-secret}).
# A secret that is missing, unreadable or lacks a field stops the start with a clear line in the journal (the app cannot
# start without them). Values are never printed.
set -euo pipefail
# The directories are overridable only so the script can be tested without root.
CONF="${JULEBRUS_CONF_DIR:-/etc/julebrus}"; RUN="${JULEBRUS_RUN_DIR:-/run/julebrus}"
. "$CONF/secrets.conf"
. "$CONF/app.env" 2>/dev/null || true
REGION="${AWS_REGION:-eu-north-1}"
install -d -m 0700 "$RUN"
out="$(mktemp -p "$RUN")"; chmod 0600 "$out"
trap 'rm -f "$out"' EXIT
sm() { aws secretsmanager get-secret-value --region "$REGION" --secret-id "$1" --query SecretString --output text 2>/dev/null; }
fail() { echo "prestart: $1" >&2; exit 1; }
# systemd EnvironmentFile: double-quoted value, backslash and double quote escaped; no $ or % expansion happens there.
q() { local v="$1"; v="${v//\\/\\\\}"; v="${v//\"/\\\"}"; printf '"%s"' "$v"; }

db="$(sm "$DB_SECRET_ID")" || fail "cannot read the secret $DB_SECRET_ID"
g="$(sm "$GOOGLE_SECRET_ID")" || fail "cannot read the secret $GOOGLE_SECRET_ID (create it with google-oauth.sh create)"
dbu="$(jq -er '.username | strings | select(length>0)' <<<"$db")" || fail "$DB_SECRET_ID lacks a username"
dbp="$(jq -er '.password | strings | select(length>0)' <<<"$db")" || fail "$DB_SECRET_ID lacks a password"
gid="$(jq -er '."client-id" | strings | select(length>0)' <<<"$g")" || fail "$GOOGLE_SECRET_ID lacks client-id"
gsec="$(jq -er '."client-secret" | strings | select(length>0)' <<<"$g")" || fail "$GOOGLE_SECRET_ID lacks client-secret"
{
  printf 'JULEBRUS_DB_USER=%s\n' "$(q "$dbu")"
  printf 'JULEBRUS_DB_PASSWORD=%s\n' "$(q "$dbp")"
  printf 'GOOGLE_CLIENT_ID=%s\n' "$(q "$gid")"
  printf 'GOOGLE_CLIENT_SECRET=%s\n' "$(q "$gsec")"
} >> "$out"
mv "$out" "$RUN/secrets.env"
