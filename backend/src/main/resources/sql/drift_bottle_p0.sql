-- 漂流瓶 P0：匿名树洞、打捞、评论与举报
CREATE TABLE IF NOT EXISTS `drift_bottle` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '漂流瓶ID',
    `user_id` bigint NOT NULL COMMENT '真实作者用户ID',
    `content` varchar(500) NOT NULL COMMENT '瓶子内容',
    `mood_type` varchar(20) NOT NULL COMMENT '心情标签',
    `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态: 0可见 1隐藏 2删除',
    `comment_count` int NOT NULL DEFAULT 0 COMMENT '评论数量',
    `picked_count` int NOT NULL DEFAULT 0 COMMENT '被捞次数',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0否 1是',
    PRIMARY KEY (`id`),
    KEY `idx_user_time` (`user_id`, `create_time`),
    KEY `idx_visible_time` (`status`, `delete_state`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='漂流瓶主表';

CREATE TABLE IF NOT EXISTS `drift_bottle_comment` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '评论ID',
    `bottle_id` bigint NOT NULL COMMENT '漂流瓶ID',
    `user_id` bigint NOT NULL COMMENT '真实评论用户ID',
    `content` varchar(200) NOT NULL COMMENT '评论内容',
    `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态: 0可见 1隐藏 2删除',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0否 1是',
    PRIMARY KEY (`id`),
    KEY `idx_bottle_time` (`bottle_id`, `status`, `delete_state`, `create_time`),
    KEY `idx_user_time` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='漂流瓶评论表';

CREATE TABLE IF NOT EXISTS `drift_bottle_pick_log` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '打捞记录ID',
    `bottle_id` bigint NOT NULL COMMENT '漂流瓶ID',
    `user_id` bigint NOT NULL COMMENT '打捞用户ID',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0否 1是',
    PRIMARY KEY (`id`),
    KEY `idx_user_time` (`user_id`, `create_time`),
    KEY `idx_user_bottle` (`user_id`, `bottle_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='漂流瓶打捞记录表';

CREATE TABLE IF NOT EXISTS `drift_bottle_report` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '举报ID',
    `target_type` tinyint NOT NULL COMMENT '目标类型: 0瓶子 1评论',
    `target_id` bigint NOT NULL COMMENT '目标ID',
    `report_user_id` bigint NOT NULL COMMENT '举报用户ID',
    `reason_type` varchar(30) NOT NULL COMMENT '举报原因类型',
    `reason_detail` varchar(200) DEFAULT NULL COMMENT '举报补充说明',
    `status` tinyint NOT NULL DEFAULT 0 COMMENT '处理状态: 0待处理 1已处理 2已驳回',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0否 1是',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_report_once` (`target_type`, `target_id`, `report_user_id`, `delete_state`),
    KEY `idx_target_status` (`target_type`, `target_id`, `status`, `delete_state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='漂流瓶举报表';
