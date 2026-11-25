#!/usr/bin/env bash
# Starts Sky Take-Out on one host: compose.production.yml plus the small-instance settings in
# deploy/aws/compose.ec2.yml. user-data.sh runs it on the first boot of an EC2 instance.
#
# Safe to run again: an existing .env, database and merchant password are kept.
set -euo pipefail
cd "$(dirname "$0")/../.."

# MD5 of "123456", the merchant password that docker/mysql/init/002-demo-admin.sql seeds.
SEEDED_ADMIN_MD5=e10adc3949ba59abbe56e057f20f883e

if ! docker info >/dev/null 2>&1; then
  echo "Cannot reach Docker. Run this script with sudo, or start Docker first." >&2
  exit 1
fi

if [ ! -f .env ]; then
  (
    umask 077
    cat > .env <<EOF
# Written by deploy/aws/setup.sh. Keep it private: it holds the database, Redis and JWT secrets.
COMPOSE_FILE=compose.production.yml:deploy/aws/compose.ec2.yml
COMPOSE_PATH_SEPARATOR=:
DB_PASSWORD=$(openssl rand -hex 16)
MYSQL_ROOT_PASSWORD=$(openssl rand -hex 16)
REDIS_PASSWORD=$(openssl rand -hex 16)
SKY_JWT_ADMIN_SECRET=$(openssl rand -hex 32)
SKY_JWT_USER_SECRET=$(openssl rand -hex 32)
CUSTOMER_PORT=${CUSTOMER_PORT:-80}
ADMIN_PORT=${ADMIN_PORT:-8081}
# The demo has no Stripe account, so customers mark their own orders paid. No money moves.
PAYMENT_SIMULATION_ENABLED=true
EOF
  )
  echo "Wrote .env with new random secrets."
fi

# Pull only the images that are missing, so running the script again never swaps in newer ones.
for image in $(docker compose config --images | tr -d '\r'); do
  docker image inspect "$image" >/dev/null 2>&1 && continue
  if ! docker pull --quiet "$image"; then
    cat >&2 <<EOF
Could not pull $image. If the error says "unauthorized" or "denied", the packages on GitHub
Container Registry are private: make sky-backend, sky-admin-web and sky-customer-web public in
their package settings on GitHub, or run \`docker login ghcr.io\` with a token that can read
packages. Then run this script again.
EOF
    exit 1
  fi
done

# The database first: on an empty volume, MySQL runs the init scripts before it reports healthy.
docker compose up -d --wait --wait-timeout 300 mysql redis

# The seeded merchant account (admin / 123456) must not reach the internet. Replace its password
# before the frontends start. The login upgrades this MD5 hash to BCrypt the first time it is used.
if [ ! -f admin-password.txt ]; then
  password=$(openssl rand -hex 10)
  changed=$(docker compose exec -T mysql sh -c 'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql -uroot -N -B "$MYSQL_DATABASE"' <<EOF | tr -d '\r'
UPDATE employee SET password = MD5('$password') WHERE username = 'admin' AND password = '$SEEDED_ADMIN_MD5';
SELECT ROW_COUNT();
EOF
)
  if [ "$changed" = 1 ]; then
    (umask 077; printf '%s\n' "$password" > admin-password.txt)
    echo "Gave the merchant account 'admin' a random password, saved in $(pwd)/admin-password.txt."
  else
    echo "The merchant account no longer has the seeded password, so it was left as it is."
  fi
fi

docker compose up -d --wait --wait-timeout 600

public_ip() {
  local token
  token=$(curl -s -m 2 -X PUT http://169.254.169.254/latest/api/token \
    -H 'X-aws-ec2-metadata-token-ttl-seconds: 60' 2>/dev/null) || true
  if [ -n "$token" ]; then
    curl -s -m 2 -H "X-aws-ec2-metadata-token: $token" \
      http://169.254.169.254/latest/meta-data/public-ipv4 2>/dev/null || true
  fi
}
host=$(public_ip)
host=${host:-localhost}
customer_port=$(sed -n 's/^CUSTOMER_PORT=//p' .env | tr -d '\r')
admin_port=$(sed -n 's/^ADMIN_PORT=//p' .env | tr -d '\r')

echo
echo "Sky Take-Out is running."
if [ "${customer_port:-80}" = 80 ]; then
  echo "  Customer app:    http://$host/"
else
  echo "  Customer app:    http://$host:$customer_port/"
fi
echo "  Merchant portal: http://$host:${admin_port:-8081}/  (user admin, password in admin-password.txt)"
