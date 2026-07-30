#!/bin/bash
# 上传 package 后在服务器执行：bash up.sh
# 作用：修权限、校验 dist、加载离线镜像、重建容器（比裸 compose up --build 完整）
set -euo pipefail
cd "$(dirname "$0")"
COMPOSE="docker compose -f docker-compose.yaml -f docker-compose.prod.yml"
LOG_ROOT="${FORUM_LOG_DIR:-../logs}"
echo "Forum package release: 20260722-mq-healthcheck-v2"

read_env() {
  local key="$1" line value
  line="$(grep -E "^${key}=" .env 2>/dev/null | tail -1 || true)"
  value="${line#*=}"
  value="${value//$'\r'/}"
  value="${value%\"}"; value="${value#\"}"
  printf '%s' "$value"
}

wait_rabbitmq() {
  local i
  for i in $(seq 1 90); do
    # ping 只代表 Erlang 运行时可连通；用户与 vhost 管理必须等待 RabbitMQ 应用本身启动完成。
    if docker exec forum-rabbitmq rabbitmq-diagnostics -q check_running >/dev/null 2>&1; then
      echo "RabbitMQ application ready"
      return 0
    fi
    sleep 2
  done
  echo "ERROR: RabbitMQ application did not become ready within 180 seconds"
  docker logs --tail 100 forum-rabbitmq >&2 || true
  return 1
}

sync_rabbitmq_credentials() {
  local user password vhost
  user="$(read_env RABBITMQ_USER)"
  password="$(read_env RABBITMQ_PASSWORD)"
  vhost="$(read_env RABBITMQ_VHOST)"
  user="${user:-nuonuo}"
  vhost="${vhost:-forum-demo}"
  [[ -n "$password" ]] || { echo "ERROR: RABBITMQ_PASSWORD is empty"; return 1; }

  wait_rabbitmq
  if docker exec forum-rabbitmq rabbitmqctl list_users -q | cut -f1 | grep -Fxq "$user"; then
    docker exec forum-rabbitmq rabbitmqctl change_password "$user" "$password" >/dev/null
  else
    docker exec forum-rabbitmq rabbitmqctl add_user "$user" "$password" >/dev/null
  fi
  docker exec forum-rabbitmq rabbitmqctl add_vhost "$vhost" >/dev/null 2>&1 || true
  docker exec forum-rabbitmq rabbitmqctl set_permissions -p "$vhost" "$user" ".*" ".*" ".*" >/dev/null
  docker exec forum-rabbitmq rabbitmqctl set_user_tags "$user" administrator >/dev/null
  docker exec forum-rabbitmq rabbitmqctl authenticate_user "$user" "$password" >/dev/null
  echo "RabbitMQ credentials synchronized"
}

for f in .env up.sh start.sh verify-frontend-dist.sh; do
  [[ -f "$f" ]] && sed -i 's/\r$//' "$f" 2>/dev/null || true
done

test -f .env || { echo "ERROR: missing .env in package/"; exit 1; }

chmod -R a+rX dist conf.d ssl 2>/dev/null || true
mkdir -p "$LOG_ROOT"/backend "$LOG_ROOT"/ai-server "$LOG_ROOT"/ffmpeg "$LOG_ROOT"/nginx
chmod -R u=rwX,go= "$LOG_ROOT"

if [[ -f ./verify-frontend-dist.sh ]]; then
  chmod +x ./verify-frontend-dist.sh
  ./verify-frontend-dist.sh .
fi

for tar in images/forum-backend.tar images/forum-ai-server.tar images/infra.tar; do
  if [[ ! -f "$tar" ]]; then
    echo "ERROR: missing $tar — re-upload full package/"
    exit 1
  fi
done

echo "==> docker load (offline images)"
docker load -i images/forum-backend.tar
docker load -i images/forum-ai-server.tar
docker load -i images/infra.tar

for img in forum-backend:latest forum-ai-server:latest forum-ffmpeg:latest nginx:1.30.1; do
  docker image inspect "$img" >/dev/null 2>&1 || {
    echo "ERROR: image missing after load: $img"
    exit 1
  }
done

docker run --rm --user 0 \
  -v "$(cd "$LOG_ROOT/backend" && pwd):/app/logs" \
  --entrypoint /bin/sh forum-backend:latest \
  -c 'chown -R 1000:1000 /app/logs'

echo "==> compose middleware"
$COMPOSE up -d mysql redis rabbitmq postgres ffmpeg
sync_rabbitmq_credentials

echo "==> compose application"
$COMPOSE up -d --force-recreate --no-deps ai-server backend-1 nginx

sleep 3
if curl -sf http://127.0.0.1/healthz >/dev/null; then
  echo "healthz OK"
else
  echo "WARN: healthz failed — check: docker logs forum-nginx --tail 30"
fi

IDX="dist/user/index.html"
if [[ -f "$IDX" ]]; then
  ASSET="$(grep -oE '/assets/[^"'\'' ]+\.js' "$IDX" | head -1 || true)"
  if [[ -n "$ASSET" ]]; then
    CODE="$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1${ASSET}" || echo 000)"
    echo "probe ${ASSET} -> HTTP ${CODE} (expect 200)"
  fi
fi

echo "Done. If browser still 403: docker logs forum-nginx --tail 50"
