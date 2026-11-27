#!/usr/bin/env bash
# Prepares an empty MySQL-compatible database on the internet, such as a TiDB Cloud Starter cluster,
# for the Render deployment: the schema and demo menu from docker/mysql/init, a user for the backend
# that can only read and change rows, and a random password for the merchant account. Run it once,
# from a clone of this repository, on any machine with Docker (on Windows, in Git Bash):
#
#   bash deploy/init-database.sh
#
# It asks for the cluster's host, user and password, or reads DB_HOST, DB_USERNAME and DB_PASSWORD;
# DB_PORT defaults to 4000, TiDB's port. The mysql client runs from the mysql:8.0 image, so nothing
# else needs installing. The connection is encrypted and the server's certificate checked; set
# DB_SSL_MODE=DISABLED only for a test database on your own machine. See docs/DEPLOY.md.
set -euo pipefail
cd "$(dirname "$0")/.."

DB_PORT=${DB_PORT:-4000}
DB_NAME=${DB_NAME:-sky_take_out}
DB_SSL_MODE=${DB_SSL_MODE:-VERIFY_IDENTITY}
# MD5 of "123456", the merchant password that docker/mysql/init/002-demo-admin.sql seeds.
SEEDED_ADMIN_MD5=e10adc3949ba59abbe56e057f20f883e

[ -n "${DB_HOST:-}" ] || read -r -p "Host: " DB_HOST
[ -n "${DB_USERNAME:-}" ] || read -r -p "User: " DB_USERNAME
if [ -z "${DB_PASSWORD+set}" ]; then
  read -r -s -p "Password: " DB_PASSWORD
  echo
fi

# The password reaches the client through the environment, not the command line.
export MYSQL_PWD=$DB_PASSWORD
# Git Bash would rewrite the certificate path below into a Windows path.
export MSYS_NO_PATHCONV=1
tls=(--ssl-mode="$DB_SSL_MODE")
if [ "$DB_SSL_MODE" != DISABLED ]; then
  tls+=(--ssl-ca=/etc/pki/tls/certs/ca-bundle.crt)
fi

sql() {
  docker run --rm -i -e MYSQL_PWD mysql:8.0 mysql --host="$DB_HOST" --port="$DB_PORT" \
    --user="$DB_USERNAME" "${tls[@]}" --batch --skip-column-names "$@"
}

tables=$(sql -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '$DB_NAME'")
if [ "$tables" != 0 ]; then
  echo "Database $DB_NAME already has $tables tables, and 001-demo-seed.sql would drop them." >&2
  echo "This script only fills an empty database. To start over, first run: DROP DATABASE $DB_NAME;" >&2
  exit 1
fi

echo "Creating the schema and demo menu in $DB_NAME..."
sql -e "CREATE DATABASE IF NOT EXISTS \`$DB_NAME\`"
for file in docker/mysql/init/0*.sql; do
  sql "$DB_NAME" < "$file"
done

# TiDB Cloud Starter requires every user name to start with the cluster's prefix, the part of the
# given user name before the dot.
case $DB_USERNAME in
  *.*) app_user="${DB_USERNAME%%.*}.sky_app" ;;
  *) app_user=sky_app ;;
esac
app_password=$(openssl rand -hex 16)
admin_password=$(openssl rand -hex 10)
# The seeded merchant account (admin / 123456) must not reach the internet. The login upgrades this
# MD5 hash to BCrypt the first time it is used.
sql "$DB_NAME" <<EOF
CREATE USER IF NOT EXISTS '$app_user'@'%' IDENTIFIED BY '$app_password';
ALTER USER '$app_user'@'%' IDENTIFIED BY '$app_password';
GRANT SELECT, INSERT, UPDATE, DELETE ON \`$DB_NAME\`.* TO '$app_user'@'%';
UPDATE employee SET password = MD5('$admin_password') WHERE username = 'admin' AND password = '$SEEDED_ADMIN_MD5';
EOF
(umask 077; printf '%s\n' "$admin_password" > admin-password.txt)

cat <<EOF

$DB_NAME is ready. When Render asks for the backend's database settings, enter:
  DB_HOST      $DB_HOST
  DB_USERNAME  $app_user
  DB_PASSWORD  $app_password
That user can read and change rows in $DB_NAME and nothing else.

The merchant account is admin, with the password $admin_password,
also saved in $(pwd)/admin-password.txt.
EOF
