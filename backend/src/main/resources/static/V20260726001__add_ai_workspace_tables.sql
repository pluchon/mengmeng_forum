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
