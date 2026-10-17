#!/usr/bin/env bash
# One-time setup of a fresh Ubuntu 24.04 EC2 test server. Safe to run again.
#   sudo bash deploy/setup-ec2.sh
# Options (environment variables):
#   AUTO_STOP_MINUTES=180    stop the server this long after every boot, so a forgotten server
#                            doesn't burn credits (0 = off; cancel once with "sudo shutdown -c")
#   DUCKDNS_DOMAIN=name      with DUCKDNS_TOKEN: point name.duckdns.org at this server on every boot
#   DUCKDNS_TOKEN=token      (skip both when using an Elastic IP)
set -euo pipefail

AUTO_STOP_MINUTES="${AUTO_STOP_MINUTES:-180}"
DUCKDNS_DOMAIN="${DUCKDNS_DOMAIN:-}"
DUCKDNS_TOKEN="${DUCKDNS_TOKEN:-}"
APP_USER="${SUDO_USER:-ubuntu}"

if [[ $EUID -ne 0 ]]; then
  echo "Run with sudo." >&2
  exit 1
fi

echo "== Swap (2 GB) =="
if ! swapon --show | grep -q /swapfile; then
  fallocate -l 2G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

echo "== Docker =="
apt-get update -y
apt-get install -y docker.io docker-compose-v2 git curl
systemctl enable --now docker
usermod -aG docker "$APP_USER"

echo "== Auto-stop after ${AUTO_STOP_MINUTES} minutes =="
if [[ "$AUTO_STOP_MINUTES" -gt 0 ]]; then
  cat > /etc/systemd/system/auto-stop.service <<EOF
[Unit]
Description=Stop this test server ${AUTO_STOP_MINUTES} minutes after boot

[Service]
Type=oneshot
ExecStart=/sbin/shutdown -h +${AUTO_STOP_MINUTES} "Auto-stop: test server shutting down"

[Install]
WantedBy=multi-user.target
EOF
  systemctl daemon-reload
  systemctl enable auto-stop.service
else
  systemctl disable auto-stop.service 2>/dev/null || true
fi

if [[ -n "$DUCKDNS_DOMAIN" && -n "$DUCKDNS_TOKEN" ]]; then
  echo "== DuckDNS: ${DUCKDNS_DOMAIN}.duckdns.org =="
  cat > /etc/systemd/system/duckdns.service <<EOF
[Unit]
Description=Point ${DUCKDNS_DOMAIN}.duckdns.org at this server's public IP
Wants=network-online.target
After=network-online.target

[Service]
Type=oneshot
ExecStart=/usr/bin/curl -fsS --retry 5 "https://www.duckdns.org/update?domains=${DUCKDNS_DOMAIN}&token=${DUCKDNS_TOKEN}&ip="

[Install]
WantedBy=multi-user.target
EOF
  chmod 600 /etc/systemd/system/duckdns.service
  systemctl daemon-reload
  systemctl enable --now duckdns.service
fi

echo
echo "Done. Log out and back in (so '$APP_USER' can use docker), then:"
echo "  cd deploy && cp .env.example .env && nano .env"
echo "  docker compose up -d --build"
