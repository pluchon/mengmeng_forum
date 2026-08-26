#!/bin/bash
# 上传 package 后在服务器执行：bash up.sh
# 作用：修权限、校验 dist、加载离线镜像、重建容器（比裸 compose up --build 完整）
set -euo pipefail
cd "$(dirname "$0")"
test -f manifest.sha256 || { echo "ERROR: missing manifest.sha256; upload the complete package"; exit 1; }
sha256sum -c manifest.sha256
ENV_FILE="${FORUM_ENV_FILE:-/opt/forum-config/prod.env}"
COMPOSE="docker compose --env-file ${ENV_FILE} -f docker-compose.yaml -f docker-compose.prod.yml"
LOG_ROOT="${FORUM_LOG_DIR:-../logs}"
echo "Forum package release: $(cat RELEASE.txt 2>/dev/null || echo unknown)"

read_env() {
  local key="$1" line value
  line="$(grep -E "^${key}=" "$ENV_FILE" | tail -1 || true)"
  value="${line#*=}"
  value="${value//$'\r'/}"
  value="${value%\"}"; value="${value#\"}"
  printf '%s' "$value"
}

ensure_nacos_bootstrap_values() {
  local key value
  for key in NACOS_AUTH_TOKEN NACOS_AUTH_IDENTITY_KEY NACOS_AUTH_IDENTITY_VALUE; do
    value="$(read_env "$key")"
    [[ -n "$value" ]] && continue
    command -v openssl >/dev/null 2>&1 || {
      echo "ERROR: openssl is required to initialize the local Nacos runtime values"
      return 1
    }
    if [[ "$key" == "NACOS_AUTH_TOKEN" ]]; then
      value="$(openssl rand -base64 48 | tr -d '\r\n')"
    else
      value="$(openssl rand -hex 24)"
    fi
    printf '\n%s=%s\n' "$key" "$value" >> "$ENV_FILE"
  done
  chmod 600 "$ENV_FILE"
}

wait_mysql() {
  local root_pw="$1" i
  for i in $(seq 1 90); do
    if docker exec -e MYSQL_PWD="${root_pw}" forum-mysql mysql -h 127.0.0.1 -uroot -Nse "SELECT 1" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  echo "ERROR: MySQL did not pass authenticated SELECT 1" >&2
  docker exec -e MYSQL_PWD="${root_pw}" forum-mysql mysql -h 127.0.0.1 -uroot -Nse "SELECT 1"
}

sql_escape() {
  printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e "s/'/''/g"
}

ensure_domain_users() {
  local root_pw="$1" domain upper user password escaped_user escaped_password
  for domain in auth content im game economy ai; do
    upper="$(printf '%s' "$domain" | tr '[:lower:]' '[:upper:]')"
    user="$(read_env "FORUM_${upper}_DB_USERNAME")"
    password="$(read_env "FORUM_${upper}_DB_PASSWORD")"
    [[ -n "$user" && -n "$password" ]] || {
      echo "ERROR: missing FORUM_${upper}_DB_USERNAME or FORUM_${upper}_DB_PASSWORD in $ENV_FILE"
      return 1
    }
    escaped_user="$(sql_escape "$user")"
    escaped_password="$(sql_escape "$password")"
    docker exec -e MYSQL_PWD="${root_pw}" -i forum-mysql mysql -uroot --default-character-set=utf8mb4 <<SQL
CREATE USER IF NOT EXISTS '${escaped_user}'@'%' IDENTIFIED BY '${escaped_password}';
ALTER USER '${escaped_user}'@'%' IDENTIFIED BY '${escaped_password}';
GRANT ALL PRIVILEGES ON \`forum_${domain}_db\`.* TO '${escaped_user}'@'%';
SQL
  done
  docker exec -e MYSQL_PWD="${root_pw}" forum-mysql mysql -uroot -e "FLUSH PRIVILEGES;" >/dev/null
}

# 取单值查询结果。查询本身失败时返回非零，避免把连接错误当成 0 处理。
mysql_scalar() {
  local root_pw="$1" sql="$2" out
  if ! out="$(docker exec -e MYSQL_PWD="${root_pw}" -i forum-mysql mysql -uroot -N -B -e "$sql" </dev/null 2>&1)"; then
    echo "ERROR: MySQL 查询失败，无法确认数据库状态" >&2
    printf '%s\n' "$out" >&2
    return 1
  fi
  printf '%s' "$out" | tr -d '\r'
}

# 更新包只接受与当前最终基线表数量一致的六域库；空库、部分库或版本不匹配均停止。
require_domain_schemas() {
  local root_pw="$1" domain count expected invalid=0
  declare -A expected_tables=([auth]=11 [content]=30 [im]=14 [game]=12 [economy]=38 [ai]=17)
  for domain in auth content im game economy ai; do
    count="$(mysql_scalar "$root_pw" "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='forum_${domain}_db' AND TABLE_TYPE='BASE TABLE';")" || return 1
    expected="${expected_tables[$domain]}"
    echo "forum_${domain}_db tables=${count} expected=${expected}"
    [[ "$count" == "$expected" ]] || invalid=1
  done
  if [[ "$invalid" != "0" ]]; then
    echo "ERROR: 六域库表清单与当前发布包不一致，已停止启动应用服务。"
    echo "       已有数据的库只能跑审核后的前向迁移，或换匹配版本的发布包。"
    echo "       确认可销毁全部业务数据时，才可运行带三次确认的 reset-db.sh。"
    return 1
  fi
  echo "domain schemas OK"
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

for f in up.sh start.sh init-db.sh verify-frontend-dist.sh sync-nacos.sh reset-db.sh; do
  [[ -f "$f" ]] && sed -i 's/\r$//' "$f" 2>/dev/null || true
done

test -f "$ENV_FILE" || { echo "ERROR: missing external environment file: $ENV_FILE"; exit 1; }
ensure_nacos_bootstrap_values

chmod -R a+rX dist conf.d ssl 2>/dev/null || true
mkdir -p "$LOG_ROOT"/java-backend "$LOG_ROOT"/ai-server "$LOG_ROOT"/ffmpeg "$LOG_ROOT"/nginx
if ! chmod -R u=rwX,go= "$LOG_ROOT" 2>/dev/null; then
  # 历史日志可能由 root 或容器用户创建；权限收紧失败不应阻断服务恢复。
  echo "WARN: unable to change existing log file permissions under $LOG_ROOT; continuing with current ownership"
fi

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

for img in forum-backend:latest forum-ai-server:latest forum-ffmpeg:latest nginx:1.30.1 nacos/nacos-server:v3.1.1; do
  docker image inspect "$img" >/dev/null 2>&1 || {
    echo "ERROR: image missing after load: $img"
    exit 1
  }
done

docker run --rm --user 0 \
  -v "$(cd "$LOG_ROOT/java-backend" && pwd):/app/logs" \
  --entrypoint /bin/sh forum-backend:latest \
  -c 'chown -R 1000:1000 /app/logs'

echo "==> compose middleware"
$COMPOSE up -d mysql redis rabbitmq postgres ffmpeg nacos
sync_rabbitmq_credentials
bash sync-nacos.sh "$ENV_FILE" nacos-config forum-nacos
root_pw="$(read_env MYSQL_ROOT_PASSWORD)"
wait_mysql "$root_pw"
ensure_domain_users "$root_pw"
require_domain_schemas "$root_pw"

echo "==> compose application"
$COMPOSE up -d --force-recreate --no-deps ai-server auth content im game economy ai gateway nginx

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
