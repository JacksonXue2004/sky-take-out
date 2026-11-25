#!/bin/bash
# First-boot setup for Sky Take-Out on one EC2 instance running Amazon Linux 2023 (x86_64).
# Paste this whole file into "User data" when launching the instance. cloud-init runs it once, as
# root, and writes its output to /var/log/cloud-init-output.log. See docs/DEPLOY-AWS.md.
set -euo pipefail
export HOME=${HOME:-/root}

REPO_URL=https://github.com/JacksonXue2004/sky-take-out.git
REPO_REF=main
APP_DIR=/opt/sky-take-out
# Amazon Linux 2023 does not package the Compose plugin, so it comes from the Docker release,
# pinned and checked against the checksum published with it.
COMPOSE_VERSION=v5.5.1
COMPOSE_SHA256=db1889184726840f75c4f9c001048430d4f25b3be3cb084d3ddd762bc0aed576
# Swap lets a memory spike slow a 1 GiB instance down instead of killing MySQL or the JVM.
SWAP_MB=2048

if [ "$(uname -m)" != x86_64 ]; then
  echo "The published images are built for linux/amd64. Use an x86_64 type such as t3.micro." >&2
  exit 1
fi

dnf install -y docker git openssl

# Rotate container logs so months of request logs cannot fill the disk.
mkdir -p /etc/docker
cat > /etc/docker/daemon.json <<'EOF'
{ "log-driver": "json-file", "log-opts": { "max-size": "10m", "max-file": "3" } }
EOF
systemctl enable --now docker

plugin=/usr/local/lib/docker/cli-plugins/docker-compose
if [ ! -x "$plugin" ]; then
  mkdir -p "$(dirname "$plugin")"
  curl -fsSL -o "$plugin" \
    "https://github.com/docker/compose/releases/download/$COMPOSE_VERSION/docker-compose-linux-x86_64"
  echo "$COMPOSE_SHA256  $plugin" | sha256sum -c -
  chmod 755 "$plugin"
fi

if [ "$SWAP_MB" -gt 0 ] && ! swapon --show=NAME --noheadings | grep -qx /swapfile; then
  dd if=/dev/zero of=/swapfile bs=1M count="$SWAP_MB" status=none
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  grep -q '^/swapfile ' /etc/fstab || echo '/swapfile none swap defaults 0 0' >> /etc/fstab
  # Use swap only when memory is nearly full.
  echo 'vm.swappiness=10' > /etc/sysctl.d/90-sky-take-out.conf
  sysctl -q -p /etc/sysctl.d/90-sky-take-out.conf
fi

if [ ! -d "$APP_DIR/.git" ]; then
  git clone --depth 1 --branch "$REPO_REF" "$REPO_URL" "$APP_DIR"
fi
bash "$APP_DIR/deploy/aws/setup.sh"
