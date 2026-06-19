-- ----------------------------
-- 游戏中心 / 五子棋增量脚本
-- 适用范围：在已有论坛库上增量增加游戏中心首个游戏（五子棋）所需表结构与种子数据
-- 执行方式：连接当前业务库后直接执行本文件
-- 注意：本脚本不包含 DROP TABLE，可重复执行
-- ----------------------------

CREATE TABLE IF NOT EXISTS `game_definition` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '游戏ID',
    `game_code` varchar(64) NOT NULL COMMENT '游戏编码，如 gobang',
    `game_name` varchar(64) NOT NULL COMMENT '游戏名称',
    `cover_url` varchar(512) DEFAULT NULL COMMENT '游戏封面图地址',
    `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态 1启用 0停用',
    `sort` int NOT NULL DEFAULT 0 COMMENT '排序值',
    `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除: 0否 1是',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_game_code` (`game_code`),
    KEY `idx_game_status_sort` (`status`, `delete_state`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='游戏定义表';

INSERT INTO `game_definition` (`game_code`, `game_name`, `cover_url`, `status`, `sort`, `delete_state`)
VALUES ('gobang', '五子棋', NULL, 1, 10, 0)
ON DUPLICATE KEY UPDATE
    `game_name` = VALUES(`game_name`),
    `status` = VALUES(`status`),
    `sort` = VALUES(`sort`),
    `delete_state` = VALUES(`delete_state`);

CREATE TABLE IF NOT EXISTS `game_user_profile` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '资料ID',
    `user_id` bigint NOT NULL COMMENT '用户ID',
    `game_code` varchar(64) NOT NULL COMMENT '游戏编码',
    `score` int NOT NULL DEFAULT 1000 COMMENT '历史游戏分段积分，当前展示与结算使用论坛积分',
    `total_count` int NOT NULL DEFAULT 0 COMMENT '总对局数',
    `win_count` int NOT NULL DEFAULT 0 COMMENT '胜局数',
    `lose_count` int NOT NULL DEFAULT 0 COMMENT '负局数',
    `draw_count` int NOT NULL DEFAULT 0 COMMENT '平局数，预留',
    `current_status` varchar(32) NOT NULL DEFAULT 'IDLE' COMMENT '当前状态 IDLE/MATCHING/PLAYING',
    `current_room_id` varchar(64) DEFAULT NULL COMMENT '当前房间ID',
    `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除: 0否 1是',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_game_user` (`user_id`, `game_code`),
    KEY `idx_game_score` (`game_code`, `score`, `delete_state`),
    KEY `idx_game_status` (`game_code`, `current_status`, `delete_state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='游戏用户资料表';

CREATE TABLE IF NOT EXISTS `game_match_record` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `game_code` varchar(64) NOT NULL COMMENT '游戏编码',
    `room_id` varchar(64) NOT NULL COMMENT '房间ID',
    `black_user_id` bigint NOT NULL COMMENT '黑方用户ID',
    `white_user_id` bigint NOT NULL COMMENT '白方用户ID',
    `winner_user_id` bigint DEFAULT NULL COMMENT '胜方用户ID',
    `loser_user_id` bigint DEFAULT NULL COMMENT '负方用户ID',
    `end_reason` varchar(32) NOT NULL COMMENT '结束原因 FIVE/SURRENDER/DISCONNECT/TIMEOUT/ABNORMAL',
    `score_delta` int NOT NULL DEFAULT 10 COMMENT '本局胜负积分变化绝对值',
    `started_at` datetime NOT NULL COMMENT '对局开始时间',
    `ended_at` datetime NOT NULL COMMENT '对局结束时间',
    `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除: 0否 1是',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_game_room_record` (`game_code`, `room_id`),
    KEY `idx_black_time` (`black_user_id`, `ended_at`),
    KEY `idx_white_time` (`white_user_id`, `ended_at`),
    KEY `idx_winner_time` (`winner_user_id`, `ended_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='游戏对局记录表';

CREATE TABLE IF NOT EXISTS `game_room_player` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `game_code` varchar(64) NOT NULL COMMENT '游戏编码',
    `room_id` varchar(64) NOT NULL COMMENT '房间ID',
    `user_id` bigint NOT NULL COMMENT '用户ID，AI使用负数虚拟ID',
    `room_role` varchar(32) NOT NULL COMMENT '房间角色 BLACK/WHITE/SPECTATOR/AI',
    `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除: 0否 1是',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_room_role` (`game_code`, `room_id`, `room_role`, `delete_state`),
    KEY `idx_user_room` (`user_id`, `game_code`, `delete_state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='游戏房间玩家映射表';

CREATE TABLE IF NOT EXISTS `game_room_move` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `game_code` varchar(64) NOT NULL COMMENT '游戏编码',
    `room_id` varchar(64) NOT NULL COMMENT '房间ID',
    `move_no` int NOT NULL COMMENT '步号，从1开始',
    `user_id` bigint NOT NULL COMMENT '落子用户ID',
    `row_index` int NOT NULL COMMENT '行号',
    `col_index` int NOT NULL COMMENT '列号',
    `chess` int NOT NULL COMMENT '棋子颜色：1黑 2白',
    `spent_ms` bigint NOT NULL DEFAULT 0 COMMENT '该步耗时毫秒',
    `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除: 0否 1是',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_room_move_no` (`game_code`, `room_id`, `move_no`),
    KEY `idx_room_moves` (`game_code`, `room_id`, `delete_state`, `move_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='游戏房间落子记录表';

DROP PROCEDURE IF EXISTS `migrate_game_room_move_columns`;
DELIMITER $$
CREATE PROCEDURE `migrate_game_room_move_columns`()
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'game_room_move'
          AND column_name = 'row'
    ) THEN
        ALTER TABLE `game_room_move` CHANGE COLUMN `row` `row_index` int NOT NULL COMMENT '行号';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'game_room_move'
          AND column_name = 'col'
    ) THEN
        ALTER TABLE `game_room_move` CHANGE COLUMN `col` `col_index` int NOT NULL COMMENT '列号';
    END IF;
END$$
DELIMITER ;
CALL `migrate_game_room_move_columns`();
DROP PROCEDURE IF EXISTS `migrate_game_room_move_columns`;
