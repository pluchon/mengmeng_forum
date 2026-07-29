-- MySQL 线上合并增量（2026-07-29）
-- 执行对象：已部署的 forum_db。本文件整合 AI 模块、看板娘上下文与推荐画像增量。
-- 可重复执行：建表、补列、索引及历史模型配置清理均为幂等操作。
-- 默认不清空历史会话；仅在确认切换旧 AI 路由且允许删除历史时，将下方变量改为 1。

SET @purge_legacy_ai_history = 0;

CREATE TABLE IF NOT EXISTS forum_ai_creation_workspace (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    companion_session_id BIGINT DEFAULT NULL,
    workspace_state VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    selected_version_id BIGINT DEFAULT NULL,
    checkpoint_id VARCHAR(128) DEFAULT NULL,
    delete_state TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_ai_workspace_user_time (user_id, delete_state, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI创作工作区';

CREATE TABLE IF NOT EXISTS forum_ai_creation_version (
    id BIGINT NOT NULL AUTO_INCREMENT,
    workspace_id BIGINT NOT NULL,
    parent_version_id BIGINT DEFAULT NULL,
    artifact_type VARCHAR(32) NOT NULL,
    version_no INT NOT NULL,
    artifact_json MEDIUMTEXT NOT NULL,
    selected TINYINT NOT NULL DEFAULT 0,
    delete_state TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_creation_version (workspace_id, artifact_type, version_no),
    KEY idx_ai_creation_version_workspace (workspace_id, delete_state, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI创作产物版本';

CREATE TABLE IF NOT EXISTS forum_ai_task_session (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    companion_session_id BIGINT DEFAULT NULL,
    workspace_id BIGINT DEFAULT NULL,
    active_module VARCHAR(64) DEFAULT NULL,
    active_worker VARCHAR(64) DEFAULT NULL,
    checkpoint_id VARCHAR(128) DEFAULT NULL,
    task_mode VARCHAR(24) NOT NULL DEFAULT 'ASSISTANT',
    task_state VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    delete_state TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_ai_task_session_user (user_id, task_state, delete_state, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI任务会话状态';

CREATE TABLE IF NOT EXISTS forum_ai_long_term_memory (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    source_session_id BIGINT DEFAULT NULL,
    memory_type VARCHAR(32) NOT NULL,
    content VARCHAR(1000) NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    delete_state TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_ai_memory_user (user_id, enabled, delete_state, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI会员长期记忆';

CREATE TABLE IF NOT EXISTS forum_mascot_related_recommendation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    companion_session_id BIGINT NOT NULL,
    source_message_id BIGINT DEFAULT NULL,
    query VARCHAR(500) NOT NULL,
    result_state VARCHAR(16) NOT NULL,
    result_count INT NOT NULL DEFAULT 0,
    delete_state TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_mascot_related_user_session_time (user_id, companion_session_id, delete_state, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='看板娘已确认相关帖子检索';

CREATE TABLE IF NOT EXISTS forum_mascot_related_recommendation_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    recommendation_id BIGINT NOT NULL,
    article_id BIGINT NOT NULL,
    display_order INT NOT NULL,
    selection_reason VARCHAR(16) NOT NULL,
    delete_state TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mascot_related_recommendation_article (recommendation_id, article_id),
    KEY idx_mascot_related_item_recommendation (recommendation_id, delete_state, display_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='看板娘相关帖子检索结果项';

CREATE TABLE IF NOT EXISTS forum_article_ai_feature (
    id BIGINT NOT NULL AUTO_INCREMENT,
    article_id BIGINT NOT NULL,
    feature_json MEDIUMTEXT NOT NULL,
    feature_version VARCHAR(32) NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    generated_by VARCHAR(32) NOT NULL,
    delete_state TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_article_ai_feature_article (article_id),
    KEY idx_article_ai_feature_state_time (delete_state, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子推荐AI特征';

CREATE TABLE IF NOT EXISTS forum_user_ai_profile_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    profile_version BIGINT NOT NULL DEFAULT 0,
    profile_json MEDIUMTEXT NOT NULL,
    feature_version VARCHAR(32) NOT NULL,
    source_window_start DATETIME DEFAULT NULL,
    source_window_end DATETIME DEFAULT NULL,
    refresh_after DATETIME NOT NULL,
    generated_by VARCHAR(32) NOT NULL,
    delete_state TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_ai_profile_snapshot_user (user_id),
    KEY idx_user_ai_profile_snapshot_refresh (delete_state, refresh_after)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户推荐AI画像快照';

DELETE FROM forum_ai_model_price
WHERE provider NOT IN ('dashscope', 'huanapi');

DELETE FROM forum_vip_quota_config
WHERE icon_provider NOT IN ('qwen', 'openai', 'huanapi');

SET @legacy_column = CONCAT('deep', 'seek_write_used');
SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'ai_usage_daily'
      AND column_name = @legacy_column
);
SET @rename_sql = IF(
    @column_exists > 0,
    CONCAT('ALTER TABLE ai_usage_daily RENAME COLUMN `', @legacy_column, '` TO `qwen_flash_used`'),
    'SELECT 1'
);
PREPARE migration_statement FROM @rename_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

DELETE FROM forum_companion_message WHERE @purge_legacy_ai_history = 1;
DELETE FROM forum_companion_session WHERE @purge_legacy_ai_history = 1;
DELETE FROM forum_ai_call_record WHERE @purge_legacy_ai_history = 1;
DELETE FROM forum_ai_usage_log WHERE @purge_legacy_ai_history = 1;
DELETE FROM forum_ai_model_usage_daily WHERE @purge_legacy_ai_history = 1;
DELETE FROM ai_usage_daily WHERE @purge_legacy_ai_history = 1;

SET @companion_metadata_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'forum_companion_message'
      AND column_name = 'metadata_json'
);
SET @companion_metadata_sql = IF(
    @companion_metadata_exists = 0,
    'ALTER TABLE forum_companion_message ADD COLUMN metadata_json MEDIUMTEXT NULL COMMENT ''消息扩展元数据（联网图集、上下文摘要来源等）'' AFTER image_url',
    'SELECT 1'
);
PREPARE companion_metadata_statement FROM @companion_metadata_sql;
EXECUTE companion_metadata_statement;
DEALLOCATE PREPARE companion_metadata_statement;

SET @related_source_message_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'forum_mascot_related_recommendation'
      AND column_name = 'source_message_id'
);
SET @related_source_message_sql = IF(
    @related_source_message_exists = 0,
    'ALTER TABLE forum_mascot_related_recommendation ADD COLUMN source_message_id BIGINT NULL AFTER companion_session_id',
    'SELECT 1'
);
PREPARE related_source_message_statement FROM @related_source_message_sql;
EXECUTE related_source_message_statement;
DEALLOCATE PREPARE related_source_message_statement;

SET @recommend_feedback_reason_code_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'user_recommend_feedback'
      AND column_name = 'reason_code'
);
SET @recommend_feedback_reason_code_sql = IF(
    @recommend_feedback_reason_code_exists = 0,
    'ALTER TABLE user_recommend_feedback ADD COLUMN reason_code VARCHAR(32) NOT NULL DEFAULT ''UNRELATED'' AFTER article_id',
    'SELECT 1'
);
PREPARE recommend_feedback_reason_code_statement FROM @recommend_feedback_reason_code_sql;
EXECUTE recommend_feedback_reason_code_statement;
DEALLOCATE PREPARE recommend_feedback_reason_code_statement;

SET @recommend_feedback_reason_detail_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'user_recommend_feedback'
      AND column_name = 'reason_detail'
);
SET @recommend_feedback_reason_detail_sql = IF(
    @recommend_feedback_reason_detail_exists = 0,
    'ALTER TABLE user_recommend_feedback ADD COLUMN reason_detail VARCHAR(200) NULL AFTER reason_code',
    'SELECT 1'
);
PREPARE recommend_feedback_reason_detail_statement FROM @recommend_feedback_reason_detail_sql;
EXECUTE recommend_feedback_reason_detail_statement;
DEALLOCATE PREPARE recommend_feedback_reason_detail_statement;
