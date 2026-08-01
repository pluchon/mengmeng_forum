# 表归属与拆库复制清单

默认仍连接共享 `forum_db`。设置各服务 `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` 后切到独立库。

## auth → `forum_auth_db`

- `user`
- `user_login_log`
- `user_follow`
- 可选：`sys_*` 管理字典（若仍由 auth 承载）

## content → `forum_content_db`

- `article`, `article_*`, `board`, `category`
- `article_favorite`, `user_favorite_folder`
- `forum_article_tag*`, `forum_article_ai_feature`
- `forum_user_ai_profile_snapshot`
- `user_interest_preference`, `user_recommend_feedback`
- content 侧 outbox（若有独立表则列入；当前 `forum_outbox_message` 归属 im）

## im → `forum_im_db`

- `message`, `system_message`, `forum_notice`
- `group_chat*`
- `user_chat_emoji`
- `forum_outbox_message`

## game → `forum_game_db`

- `game_*`（含 `game_settlement_event`）

## economy → `forum_economy_db`

- `points_wallet`, `points_log`
- `checkin_*`, `user_checkin_info`
- `lottery_*`, `user_lottery_pity`
- `emoji_shop`, `emoji_item`, `user_emoji`
- `growth_*`, `user_growth_profile`, `exam_question*`
- `user_vip_subscription`, `vip_trial_entitlement`, `forum_vip_quota_config`

## ai → `forum_ai_db`

- `ai_usage_daily`, `forum_ai_*`
- `forum_companion_*`, `forum_mascot_*`, `user_mascot_preference`
- `drift_bottle*`

## 复制方式

见同目录 `copy-tables-from-forum-db.sql`（`CREATE TABLE ... LIKE` + `INSERT ... SELECT`）。

切库后：

1. 撤销服务账号对其它库的 `GRANT`
2. 冒烟本域读写 + 跨域 Feign
3. 保留回滚：临时把 `DB_URL` 指回 `forum_db`
