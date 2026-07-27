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
