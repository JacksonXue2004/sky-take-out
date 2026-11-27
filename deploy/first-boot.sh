#!/bin/bash
# First-boot setup for Sky Take-Out on one fresh x86_64 VM running Ubuntu (Azure, GCP and most
# others) or Amazon Linux 2023 (AWS). Pass the whole file as the VM's user data or custom data;
# deploy/azure/launch.sh and deploy/aws/launch.sh do that. cloud-init runs it once, as root, and
# writes its output to /var/log/cloud-init-output.log. See docs/DEPLOY.md.
set -euo pipefail
export HOME=${HOME:-/root}

REPO_URL=https://github.com/JacksonXue2004/sky-take-out.git
REPO_REF=main
APP_DIR=/opt/sky-take-out
# The Compose plugin comes from the Docker release, pinned and checked against the checksum
# published with it, so every distribution runs the same version.
COMPOSE_VERSION=v5.5.1
COMPOSE_SHA256=db1889184726840f75c4f9c001048430d4f25b3be3cb084d3ddd762bc0aed576
# Swap lets a memory spike slow a 1 GiB VM down instead of killing MySQL or the JVM.
SWAP_MB=2048

if [ "$(uname -m)" != x86_64 ]; then
  echo "The published images are built for linux/amd64. Use an x86_64 VM size." >&2
  exit 1
fi

# Rotate container logs so months of request logs cannot fill the disk. Written before Docker
# is installed, because Ubuntu's package starts the daemon straight away.
mkdir -p /etc/docker
cat > /etc/docker/daemon.json <<'EOF'
{ "log-driver": "json-file", "log-opts": { "max-size": "10m", "max-file": "3" } }
EOF

if command -v apt-get >/dev/null; then
  export DEBIAN_FRONTEND=noninteractive
  # A new Ubuntu VM often runs its own package updates at first boot; wait for their locks.
  for attempt in 1 2 3 4 5; do
    apt-get -o DPkg::Lock::Timeout=300 update -q && break
    sleep 15
  done
  apt-get -o DPkg::Lock::Timeout=300 install -y -q docker.io git openssl curl
elif command -v dnf >/dev/null; then
  dnf install -y docker git openssl
else
  echo "No apt-get or dnf on this image. Use Ubuntu or Amazon Linux 2023." >&2
  exit 1
fi
systemctl enable docker
systemctl restart docker

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
bash "$APP_DIR/deploy/setup.sh"
