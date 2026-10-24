#!/usr/bin/env bash
# Julebrus box: reach it with AWS SSM Session Manager only (no SSH, no key pair). Run on the operator's laptop.
# Needs session-manager-plugin and an AWS profile allowed to ssm:StartSession on the instance and to read the stack outputs.
#   PROFILE=<aws-profile>  (required; or AWS_PROFILE)   JULEBRUS_AWS_DIR=<folder with scripts/ and systemd/>  (default: one up from this script)
#
#   julebrus.sh setup           [mutating, type SETUP] upload scripts/ and systemd/ to the artifacts bucket, run the one-time box setup
#   julebrus.sh tls EMAIL       [mutating, type TLS] AFTER the DNS A record resolves to the box: get the Let's Encrypt certificate
#   julebrus.sh status          current version, services, timers, health, disk
#   julebrus.sh update-log [N]  the updater's own log, the releases on disk and the app's service status
#   julebrus.sh diag            health with detail and the key lines of the app log (no stack frames)
#   julebrus.sh update          [mutating, type UPDATE] pull and install the latest release now, then show its log
#   julebrus.sh env keys        which non-secret settings are set (never the values, never the file)
#   julebrus.sh env set KEY=VALUE   [mutating, type ENV] change JULEBRUS_ALLOWED_DOMAINS, JULEBRUS_ADMINS or
#                               JULEBRUS_CONTACT_EMAIL, then restart the app and wait for /actuator/health
# Session Manager's start-session does not return the exit status of the remote command. So these modes never print a
# success line of their own; each ends with a separate remote check that prints its own OK/FAILED line. Read it.
set -euo pipefail
PROFILE="${PROFILE:-${AWS_PROFILE:-}}"; [ -n "$PROFILE" ] || { echo "Set PROFILE (or AWS_PROFILE) to the AWS CLI profile to use."; exit 1; }
REGION="${REGION:-eu-north-1}"; STACK="${STACK:-julebrus}"
SRC="${JULEBRUS_AWS_DIR:-$(cd "$(dirname "$0")/.." && pwd)}"
A=(aws --profile "$PROFILE" --region "$REGION")
out() { "${A[@]}" cloudformation describe-stacks --stack-name "$STACK" --query "Stacks[0].Outputs[?OutputKey=='$1'].OutputValue" --output text; }
confirm() { local ans; read -r -p "$1 Type $2 to continue (anything else cancels): " ans </dev/tty; [ "$ans" = "$2" ] || { echo "Cancelled."; exit 0; }; }
MODE="${1:-help}"
case "$MODE" in setup|tls|status|update-log|diag|update|env) ;; *) sed -n 2,17p "$0"; exit 1 ;; esac
ID="$(out InstanceId 2>/dev/null || true)"
[ -n "$ID" ] && [ "$ID" != None ] || { echo "No stack '$STACK' (or no InstanceId output) in profile '$PROFILE', $REGION: deploy it first (scripts/00-provision.sh)."; exit 1; }
S() { "${A[@]}" ssm start-session --target "$ID" "$@"; }
X() { S --document-name AWS-StartInteractiveCommand --parameters "command=[\"$1\"]"; }   # interactive, so prompts work
# Every command with quotes or $(...) goes as base64, so no quoting layer can break it.
XB() { X "echo $(printf '%s' "$1" | base64 -w0) | base64 -d | bash"; }
case "$MODE" in
  status)  XB 'echo "version: $(basename "$(readlink /opt/julebrus/current 2>/dev/null)")"; echo "on disk (oldest first): $(ls -1 /opt/julebrus/releases 2>/dev/null | sort -V | tr "\n" " ")"; echo "health: $(curl -s -m 3 http://127.0.0.1:8080/actuator/health)"; systemctl is-active julebrus nginx; systemctl list-timers julebrus-update.timer --no-pager; df -h / | tail -1' ;;
  setup)
    [ -d "$SRC/scripts" ] && [ -d "$SRC/systemd" ] || { echo "Cannot find scripts/ and systemd/ in $SRC (set JULEBRUS_AWS_DIR)."; exit 1; }
    B="$(out ArtifactsBucketName 2>/dev/null || true)"
    [[ "$B" =~ ^julebrus-artifacts-[0-9]{12}$ ]] || { echo "Stack output ArtifactsBucketName is '$B', not a julebrus artifacts bucket: not continuing."; exit 1; }
    confirm "This uploads $SRC/scripts and systemd to s3://$B/deploy and installs them on $ID (nginx, units, update timer)." SETUP
    "${A[@]}" s3 sync "$SRC/scripts" "s3://$B/deploy/scripts" --delete --quiet
    "${A[@]}" s3 sync "$SRC/systemd" "s3://$B/deploy/systemd" --delete --quiet
    echo "uploaded scripts/ and systemd/ to s3://$B/deploy/ ; running the one-time setup on the box"
    XB "sudo bash -c 'rm -rf /root/deploy && aws s3 sync s3://$B/deploy /root/deploy --quiet && bash /root/deploy/scripts/box-setup.sh'"
    echo "--- check"
    XB 'if systemctl is-enabled julebrus-update.timer >/dev/null 2>&1 && test -x /opt/julebrus/julebrus-update.sh && sudo nginx -t >/dev/null 2>&1; then echo "OK: timer enabled, updater installed, nginx config valid"; else echo "FAILED: setup did not complete; read the output above"; fi' ;;
  tls)
    EMAIL="${2:-}"
    [[ "$EMAIL" =~ ^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$ ]] || { echo "usage: $0 tls EMAIL (a plain e-mail address)"; exit 1; }
    confirm "This asks Let's Encrypt for a certificate for the app's host name (the DNS A record must already point at the box); contact e-mail: $EMAIL." TLS
    XB "sudo bash /root/deploy/scripts/box-setup.sh tls '$EMAIL'"
    echo "--- check"
    XB 'h=$(. /etc/julebrus/secrets.conf; echo $PUBLIC_HOST); if sudo test -d /etc/letsencrypt/live/$h && grep -q "listen 443" /etc/nginx/conf.d/julebrus.conf; then echo "OK: certificate present for $h and nginx serves 443"; else echo "FAILED: no certificate for $h; read the output above"; fi' ;;
  update)
    confirm "This pulls the release named in latest.json and restarts the app on $ID (rolls back if it does not start or /actuator/health fails)." UPDATE
    XB 'sudo systemctl start julebrus-update.service; sudo journalctl -u julebrus-update -n 40 --no-pager; echo "--- check"; if [ "$(systemctl show -p Result --value julebrus-update.service)" = success ]; then echo "OK: the update run finished without error; version $(basename "$(readlink /opt/julebrus/current)")"; else echo "FAILED: the update run reported an error (see the log above)"; fi' ;;
  update-log) XB "sudo journalctl -u julebrus-update -n ${2:-60} --no-pager; echo; echo current: \$(readlink /opt/julebrus/current 2>/dev/null); ls -l /opt/julebrus/releases 2>/dev/null; echo; sudo systemctl status julebrus --no-pager -l | head -20" ;;
  diag)    XB 'echo "--- health (with detail):"; curl -s -m 5 -i http://127.0.0.1:8080/actuator/health | head -8; echo; echo "--- key log lines (no stack frames):"; sudo journalctl -u julebrus -n 1500 --no-pager | grep -v -E "java\[[0-9]+\]:[[:space:]]+(at |\.\.\. [0-9]+ )" | grep -i -E "Started Julebrus|Flyway|Migrat|health|oauth|Caused by|ERROR|WARN|Exception|prestart" | cut -c1-260 | tail -40' ;;
  env)
    case "${2:-}" in
      keys) XB 'sudo /opt/julebrus/julebrus-env.sh keys' ;;
      set)
        kv="${3:-}"; k="${kv%%=*}"; v="${kv#*=}"
        [[ "$kv" == *=* ]] || { echo "usage: $0 env set KEY=VALUE"; exit 1; }
        case "$k" in JULEBRUS_ALLOWED_DOMAINS|JULEBRUS_ADMINS|JULEBRUS_CONTACT_EMAIL) ;; *) echo "Key not allowed: $k"; exit 1 ;; esac
        [[ "$v" =~ ^[A-Za-z0-9@._,+:/-]*$ ]] || { echo "Value may only contain letters, digits and @ . _ , + : / -"; exit 1; }
        confirm "This sets $k on $ID and restarts the app (a short outage)." ENV
        XB "sudo /opt/julebrus/julebrus-env.sh set '$k=$v' && sudo systemctl restart julebrus; for i in \$(seq 1 40); do c=\$(curl -s -o /dev/null -m 3 -w '%{http_code}' http://127.0.0.1:8080/actuator/health); echo \"health \$c\"; [ \"\$c\" = 200 ] && echo 'OK: the app is healthy' && break; [ \"\$i\" = 40 ] && echo 'FAILED: the app is not healthy after the restart'; sleep 5; done" ;;
      *) echo "usage: $0 env keys | env set KEY=VALUE"; exit 1 ;;
    esac ;;
esac
