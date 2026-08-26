#!/bin/bash
# 将发布包中的版本化配置同步到 Nacos 3.x；真实密钥仍由外部环境变量提供。
set -euo pipefail

ENV_FILE="${1:-${FORUM_ENV_FILE:-/opt/forum-config/prod.env}}"
CONFIG_ROOT="${2:-nacos-config}"
NACOS_CONTAINER="${3:-forum-nacos}"

read_env() {
  local key="$1" line value
  line="$(grep -E "^${key}=" "$ENV_FILE" | tail -1 || true)"
  value="${line#*=}"
  value="${value//$'\r'/}"
  value="${value%\"}"
  value="${value#\"}"
  printf '%s' "$value"
}

test -f "$ENV_FILE" || { echo "ERROR: missing external environment file: $ENV_FILE"; exit 1; }
test -d "$CONFIG_ROOT" || { echo "ERROR: missing Nacos config directory: $CONFIG_ROOT"; exit 1; }
docker inspect -f '{{.State.Running}}' "$NACOS_CONTAINER" | grep -Fxq true || {
  echo "ERROR: Nacos container is not running: $NACOS_CONTAINER"
  exit 1
}

namespace="$(read_env NACOS_NAMESPACE)"
group="$(read_env NACOS_GROUP)"
username="$(read_env NACOS_CONSOLE_USERNAME)"
password="$(read_env NACOS_CONSOLE_PASSWORD)"
namespace="${namespace:-forum-prod}"
group="${group:-FORUM}"
username="${username:-nacos}"
password="${password:-nacos}"

[[ "$namespace" =~ ^[A-Za-z0-9_-]+$ ]] || { echo "ERROR: invalid NACOS_NAMESPACE"; exit 1; }
[[ "$group" =~ ^[A-Za-z0-9_.-]+$ ]] || { echo "ERROR: invalid NACOS_GROUP"; exit 1; }

wait_for_console() {
  local state="" last_error="" i
  for i in $(seq 1 90); do
    if state="$(docker exec "$NACOS_CONTAINER" curl -fsS \
      http://127.0.0.1:8080/v3/console/server/state 2>&1)"; then
      if printf '%s' "$state" | grep -Eq '"auth_admin_request"[[:space:]]*:[[:space:]]*"?(true|false)"?'; then
        printf '%s' "$state"
        return 0
      fi
      last_error="Nacos console state endpoint returned an incomplete response"
    else
      last_error="$state"
    fi
    if [[ "$i" == "1" || $((i % 10)) == "0" ]]; then
      echo "Waiting for Nacos console API (${i}/90)..." >&2
    fi
    sleep 2
  done

  echo "ERROR: Nacos console API did not become ready within 180 seconds" >&2
  [[ -n "$last_error" ]] && printf 'Last probe error: %s\n' "$last_error" >&2
  docker inspect --format 'status={{.State.Status}} restarts={{.RestartCount}}' "$NACOS_CONTAINER" >&2
  docker logs --tail 100 "$NACOS_CONTAINER" >&2
  return 1
}

server_state="$(wait_for_console)"

# Nacos 3.1.1 的全新数据目录不会预置管理员，首次启动时必须先初始化。
if printf '%s' "$server_state" | grep -Eq '"auth_admin_request"[[:space:]]*:[[:space:]]*"?true"?'; then
  admin_json="$(docker exec "$NACOS_CONTAINER" curl -fsS -X POST \
    --data-urlencode "username=$username" --data-urlencode "password=$password" \
    http://127.0.0.1:8080/v3/auth/user/admin)"
  printf '%s' "$admin_json" | grep -Fq '"code":0' || {
    echo "ERROR: failed to initialize the Nacos administrator" >&2
    exit 1
  }
  echo "Nacos initial administrator created"
fi

expected=(
  forum-common.yml forum-gateway.yml forum-auth.yml forum-content.yml forum-im.yml
  forum-game.yml forum-economy.yml forum-ai.yml
  sentinel-forum-game-flow.json sentinel-forum-game-degrade.json
  sentinel-forum-content-flow.json sentinel-forum-content-degrade.json
  sentinel-forum-ai-flow.json sentinel-forum-ai-degrade.json
)
for data_id in "${expected[@]}"; do
  test -f "$CONFIG_ROOT/$data_id" || { echo "ERROR: missing Nacos config: $data_id"; exit 1; }
done

login_json="$(docker exec "$NACOS_CONTAINER" curl -fsS -X POST \
  --data-urlencode "username=$username" --data-urlencode "password=$password" \
  http://127.0.0.1:8080/v3/auth/user/login)"
token="$(printf '%s' "$login_json" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')"
[[ -n "$token" ]] || { echo "ERROR: Nacos login response has no accessToken"; exit 1; }

namespaces="$(docker exec "$NACOS_CONTAINER" curl -fsS \
  -H "accessToken: $token" \
  "http://127.0.0.1:8080/v3/console/core/namespace/list?username=$username")"
if ! printf '%s' "$namespaces" | grep -Fq "\"namespace\":\"$namespace\""; then
  created="$(docker exec "$NACOS_CONTAINER" curl -fsS -X POST \
    -H "accessToken: $token" \
    --data-urlencode "customNamespaceId=$namespace" \
    --data-urlencode "namespaceName=$namespace" \
    --data-urlencode "namespaceDesc=Forum deployment config" \
    "http://127.0.0.1:8080/v3/console/core/namespace?username=$username")"
  printf '%s' "$created" | grep -Eq '"code":0|"data":true|^true$' || {
    echo "ERROR: failed to create Nacos namespace: $namespace"
    exit 1
  }
fi

remote_tmp="/tmp/forum-nacos-sync"
docker exec "$NACOS_CONTAINER" sh -c "rm -rf '$remote_tmp' && mkdir -p '$remote_tmp'"
cleanup() {
  docker exec "$NACOS_CONTAINER" rm -rf "$remote_tmp" >/dev/null 2>&1 || true
}
trap cleanup EXIT

for data_id in "${expected[@]}"; do
  docker cp "$CONFIG_ROOT/$data_id" "$NACOS_CONTAINER:$remote_tmp/$data_id" >/dev/null
  type="yaml"
  [[ "$data_id" == *.json ]] && type="json"
  published="$(docker exec "$NACOS_CONTAINER" curl -fsS -X POST \
    -H "accessToken: $token" \
    --data-urlencode "dataId=$data_id" \
    --data-urlencode "groupName=$group" \
    --data-urlencode "namespaceId=$namespace" \
    --data-urlencode "type=$type" \
    --data-urlencode "content@$remote_tmp/$data_id" \
    http://127.0.0.1:8848/nacos/v3/admin/cs/config)"
  printf '%s' "$published" | grep -Eq '"code":0.*"data":true' || {
    echo "ERROR: failed to publish Nacos config: $data_id"
    exit 1
  }
  checked="$(docker exec "$NACOS_CONTAINER" curl -fsS \
    -H "accessToken: $token" \
    "http://127.0.0.1:8848/nacos/v3/admin/cs/config?dataId=$data_id&groupName=$group&namespaceId=$namespace")"
  printf '%s' "$checked" | grep -Eq '"code":0' || {
    echo "ERROR: failed to read back Nacos config: $data_id"
    exit 1
  }
  echo "OK $data_id"
done

echo "Nacos config synchronized: namespace=$namespace group=$group count=${#expected[@]}"
