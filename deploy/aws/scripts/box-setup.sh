#!/usr/bin/env bash
# One-time box setup for Julebrus. RUN ON THE BOX, in Session Manager, as root (julebrus.sh setup / tls do this for you):
#   sudo bash box-setup.sh            installs the units, the updater, the pre-start script and the nginx site (HTTP),
#                                     and starts the update timer. The app starts when the first release is in the bucket.
#   sudo bash box-setup.sh tls EMAIL  AFTER the DNS A record resolves to this box: gets the Let's Encrypt certificate.
# Needs scripts/ and systemd/ from this folder next to it (copied up through the artifacts bucket, see README).
set -euo pipefail
[ "$(id -u)" = 0 ] || { echo "run as root (sudo)"; exit 1; }
. /etc/julebrus/secrets.conf
HERE="$(cd "$(dirname "$0")" && pwd)"
if [ "${1:-}" = tls ]; then
  EMAIL="${2:?usage: box-setup.sh tls EMAIL}"
  IP="$(curl -fsS -m 3 -H "X-aws-ec2-metadata-token: $(curl -fsS -X PUT -m 3 -H 'X-aws-ec2-metadata-token-ttl-seconds: 60' http://169.254.169.254/latest/api/token)" http://169.254.169.254/latest/meta-data/public-ipv4)"
  getent hosts "$PUBLIC_HOST" | grep -q "$IP" || { echo "STOPPED: $PUBLIC_HOST does not resolve to $IP yet."; exit 1; }
  certbot --nginx -d "$PUBLIC_HOST" --agree-tos -m "$EMAIL" --redirect --non-interactive
  systemctl enable --now certbot-renew.timer   # AL2023 ships the timer disabled: without this the certificate never renews
  systemctl reload nginx; echo "TLS done: https://$PUBLIC_HOST"; exit 0
fi
install -m 0755 "$HERE/julebrus-update.sh" /opt/julebrus/julebrus-update.sh
install -m 0755 "$HERE/julebrus-prestart.sh" /opt/julebrus/julebrus-prestart.sh
install -m 0755 "$HERE/julebrus-env.sh" /opt/julebrus/julebrus-env.sh
install -m 0644 "$HERE"/../systemd/julebrus.service "$HERE"/../systemd/julebrus-update.service "$HERE"/../systemd/julebrus-update.timer /etc/systemd/system/
if [ -e /etc/nginx/conf.d/julebrus.conf ]; then
  echo "kept the existing /etc/nginx/conf.d/julebrus.conf (certbot has edited it: rewriting it would remove the certificate configuration)"
else
cat > /etc/nginx/conf.d/julebrus.conf <<NGINX
server {
  listen 80;
  server_name $PUBLIC_HOST;
  client_max_body_size 2m;
  location / {
    proxy_pass http://127.0.0.1:8080;
    proxy_http_version 1.1;
    proxy_set_header Host \$host;
    proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto \$scheme;
    proxy_set_header X-Forwarded-Host \$host;
    proxy_read_timeout 60s;
  }
}
NGINX
fi
nginx -t && systemctl enable --now nginx && systemctl reload nginx
systemctl daemon-reload
systemctl enable julebrus.service >/dev/null 2>&1 || true
systemctl enable --now julebrus-update.timer
echo "Installed. The app starts once the first release exists in the artifacts bucket (the timer polls it every 5 minutes)."
echo "First release now:  systemctl start julebrus-update.service ; journalctl -u julebrus-update -n 20 --no-pager"
