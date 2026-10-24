#!/usr/bin/env bash
# The Google OAuth client of Julebrus, kept in Secrets Manager as julebrus/google-oauth {client-id, client-secret}.
# Run on the OPERATOR's laptop. The values are typed at a hidden prompt (read -rs): never echoed, never in shell
# history, never in a command line, never printed.
#   PROFILE=<aws-profile> bash google-oauth.sh create    store the client id and secret (asks to replace if the secret exists)
#   PROFILE=<aws-profile> bash google-oauth.sh status    only says whether the secret exists and when it was last changed
# The app fetches the values at every start: after a change, restart it (julebrus.sh env set ... restarts it too).
set -euo pipefail
PROFILE="${PROFILE:-${AWS_PROFILE:-}}"; [ -n "$PROFILE" ] || { echo "Set PROFILE (or AWS_PROFILE) to the AWS CLI profile to use."; exit 1; }
REGION="${REGION:-eu-north-1}"; NAME="${SECRET_NAME:-julebrus/google-oauth}"
A=(aws --profile "$PROFILE" --region "$REGION")
exists() {
  local err
  if err="$("${A[@]}" secretsmanager describe-secret --secret-id "$NAME" --query Name --output text 2>&1 >/dev/null)"; then return 0; fi
  grep -q ResourceNotFoundException <<<"$err" && return 1
  echo "Could not check the secret: ${err%%$'\n'*}" >&2; exit 1
}
case "${1:-help}" in
  status)
    if exists; then
      echo "$NAME exists; last changed: $("${A[@]}" secretsmanager describe-secret --secret-id "$NAME" --query 'LastChangedDate' --output text)"
    else echo "$NAME does not exist"; fi ;;
  create)
    replace=0
    if exists; then
      read -r -p "$NAME already exists. Type REPLACE to overwrite it with new values: " ANSWER </dev/tty
      [ "$ANSWER" = REPLACE ] || { echo "Cancelled."; exit 0; }; replace=1
    fi
    read -rs -p "Google client id (hidden): " CID </dev/tty; echo
    read -rs -p "Google client secret (hidden): " CSEC </dev/tty; echo
    [ -n "$CID" ] && [ -n "$CSEC" ] || { echo "Both values are required."; exit 1; }
    [[ "$CID" == *.apps.googleusercontent.com ]] || echo "Note: a Google client id normally ends in .apps.googleusercontent.com"
    # The JSON is built from the environment and piped on stdin, so the values never appear in an argument list.
    json() { CID="$CID" CSEC="$CSEC" jq -n '{"client-id": env.CID, "client-secret": env.CSEC}'; }
    if [ "$replace" = 1 ]; then
      json | "${A[@]}" secretsmanager put-secret-value --secret-id "$NAME" --secret-string file:///dev/stdin --query Name --output text
    else
      json | "${A[@]}" secretsmanager create-secret --name "$NAME" --description "Julebrus Google OAuth client" \
        --tags Key=Project,Value=julebrus --secret-string file:///dev/stdin --query Name --output text
    fi
    unset CID CSEC
    host="$("${A[@]}" cloudformation describe-stacks --stack-name "${STACK:-julebrus}" --query "Stacks[0].Outputs[?OutputKey=='AppHostName'].OutputValue" --output text 2>/dev/null || true)"
    echo "Stored. Redirect URI to register at Google: https://${host:-<AppHostName of the stack>}/login/oauth2/code/google" ;;
  *) sed -n 2,8p "$0"; exit 1 ;;
esac
