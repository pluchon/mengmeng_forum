#!/usr/bin/env bash
# =============================================================================
# 线上 / 本地：MySQL 拆库迁移脚本（六域独立库）
# 放在仓库根目录，供服务器与本地统一使用。
#
# 用法（仓库根目录）：
#   # 1) 仅创建账号并授权（安全，不删库）
#   bash migrate-online-db.sh users
#
#   # 2) 用各域 create.sql 重建空库（危险：会 DROP DATABASE）
#   CONFIRM_DROP=YES bash migrate-online-db.sh init
#
#   # 3) 已有旧 forum_db 数据：建立六域库基线结构并把数据搬过去（保留 forum_db）
#   #    找不到 forum_db 或它没有表时会直接报错中止，不会留下空库
#   bash migrate-online-db.sh migrate-data
#   CONFIRM_DROP_FORUM_DB=YES bash migrate-online-db.sh migrate-data
#
# 连接方式（二选一）：
#   A) Docker：MYSQL_CONTAINER=forum-mysql  （package 生产默认）
#               MYSQL_CONTAINER=forum-mysql-dev （本地 docker-compose.dev）
#   B) 直连：  MYSQL_HOST=127.0.0.1 MYSQL_PORT=33306
#
# 常用环境变量：
#   MYSQL_ROOT_PASSWORD   默认 123456789
#   DB_AUTH_USER/PASS ... 各域账号，默认与 application.yml 一致
# =============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
ACTION="${1:-users}"

MYSQL_CONTAINER="${MYSQL_CONTAINER:-}"
MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-33306}"
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-}"
CONFIRM_DROP="${CONFIRM_DROP:-NO}"
CONFIRM_DROP_FORUM_DB="${CONFIRM_DROP_FORUM_DB:-NO}"
USE_DIRECT_MYSQL="${USE_DIRECT_MYSQL:-0}"

read_deployment_setting() {
  local key="$1" env_file line
  # 离线包脚本位于 package/，源码脚本位于仓库根目录；依次兼容两种 .env 位置。
  for env_file in "$ROOT/.env" "$ROOT/nginx/.env"; do
    if [[ -f "$env_file" ]]; then
      line="$(grep -E "^${key}=" "$env_file" | tail -1 || true)"
      if [[ -n "$line" ]]; then
        line="${line#*=}"
        line="${line%$'\r'}"
        line="${line%\"}"; line="${line#\"}"
        printf '%s' "$line"
        return
      fi
    fi
  done
}

MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-$(read_deployment_setting MYSQL_ROOT_PASSWORD)}"
[[ -n "$MYSQL_ROOT_PASSWORD" ]] || {
  echo "ERROR: MYSQL_ROOT_PASSWORD must be supplied through the environment, .env, or nginx/.env"
  exit 1
}

# 未指定容器且未强制直连时，自动探测常见 MySQL 容器名；优先选择正在运行的容器
if [[ -z "$MYSQL_CONTAINER" && "$USE_DIRECT_MYSQL" != "1" ]]; then
  if command -v docker >/dev/null 2>&1; then
    for c in forum-mysql forum-mysql-dev; do
      if [[ "$(docker inspect -f '{{.State.Running}}' "$c" 2>/dev/null)" == "true" ]]; then
        MYSQL_CONTAINER="$c"
        break
      fi
    done
    if [[ -z "$MYSQL_CONTAINER" ]]; then
      for c in forum-mysql forum-mysql-dev; do
        if docker inspect "$c" >/dev/null 2>&1; then
          MYSQL_CONTAINER="$c"
          break
        fi
      done
    fi
  fi
fi

DB_AUTH_USER="${DB_AUTH_USER:-$(read_deployment_setting FORUM_AUTH_DB_USERNAME)}"
DB_AUTH_PASS="${DB_AUTH_PASS:-$(read_deployment_setting FORUM_AUTH_DB_PASSWORD)}"
DB_CONTENT_USER="${DB_CONTENT_USER:-$(read_deployment_setting FORUM_CONTENT_DB_USERNAME)}"
DB_CONTENT_PASS="${DB_CONTENT_PASS:-$(read_deployment_setting FORUM_CONTENT_DB_PASSWORD)}"
DB_IM_USER="${DB_IM_USER:-$(read_deployment_setting FORUM_IM_DB_USERNAME)}"
DB_IM_PASS="${DB_IM_PASS:-$(read_deployment_setting FORUM_IM_DB_PASSWORD)}"
DB_GAME_USER="${DB_GAME_USER:-$(read_deployment_setting FORUM_GAME_DB_USERNAME)}"
DB_GAME_PASS="${DB_GAME_PASS:-$(read_deployment_setting FORUM_GAME_DB_PASSWORD)}"
DB_ECONOMY_USER="${DB_ECONOMY_USER:-$(read_deployment_setting FORUM_ECONOMY_DB_USERNAME)}"
DB_ECONOMY_PASS="${DB_ECONOMY_PASS:-$(read_deployment_setting FORUM_ECONOMY_DB_PASSWORD)}"
DB_AI_USER="${DB_AI_USER:-$(read_deployment_setting FORUM_AI_DB_USERNAME)}"
DB_AI_PASS="${DB_AI_PASS:-$(read_deployment_setting FORUM_AI_DB_PASSWORD)}"

DOMAINS=(auth content im game economy ai)

sql_file_for() {
  local domain="$1"
  if [[ -f "$ROOT/sql/${domain}-create.sql" ]]; then
    echo "$ROOT/sql/${domain}-create.sql"
    return
  fi
  echo "$ROOT/java-cloud-standalone/${domain}/server/src/main/resources/db/create.sql"
}

mysql_exec() {
  if [[ -n "$MYSQL_CONTAINER" ]]; then
    docker exec -i "$MYSQL_CONTAINER" mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" --default-character-set=utf8mb4 "$@"
  else
    mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -uroot -p"${MYSQL_ROOT_PASSWORD}" --default-character-set=utf8mb4 "$@"
  fi
}

mysql_exec_file() {
  local file="$1"
  if [[ -n "$MYSQL_CONTAINER" ]]; then
    docker exec -i "$MYSQL_CONTAINER" mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" --default-character-set=utf8mb4 < "$file"
  else
    mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -uroot -p"${MYSQL_ROOT_PASSWORD}" --default-character-set=utf8mb4 < "$file"
  fi
}

# 只读查询统一入口。
# 迁移脚本一旦把“连接失败/权限不足”静默当成“对象不存在”，就会误判空库并搬空数据，
# 因此这里必须把任何非零退出码直接暴露成致命错误。
mysql_query() {
  local sql="$1" out rc err_file
  err_file="$(mktemp)"
  set +e
  out="$(mysql_exec -N -B -e "$sql" 2>"$err_file" </dev/null)"
  rc=$?
  set -e
  if (( rc != 0 )); then
    echo "ERROR: MySQL 查询失败（退出码 ${rc}），已中止，未做任何写入。" >&2
    grep -v 'Using a password on the command line interface' "$err_file" >&2 || true
    rm -f "$err_file"
    exit 1
  fi
  rm -f "$err_file"
  printf '%s' "$out" | tr -d '\r'
}

ensure_tools() {
  if [[ -n "$MYSQL_CONTAINER" ]]; then
    docker inspect "$MYSQL_CONTAINER" >/dev/null 2>&1 || {
      echo "ERROR: container not found: $MYSQL_CONTAINER"
      exit 1
    }
    # 容器未运行时所有查询都会失败；必须直接报错，否则会被误判成“数据库不存在”。
    if [[ "$(docker inspect -f '{{.State.Running}}' "$MYSQL_CONTAINER" 2>/dev/null)" != "true" ]]; then
      echo "ERROR: 容器 $MYSQL_CONTAINER 未运行，无法判断数据库状态，已中止。"
      echo "       请先启动 MySQL 再重试："
      echo "       docker compose -f docker-compose.yaml -f docker-compose.prod.yml up -d mysql"
      exit 1
    fi
  else
    command -v mysql >/dev/null 2>&1 || {
      echo "ERROR: mysql client missing. Install client or set MYSQL_CONTAINER=forum-mysql"
      exit 1
    }
  fi
}

ensure_schema_files() {
  local d f
  for d in "${DOMAINS[@]}"; do
    f="$(sql_file_for "$d")"
    [[ -f "$f" ]] || { echo "ERROR: missing schema $f"; exit 1; }
  done
}

create_users_and_grants() {
  echo "==> create domain users + grants"
  local value
  for value in "$DB_AUTH_USER" "$DB_AUTH_PASS" "$DB_CONTENT_USER" "$DB_CONTENT_PASS" "$DB_IM_USER" "$DB_IM_PASS" "$DB_GAME_USER" "$DB_GAME_PASS" "$DB_ECONOMY_USER" "$DB_ECONOMY_PASS" "$DB_AI_USER" "$DB_AI_PASS"; do
    [[ -n "$value" ]] || { echo "ERROR: missing one or more domain database credentials"; exit 1; }
  done
  mysql_exec <<SQL
CREATE DATABASE IF NOT EXISTS \`forum_auth_db\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS \`forum_content_db\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS \`forum_im_db\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS \`forum_game_db\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS \`forum_economy_db\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS \`forum_ai_db\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS '${DB_AUTH_USER}'@'%' IDENTIFIED BY '${DB_AUTH_PASS}';
CREATE USER IF NOT EXISTS '${DB_CONTENT_USER}'@'%' IDENTIFIED BY '${DB_CONTENT_PASS}';
CREATE USER IF NOT EXISTS '${DB_IM_USER}'@'%' IDENTIFIED BY '${DB_IM_PASS}';
CREATE USER IF NOT EXISTS '${DB_GAME_USER}'@'%' IDENTIFIED BY '${DB_GAME_PASS}';
CREATE USER IF NOT EXISTS '${DB_ECONOMY_USER}'@'%' IDENTIFIED BY '${DB_ECONOMY_PASS}';
CREATE USER IF NOT EXISTS '${DB_AI_USER}'@'%' IDENTIFIED BY '${DB_AI_PASS}';

ALTER USER '${DB_AUTH_USER}'@'%' IDENTIFIED BY '${DB_AUTH_PASS}';
ALTER USER '${DB_CONTENT_USER}'@'%' IDENTIFIED BY '${DB_CONTENT_PASS}';
ALTER USER '${DB_IM_USER}'@'%' IDENTIFIED BY '${DB_IM_PASS}';
ALTER USER '${DB_GAME_USER}'@'%' IDENTIFIED BY '${DB_GAME_PASS}';
ALTER USER '${DB_ECONOMY_USER}'@'%' IDENTIFIED BY '${DB_ECONOMY_PASS}';
ALTER USER '${DB_AI_USER}'@'%' IDENTIFIED BY '${DB_AI_PASS}';

GRANT ALL PRIVILEGES ON \`forum_auth_db\`.* TO '${DB_AUTH_USER}'@'%';
GRANT ALL PRIVILEGES ON \`forum_content_db\`.* TO '${DB_CONTENT_USER}'@'%';
GRANT ALL PRIVILEGES ON \`forum_im_db\`.* TO '${DB_IM_USER}'@'%';
GRANT ALL PRIVILEGES ON \`forum_game_db\`.* TO '${DB_GAME_USER}'@'%';
GRANT ALL PRIVILEGES ON \`forum_economy_db\`.* TO '${DB_ECONOMY_USER}'@'%';
GRANT ALL PRIVILEGES ON \`forum_ai_db\`.* TO '${DB_AI_USER}'@'%';
FLUSH PRIVILEGES;
SQL
  echo "users/grants OK"
}

init_schemas() {
  if [[ "$CONFIRM_DROP" != "YES" ]]; then
    echo "ERROR: init 会执行各域 create.sql 中的 DROP DATABASE。"
    echo "若确认可清空并重建六域库，请设置： CONFIRM_DROP=YES bash migrate-online-db.sh init"
    exit 1
  fi
  local d f
  for d in "${DOMAINS[@]}"; do
    f="$(sql_file_for "$d")"
    echo "==> apply $f"
    mysql_exec_file "$f"
  done
  create_users_and_grants
  echo "init schemas OK"
}

db_exists() {
  local name="$1"
  [[ "$(mysql_query "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='${name}';")" != "0" ]]
}

table_count() {
  local schema="$1"
  mysql_query "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='${schema}' AND TABLE_TYPE='BASE TABLE';"
}

table_exists() {
  local schema="$1" table="$2"
  [[ "$(mysql_query "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='${schema}' AND TABLE_NAME='${table}' AND TABLE_TYPE='BASE TABLE';")" != "0" ]]
}

list_tables() {
  local schema="$1"
  mysql_query "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA='${schema}' AND TABLE_TYPE='BASE TABLE' ORDER BY TABLE_NAME;"
}

# 取源表与目标表的同名列交集，按目标表列序输出反引号列名。
# 目标表来自新版基线结构，源表来自旧单库，两者可能存在列差异；
# 用列交集而不是 SELECT * 才能在结构演进后仍然搬得动数据。
shared_columns() {
  local target="$1" table="$2" out
  out="$(mysql_query "SET SESSION group_concat_max_len = 1048576;
SELECT GROUP_CONCAT(CONCAT('\`', t.COLUMN_NAME, '\`') ORDER BY t.ORDINAL_POSITION SEPARATOR ',')
  FROM information_schema.COLUMNS t
  JOIN information_schema.COLUMNS s
    ON s.TABLE_SCHEMA = 'forum_db'
   AND s.TABLE_NAME = '${table}'
   AND s.COLUMN_NAME = t.COLUMN_NAME
 WHERE t.TABLE_SCHEMA = '${target}'
   AND t.TABLE_NAME = '${table}'
   AND t.EXTRA NOT LIKE '%GENERATED%';")"
  [[ "$out" == "NULL" ]] && out=""
  printf '%s' "$out"
}

# 只对“完全没有表”的域库应用基线结构。
# create.sql 带 DROP DATABASE，因此绝不能覆盖已有数据的域库；
# 同时必须在建库之后再授权，否则 DROP DATABASE 会连带清除该库上的权限。
apply_domain_baselines() {
  local domain schema_file existing
  for domain in "${DOMAINS[@]}"; do
    existing="$(table_count "forum_${domain}_db")"
    if [[ "$existing" != "0" ]]; then
      echo "==> forum_${domain}_db 已有 ${existing} 张表，跳过基线结构"
      continue
    fi
    schema_file="$(sql_file_for "$domain")"
    echo "==> 建立 forum_${domain}_db 基线结构（$schema_file）"
    mysql_exec_file "$schema_file"
  done
}

migrate_data_from_forum_db() {
  # 找不到源库时必须中止。旧版本在这里继续创建六个空库，
  # 结果是“库有了、表没有”，服务启动后报表不存在，属于必须避免的静默错误。
  if ! db_exists "forum_db"; then
    echo "ERROR: 当前 MySQL 实例中不存在 forum_db，已中止，未创建任何库。"
    echo "       当前实例中的数据库："
    mysql_exec -e "SHOW DATABASES;" </dev/null || true
    echo "       若确认要在全新空实例上初始化，请改用： CONFIRM_DROP=YES bash migrate-online-db.sh init"
    exit 1
  fi

  local source_tables
  source_tables="$(table_count forum_db)"
  if [[ "$source_tables" == "0" ]]; then
    echo "ERROR: forum_db 存在但没有任何表，无法作为迁移源，已中止。"
    exit 1
  fi
  echo "==> 迁移源 forum_db 共 ${source_tables} 张表"

  apply_domain_baselines
  create_users_and_grants

  declare -A TABLE_OWNER=(
    [sys_dept]=forum_auth_db [sys_dict_data]=forum_auth_db [sys_dict_type]=forum_auth_db
    [sys_menu]=forum_auth_db [sys_role]=forum_auth_db [sys_role_menu]=forum_auth_db
    [sys_user_role]=forum_auth_db [user]=forum_auth_db [user_follow]=forum_auth_db
    [user_login_log]=forum_auth_db

    [article]=forum_content_db [article_favorite]=forum_content_db [article_image]=forum_content_db
    [article_like]=forum_content_db [article_reply]=forum_content_db [article_reply_like]=forum_content_db
    [article_reply_media]=forum_content_db [article_sub_reply]=forum_content_db
    [article_sub_reply_like]=forum_content_db [article_video_danmaku]=forum_content_db
    [board]=forum_content_db [category]=forum_content_db
    [forum_article_ai_feature]=forum_content_db [forum_article_tag]=forum_content_db
    [forum_article_tag_link]=forum_content_db [forum_article_tag_request]=forum_content_db
    [forum_user_ai_profile_snapshot]=forum_content_db [user_favorite_folder]=forum_content_db
    [user_interest_preference]=forum_content_db [user_recommend_feedback]=forum_content_db

    [forum_notice]=forum_im_db [forum_outbox_message]=forum_im_db [group_chat]=forum_im_db
    [group_chat_join_request]=forum_im_db [group_chat_member]=forum_im_db
    [group_chat_message]=forum_im_db [group_chat_report]=forum_im_db [message]=forum_im_db
    [system_message]=forum_im_db [user_chat_emoji]=forum_im_db

    [game_definition]=forum_game_db [game_gobang_match_record]=forum_game_db
    [game_gobang_room_move]=forum_game_db [game_jinzi_match_record]=forum_game_db
    [game_jinzi_room_move]=forum_game_db [game_match_record]=forum_game_db
    [game_room_move]=forum_game_db [game_room_player]=forum_game_db
    [game_settlement_event]=forum_game_db [game_tetris_pk_match_record]=forum_game_db
    [game_tetris_record]=forum_game_db [game_user_profile]=forum_game_db

    [checkin_log]=forum_economy_db [checkin_rule]=forum_economy_db
    [checkin_streak_reward]=forum_economy_db [emoji_item]=forum_economy_db
    [emoji_shop]=forum_economy_db [exam_question]=forum_economy_db
    [exam_question_bank]=forum_economy_db [exam_question_user_progress]=forum_economy_db
    [forum_vip_quota_config]=forum_economy_db [growth_challenge]=forum_economy_db
    [growth_challenge_attempt]=forum_economy_db [growth_experience_log]=forum_economy_db
    [growth_reward_record]=forum_economy_db [lottery_activity]=forum_economy_db
    [lottery_activity_prize]=forum_economy_db [lottery_draw_hourly_stat]=forum_economy_db
    [lottery_draw_record]=forum_economy_db [lottery_draw_request]=forum_economy_db
    [lottery_prize]=forum_economy_db [lottery_prize_mystery_item]=forum_economy_db
    [points_log]=forum_economy_db [points_wallet]=forum_economy_db
    [user_checkin_info]=forum_economy_db [user_emoji]=forum_economy_db
    [user_growth_profile]=forum_economy_db [user_lottery_pity]=forum_economy_db
    [user_vip_subscription]=forum_economy_db [vip_trial_entitlement]=forum_economy_db

    [ai_usage_daily]=forum_ai_db [drift_bottle]=forum_ai_db
    [drift_bottle_comment]=forum_ai_db [drift_bottle_pick_log]=forum_ai_db
    [drift_bottle_report]=forum_ai_db [forum_ai_call_record]=forum_ai_db
    [forum_ai_creation_version]=forum_ai_db [forum_ai_creation_workspace]=forum_ai_db
    [forum_ai_long_term_memory]=forum_ai_db [forum_ai_model_price]=forum_ai_db
    [forum_ai_model_usage_daily]=forum_ai_db [forum_ai_task_session]=forum_ai_db
    [forum_ai_usage_log]=forum_ai_db [forum_companion_message]=forum_ai_db
    [forum_companion_session]=forum_ai_db [forum_mascot_model]=forum_ai_db
    [forum_mascot_related_recommendation]=forum_ai_db
    [forum_mascot_related_recommendation_item]=forum_ai_db
    [user_mascot_preference]=forum_ai_db
  )

  echo "==> 搬迁数据 forum_db -> 六域库"

  # 先把表名读进数组再循环。
  # 循环体内的 docker exec 会争抢循环的标准输入，边读边执行会导致只处理第一张表。
  local tables=()
  mapfile -t tables < <(list_tables "forum_db")

  local table target columns existing_rows copied_rows
  local moved=0 skipped=0 unmapped=0
  for table in "${tables[@]}"; do
    [[ -n "$table" ]] || continue
    target="${TABLE_OWNER[$table]:-}"
    if [[ -z "$target" ]]; then
      echo "WARN: 未映射表 forum_db.${table}，跳过"
      unmapped=$((unmapped + 1))
      continue
    fi
    # 基线结构里没有的历史遗留表，按原结构复制一份，避免旧数据丢失
    if ! table_exists "$target" "$table"; then
      echo "  -- ${table}: 基线结构中不存在，按旧结构创建"
      mysql_exec </dev/null <<SQL
CREATE TABLE \`${target}\`.\`${table}\` LIKE \`forum_db\`.\`${table}\`;
SQL
    fi
    existing_rows="$(mysql_query "SELECT COUNT(*) FROM \`${target}\`.\`${table}\`;")"
    if [[ "$existing_rows" != "0" ]]; then
      echo "  -- ${table}: 目标已有 ${existing_rows} 行，跳过"
      skipped=$((skipped + 1))
      continue
    fi
    columns="$(shared_columns "$target" "$table")"
    if [[ -z "$columns" ]]; then
      echo "WARN: ${table} 与目标表没有可搬迁的同名列，跳过"
      skipped=$((skipped + 1))
      continue
    fi
    # 按表逐张搬迁，外键顺序无法保证，故在本次连接内关闭外键校验
    mysql_exec </dev/null <<SQL
SET FOREIGN_KEY_CHECKS = 0;
INSERT INTO \`${target}\`.\`${table}\` (${columns}) SELECT ${columns} FROM \`forum_db\`.\`${table}\`;
SET FOREIGN_KEY_CHECKS = 1;
SQL
    copied_rows="$(mysql_query "SELECT COUNT(*) FROM \`${target}\`.\`${table}\`;")"
    echo "  -> ${target}.${table}: ${copied_rows} 行"
    moved=$((moved + 1))
  done

  echo "迁移完成：搬迁 ${moved} 张表，跳过 ${skipped} 张，未映射 ${unmapped} 张"

  if [[ "$CONFIRM_DROP_FORUM_DB" == "YES" ]]; then
    echo "==> DROP DATABASE forum_db"
    mysql_exec -e "DROP DATABASE IF EXISTS \`forum_db\`;"
  else
    echo "INFO: 保留 forum_db。确认业务正常后可执行："
    echo "      CONFIRM_DROP_FORUM_DB=YES bash migrate-online-db.sh drop-forum-db"
  fi
}

drop_forum_db() {
  if [[ "$CONFIRM_DROP_FORUM_DB" != "YES" ]]; then
    echo "ERROR: 需要 CONFIRM_DROP_FORUM_DB=YES"
    exit 1
  fi
  mysql_exec -e "DROP DATABASE IF EXISTS \`forum_db\`;"
  echo "forum_db dropped"
}

show_status() {
  echo "==> databases"
  mysql_exec -e "SHOW DATABASES;" </dev/null
  echo "==> source table count"
  if db_exists "forum_db"; then
    echo "forum_db	$(table_count forum_db)"
  else
    echo "forum_db	(不存在)"
  fi
  echo "==> domain table counts"
  local d
  for d in "${DOMAINS[@]}"; do
    echo "forum_${d}_db	$(table_count "forum_${d}_db")"
  done
}

main() {
  ensure_tools
  ensure_schema_files
  echo "ROOT=$ROOT ACTION=$ACTION CONTAINER=${MYSQL_CONTAINER:-"(direct $MYSQL_HOST:$MYSQL_PORT)"}"

  case "$ACTION" in
    users) create_users_and_grants ;;
    init) init_schemas ;;
    migrate-data) migrate_data_from_forum_db ;;
    drop-forum-db) drop_forum_db ;;
    status) show_status ;;
    *)
      echo "Usage: bash migrate-online-db.sh {users|init|migrate-data|drop-forum-db|status}"
      exit 1
      ;;
  esac

  show_status
  echo "Done."
}

main "$@"
