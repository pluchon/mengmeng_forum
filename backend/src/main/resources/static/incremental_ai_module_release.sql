-- AI 模块线上合并增量（2026-07-28）
-- 执行对象：已部署的 forum_db。
-- 可重复执行：建表、索引和模型配置清理均为幂等操作。
-- 默认不会清空历史会话；仅在首次切换旧 AI 路由且确认可删除历史时，将下方变量改为 1。

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

-- 看板娘：联网图集与上下文压缩事件
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
