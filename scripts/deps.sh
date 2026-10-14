#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# 只加载用户本地配置，不在源码中保存密码
if [[ -f .env ]]; then
  set -a
  source .env
  set +a
fi
case "${1:-up}" in
  up)
    : "${DB_PASSWORD:?请复制 .env.example 为 .env 并配置密码}"
    : "${MYSQL_ROOT_PASSWORD:?请配置 MYSQL_ROOT_PASSWORD}"
    export MYSQL_PASSWORD="$DB_PASSWORD" MYSQL_ROOT_PASSWORD
    podman run -d --name smart-ticket-mysql \
      -p "127.0.0.1:${DB_PORT:-13306}:3306" \
      -e MYSQL_DATABASE=ticket -e MYSQL_USER=ticket -e MYSQL_PASSWORD -e MYSQL_ROOT_PASSWORD \
      -v smart-ticket-mysql-data:/var/lib/mysql docker.io/library/mysql:8.4
    podman run -d --name smart-ticket-redis \
      -p "127.0.0.1:${REDIS_PORT:-16379}:6379" \
      docker.io/library/redis:7.4-alpine redis-server --save '' --appendonly no
    echo '依赖已启动。首次 MySQL 初始化需等待约 30 秒。'
    ;;
  down)
    # 保留数据库卷，避免停止服务时删除演示数据
    podman rm -f smart-ticket-mysql smart-ticket-redis
    ;;
  *) echo '用法：bash scripts/deps.sh up|down' >&2; exit 2 ;;
esac
