#!/bin/bash
# 首次建库：用各域 create.sql 初始化空库，并校正六域账号。
# 用法（发布包内）:
#   FORUM_ENV_FILE=/opt/forum-config/prod.env bash init-db.sh
# 已有业务数据时不要跑本脚本；需要清空重建请用 reset-db.sh。
set -euo pipefail
cd "$(dirname "$0")"

ENV_FILE="${FORUM_ENV_FILE:-/opt/forum-config/prod.env}"
SQL_DIR="${FORUM_SQL_DIR:-./sql}"
MYSQL_CONTAINER="${FORUM_MYSQL_CONTAINER:-forum-mysql}"
POSTGRES_CONTAINER="${FORUM_POSTGRES_CONTAINER:-forum-postgres}"

declare -A EXPECTED_TABLES=([auth]=11 [content]=30 [im]=14 [game]=12 [economy]=38 [ai]=17)

test -f "$ENV_FILE" || { echo "ERROR: missing env file: $ENV_FILE" >&2; exit 1; }
test -d "$SQL_DIR" || { echo "ERROR: missing sql dir: $SQL_DIR" >&2; exit 1; }

read_env() {
  local key="$1" line value
  line="$(grep -E "^${key}=" "$ENV_FILE" | tail -1 || true)"
  value="${line#*=}"
  value="${value//$'\r'/}"
  value="${value%\"}"; value="${value#\"}"
  printf '%s' "$value"
}

sql_escape() {
  printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e "s/'/''/g"
}

wait_mysql() {
  local root_pw="$1" i
  for i in $(seq 1 90); do
    if docker exec -e MYSQL_PWD="${root_pw}" "$MYSQL_CONTAINER" mysql -h 127.0.0.1 -uroot -Nse "SELECT 1" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  echo "ERROR: MySQL did not pass authenticated SELECT 1" >&2
  docker exec -e MYSQL_PWD="${root_pw}" "$MYSQL_CONTAINER" mysql -h 127.0.0.1 -uroot -Nse "SELECT 1"
  return 1
}

mysql_scalar() {
  local root_pw="$1" sql="$2" out
  if ! out="$(docker exec -e MYSQL_PWD="${root_pw}" -i "$MYSQL_CONTAINER" mysql -uroot -N -B -e "$sql" </dev/null 2>&1)"; then
    echo "ERROR: MySQL query failed" >&2
    printf '%s\n' "$out" >&2
    return 1
  fi
  printf '%s' "$out" | tr -d '\r'
}

ensure_domain_users() {
  local root_pw="$1" domain upper user password escaped_user escaped_password
  for domain in auth content im game economy ai; do
    upper="$(printf '%s' "$domain" | tr '[:lower:]' '[:upper:]')"
    user="$(read_env "FORUM_${upper}_DB_USERNAME")"
    password="$(read_env "FORUM_${upper}_DB_PASSWORD")"
    [[ -n "$user" && -n "$password" ]] || {
      echo "ERROR: missing FORUM_${upper}_DB_USERNAME or FORUM_${upper}_DB_PASSWORD" >&2
      return 1
    }
    escaped_user="$(sql_escape "$user")"
    escaped_password="$(sql_escape "$password")"
    docker exec -e MYSQL_PWD="${root_pw}" -i "$MYSQL_CONTAINER" mysql -uroot --default-character-set=utf8mb4 <<SQL
CREATE USER IF NOT EXISTS '${escaped_user}'@'%' IDENTIFIED BY '${escaped_password}';
ALTER USER '${escaped_user}'@'%' IDENTIFIED BY '${escaped_password}';
GRANT ALL PRIVILEGES ON \`forum_${domain}_db\`.* TO '${escaped_user}'@'%';
SQL
  done
  docker exec -e MYSQL_PWD="${root_pw}" "$MYSQL_CONTAINER" mysql -uroot -e "FLUSH PRIVILEGES;" >/dev/null
}

validate_domain_tables() {
  local root_pw="$1" domain actual expected invalid=0
  for domain in auth content im game economy ai; do
    expected="${EXPECTED_TABLES[$domain]}"
    actual="$(mysql_scalar "$root_pw" "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='forum_${domain}_db' AND TABLE_TYPE='BASE TABLE';")" || return 1
    echo "forum_${domain}_db tables=${actual} expected=${expected}"
    [[ "$actual" == "$expected" ]] || invalid=1
  done
  [[ "$invalid" == "0" ]]
}

root_pw="$(read_env MYSQL_ROOT_PASSWORD)"
[[ -n "$root_pw" ]] || { echo "ERROR: MYSQL_ROOT_PASSWORD empty" >&2; exit 1; }
wait_mysql "$root_pw"

empty_domains=0
valid_domains=0
for domain in auth content im game economy ai; do
  actual="$(mysql_scalar "$root_pw" "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='forum_${domain}_db' AND TABLE_TYPE='BASE TABLE';")" || exit 1
  if [[ "$actual" == "0" ]]; then
    empty_domains=$((empty_domains + 1))
  elif [[ "$actual" == "${EXPECTED_TABLES[$domain]}" ]]; then
    valid_domains=$((valid_domains + 1))
  else
    echo "ERROR: forum_${domain}_db has ${actual} tables; expected ${EXPECTED_TABLES[$domain]}" >&2
    echo "Do not force baseline SQL on a non-empty mismatched schema." >&2
    exit 1
  fi
done

if [[ "$empty_domains" == "6" ]]; then
  for domain in auth content im game economy ai; do
    schema="${SQL_DIR}/${domain}-create.sql"
    test -f "$schema" || { echo "ERROR: missing $schema" >&2; exit 1; }
    echo "==> initialize ${schema}"
    docker exec -e MYSQL_PWD="${root_pw}" -i "$MYSQL_CONTAINER" mysql -uroot --default-character-set=utf8mb4 < "$schema"
  done
  validate_domain_tables "$root_pw" || { echo "ERROR: table-count validation failed" >&2; exit 1; }
elif [[ "$valid_domains" == "6" ]]; then
  echo "Six domain schemas already match baseline; MySQL init skipped."
else
  echo "ERROR: mixed empty/initialized domain databases; refusing init" >&2
  exit 1
fi

ensure_domain_users "$root_pw"

pu="$(read_env POSTGRES_USER)"; pd="$(read_env POSTGRES_DB)"
pu="${pu:-langgraph}"; pd="${pd:-langgraph_db}"
pg_sql="${SQL_DIR}/postgres_ai_session.sql"
if [[ -f "$pg_sql" ]]; then
  for i in $(seq 1 90); do
    if docker exec "$POSTGRES_CONTAINER" pg_isready -U "$pu" -d "$pd" >/dev/null 2>&1; then
      break
    fi
    sleep 2
  done
  echo "==> apply PostgreSQL schema"
  docker exec -i "$POSTGRES_CONTAINER" psql -v ON_ERROR_STOP=1 -U "$pu" -d "$pd" < "$pg_sql"
fi

echo "init-db done"
