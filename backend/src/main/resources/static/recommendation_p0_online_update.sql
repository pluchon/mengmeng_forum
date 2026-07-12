-- 为你推荐 P0：兴趣偏好与“不想看这篇”反馈（MySQL 8+）
-- 已有线上库直接执行；全新环境同时已在 sql/create.sql 中包含相同结构。

CREATE TABLE IF NOT EXISTS `user_interest_preference` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` bigint NOT NULL COMMENT '用户ID',
    `board_id` bigint NOT NULL COMMENT '细分板块ID；0表示当前用户的个性化开关记录',
    `personalized_enabled` tinyint NOT NULL DEFAULT 1 COMMENT '个性化开关：0关闭 1开启',
    `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否 1是',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_interest_board` (`user_id`, `board_id`),
    KEY `idx_user_interest_active` (`user_id`, `delete_state`, `board_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户推荐兴趣偏好';

CREATE TABLE IF NOT EXISTS `user_recommend_feedback` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` bigint NOT NULL COMMENT '用户ID',
    `article_id` bigint NOT NULL COMMENT '帖子ID',
    `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否 1是',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_recommend_feedback_article` (`user_id`, `article_id`),
    KEY `idx_user_recommend_feedback_active` (`user_id`, `delete_state`, `article_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户推荐帖子反馈';
