-- 推荐 AI 特征与用户画像增量（2026-07-29）
-- 仅保存公开帖子派生特征与用户脱敏聚合画像，可重复执行。

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
