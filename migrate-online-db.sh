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
#   # 3) 若仍存在旧 forum_db：把表搬到六域库后可选删除 forum_db
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
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-123456789}"
CONFIRM_DROP="${CONFIRM_DROP:-NO}"
CONFIRM_DROP_FORUM_DB="${CONFIRM_DROP_FORUM_DB:-NO}"
USE_DIRECT_MYSQL="${USE_DIRECT_MYSQL:-0}"

# 未指定容器且未强制直连时，自动探测常见 MySQL 容器名
if [[ -z "$MYSQL_CONTAINER" && "$USE_DIRECT_MYSQL" != "1" ]]; then
  if command -v docker >/dev/null 2>&1; then
    for c in forum-mysql forum-mysql-dev; do
      if docker inspect "$c" >/dev/null 2>&1; then
        MYSQL_CONTAINER="$c"
        break
      fi
    done
  fi
fi

DB_AUTH_USER="${DB_AUTH_USER:-forum_auth}"
DB_AUTH_PASS="${DB_AUTH_PASS:-forum_auth_pass}"
DB_CONTENT_USER="${DB_CONTENT_USER:-forum_content}"
DB_CONTENT_PASS="${DB_CONTENT_PASS:-forum_content_pass}"
DB_IM_USER="${DB_IM_USER:-forum_im}"
DB_IM_PASS="${DB_IM_PASS:-forum_im_pass}"
DB_GAME_USER="${DB_GAME_USER:-forum_game}"
DB_GAME_PASS="${DB_GAME_PASS:-forum_game_pass}"
DB_ECONOMY_USER="${DB_ECONOMY_USER:-forum_economy}"
DB_ECONOMY_PASS="${DB_ECONOMY_PASS:-forum_economy_pass}"
DB_AI_USER="${DB_AI_USER:-forum_ai}"
DB_AI_PASS="${DB_AI_PASS:-forum_ai_pass}"

DOMAINS=(auth content im game economy ai)

sql_file_for() {
  local domain="$1"
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

ensure_tools() {
  if [[ -n "$MYSQL_CONTAINER" ]]; then
    docker inspect "$MYSQL_CONTAINER" >/dev/null 2>&1 || {
      echo "ERROR: container not found: $MYSQL_CONTAINER"
      exit 1
    }
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
  local out
  out="$(mysql_exec -N -e "SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='${name}';" 2>/dev/null | tr -d '\r')"
  [[ "$out" == "$name" ]]
}

list_tables() {
  local schema="$1"
  mysql_exec -N -e "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA='${schema}' AND TABLE_TYPE='BASE TABLE' ORDER BY TABLE_NAME;" 2>/dev/null | tr -d '\r'
}

migrate_data_from_forum_db() {
  if ! db_exists "forum_db"; then
    echo "INFO: forum_db 不存在，跳过数据迁移（仅确保六域库与账号）。"
    create_users_and_grants
    return 0
  fi

  echo "==> migrate tables from forum_db -> domain DBs"
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

  local table target skipped=0 moved=0
  while IFS= read -r table; do
    [[ -n "$table" ]] || continue
    target="${TABLE_OWNER[$table]:-}"
    if [[ -z "$target" ]]; then
      echo "WARN: 未映射表 forum_db.${table}，跳过"
      skipped=$((skipped + 1))
      continue
    fi
    echo "  -> ${table} => ${target}.${table}"
    mysql_exec <<SQL
CREATE TABLE IF NOT EXISTS \`${target}\`.\`${table}\` LIKE \`forum_db\`.\`${table}\`;
SQL
    local cnt
    cnt="$(mysql_exec -N -e "SELECT COUNT(*) FROM \`${target}\`.\`${table}\`;" 2>/dev/null | tr -d '\r')"
    if [[ "${cnt:-0}" != "0" ]]; then
      echo "     skip: target already has ${cnt} rows"
      continue
    fi
    mysql_exec <<SQL
INSERT INTO \`${target}\`.\`${table}\` SELECT * FROM \`forum_db\`.\`${table}\`;
SQL
    moved=$((moved + 1))
  done < <(list_tables "forum_db")

  echo "migrated tables: $moved, skipped unmapped: $skipped"

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
  mysql_exec -e "SHOW DATABASES;"
  echo "==> domain table counts"
  local d
  for d in auth content im game economy ai; do
    mysql_exec -N -e "SELECT '${d}', COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='forum_${d}_db' AND TABLE_TYPE='BASE TABLE';" 2>/dev/null || true
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
