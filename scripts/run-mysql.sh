#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -f .env ]]; then
  set -a
  source .env
  set +a
fi
: "${DB_PASSWORD:?请配置 DB_PASSWORD}"
: "${APP_PASSWORD:?请配置 APP_PASSWORD}"
export DB_PASSWORD APP_PASSWORD
export DB_PORT="${DB_PORT:-13306}" REDIS_PORT="${REDIS_PORT:-16379}"
export SPRING_PROFILES_ACTIVE=mysql
exec java -jar target/smart-ticket-1.0.0.jar
