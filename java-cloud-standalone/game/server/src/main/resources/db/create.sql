-- game 域最终空库基线；本文件不会删除已有数据库或表。
-- 仅对全新空库执行；已有表时应失败并改用经过审核的前向迁移。

-- 必须先声明会话字符集：下方 mysqldump 风格的 character_set_client 只在每个 CREATE TABLE
-- 前后成对生效，到 seed INSERT 时已还原成客户端默认值。若客户端默认是 latin1，
-- 所有中文初始化数据会被按 latin1 写入而变成乱码。
SET NAMES utf8mb4;

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `forum_game_db` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `forum_game_db`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `game_definition` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '游戏ID',
  `game_code` varchar(64) NOT NULL COMMENT '游戏编码，如 gobang',
  `game_name` varchar(64) NOT NULL COMMENT '游戏名称',
  `cover_url` varchar(512) DEFAULT NULL COMMENT '游戏封面图地址',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态 1启用 0停用',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序值',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除: 0否 1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_game_code` (`game_code`),
  KEY `idx_game_status_sort` (`status`,`delete_state`,`sort`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='游戏定义表';

INSERT INTO `game_definition` (`id`, `game_code`, `game_name`, `cover_url`, `status`, `sort`, `delete_state`) VALUES
(1, 'gobang', '五子棋', NULL, 1, 1, 0),
(2, 'jinzi', '井字棋', NULL, 1, 2, 0),
(3, 'tetris', '俄罗斯方块单人', NULL, 1, 3, 0),
(4, 'tetris_pk', '俄罗斯方块竞速', NULL, 1, 4, 0);
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `game_gobang_match_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `room_id` varchar(64) NOT NULL COMMENT '房间ID',
  `black_user_id` bigint NOT NULL COMMENT '黑方用户ID',
  `white_user_id` bigint NOT NULL COMMENT '白方用户ID',
  `winner_user_id` bigint DEFAULT NULL COMMENT '胜方用户ID',
  `loser_user_id` bigint DEFAULT NULL COMMENT '负方用户ID',
  `end_reason` varchar(32) NOT NULL COMMENT '结束原因 FIVE/SURRENDER/DISCONNECT/TIMEOUT/ABNORMAL',
  `score_delta` int NOT NULL DEFAULT '10' COMMENT '本局胜负积分变化绝对值',
  `winner_score_delta` int NOT NULL DEFAULT '0' COMMENT '胜方本局排位分变化',
  `loser_score_delta` int NOT NULL DEFAULT '0' COMMENT '败方本局排位分变化，负数',
  `started_at` datetime NOT NULL COMMENT '对局开始时间',
  `ended_at` datetime NOT NULL COMMENT '对局结束时间',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除: 0否 1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_gobang_room_record` (`room_id`),
  KEY `idx_gobang_black_time` (`black_user_id`,`ended_at`),
  KEY `idx_gobang_white_time` (`white_user_id`,`ended_at`),
  KEY `idx_gobang_winner_time` (`winner_user_id`,`ended_at`)
) ENGINE=InnoDB AUTO_INCREMENT=21 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='五子棋对局记录表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `game_gobang_room_move` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `room_id` varchar(64) NOT NULL COMMENT '房间ID',
  `move_no` int NOT NULL COMMENT '步号，从1开始',
  `user_id` bigint NOT NULL COMMENT '落子用户ID',
  `row_index` int NOT NULL COMMENT '行号',
  `col_index` int NOT NULL COMMENT '列号',
  `chess` int NOT NULL COMMENT '棋子颜色：1黑 2白',
  `spent_ms` bigint NOT NULL DEFAULT '0' COMMENT '该步耗时毫秒',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除: 0否 1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_gobang_room_move_no` (`room_id`,`move_no`),
  KEY `idx_gobang_room_moves` (`room_id`,`delete_state`,`move_no`)
) ENGINE=InnoDB AUTO_INCREMENT=40 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='五子棋房间落子记录表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `game_jinzi_match_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `room_id` varchar(64) NOT NULL COMMENT '房间ID',
  `black_user_id` bigint NOT NULL COMMENT '黑方用户ID',
  `white_user_id` bigint NOT NULL COMMENT '白方用户ID',
  `winner_user_id` bigint DEFAULT NULL COMMENT '胜方用户ID',
  `loser_user_id` bigint DEFAULT NULL COMMENT '负方用户ID',
  `end_reason` varchar(32) NOT NULL COMMENT '结束原因 THREE/SURRENDER/DISCONNECT/TIMEOUT/ABNORMAL/DRAW',
  `score_delta` int NOT NULL DEFAULT '10' COMMENT '本局胜负积分变化绝对值',
  `winner_score_delta` int NOT NULL DEFAULT '0' COMMENT '胜方本局排位分变化',
  `loser_score_delta` int NOT NULL DEFAULT '0' COMMENT '败方本局排位分变化，负数',
  `started_at` datetime NOT NULL COMMENT '对局开始时间',
  `ended_at` datetime NOT NULL COMMENT '对局结束时间',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除: 0否 1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_jinzi_room_record` (`room_id`),
  KEY `idx_jinzi_black_time` (`black_user_id`,`ended_at`),
  KEY `idx_jinzi_white_time` (`white_user_id`,`ended_at`),
  KEY `idx_jinzi_winner_time` (`winner_user_id`,`ended_at`)
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='井字棋对局记录表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `game_jinzi_room_move` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `room_id` varchar(64) NOT NULL COMMENT '房间ID',
  `move_no` int NOT NULL COMMENT '步号，从1开始',
  `user_id` bigint NOT NULL COMMENT '落子用户ID',
  `row_index` int NOT NULL COMMENT '行号',
  `col_index` int NOT NULL COMMENT '列号',
  `chess` int NOT NULL COMMENT '棋子颜色：1黑 2白',
  `spent_ms` bigint NOT NULL DEFAULT '0' COMMENT '该步耗时毫秒',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除: 0否 1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_jinzi_room_move_no` (`room_id`,`move_no`),
  KEY `idx_jinzi_room_moves` (`room_id`,`delete_state`,`move_no`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='井字棋房间落子记录表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `game_match_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `game_code` varchar(64) NOT NULL COMMENT '游戏编码',
  `room_id` varchar(64) NOT NULL COMMENT '房间ID',
  `black_user_id` bigint NOT NULL COMMENT '黑方用户ID',
  `white_user_id` bigint NOT NULL COMMENT '白方用户ID',
  `winner_user_id` bigint DEFAULT NULL COMMENT '胜方用户ID',
  `loser_user_id` bigint DEFAULT NULL COMMENT '负方用户ID',
  `end_reason` varchar(32) NOT NULL COMMENT '结束原因 FIVE/SURRENDER/DISCONNECT/TIMEOUT/ABNORMAL',
  `score_delta` int NOT NULL DEFAULT '10' COMMENT '本局胜负积分变化绝对值',
  `started_at` datetime NOT NULL COMMENT '对局开始时间',
  `ended_at` datetime NOT NULL COMMENT '对局结束时间',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除: 0否 1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_game_room_record` (`game_code`,`room_id`),
  KEY `idx_black_time` (`black_user_id`,`ended_at`),
  KEY `idx_white_time` (`white_user_id`,`ended_at`),
  KEY `idx_winner_time` (`winner_user_id`,`ended_at`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='游戏对局记录表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `game_room_move` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `game_code` varchar(64) NOT NULL COMMENT '游戏编码',
  `room_id` varchar(64) NOT NULL COMMENT '房间ID',
  `move_no` int NOT NULL COMMENT '步号，从1开始',
  `user_id` bigint NOT NULL COMMENT '落子用户ID',
  `row_index` int NOT NULL COMMENT '行号',
  `col_index` int NOT NULL COMMENT '列号',
  `chess` int NOT NULL COMMENT '棋子颜色：1黑 2白',
  `spent_ms` bigint NOT NULL DEFAULT '0' COMMENT '该步耗时毫秒',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除: 0否 1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_room_move_no` (`game_code`,`room_id`,`move_no`),
  KEY `idx_room_moves` (`game_code`,`room_id`,`delete_state`,`move_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='游戏房间落子记录表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `game_room_player` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `game_code` varchar(64) NOT NULL COMMENT '游戏编码',
  `room_id` varchar(64) NOT NULL COMMENT '房间ID',
  `user_id` bigint NOT NULL COMMENT '用户ID，AI使用负数虚拟ID',
  `room_role` varchar(32) NOT NULL COMMENT '房间角色 BLACK/WHITE/SPECTATOR/AI',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除: 0否 1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_room_role` (`game_code`,`room_id`,`room_role`,`delete_state`),
  KEY `idx_user_room` (`user_id`,`game_code`,`delete_state`)
) ENGINE=InnoDB AUTO_INCREMENT=72 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='游戏房间玩家映射表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `game_settlement_event` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '事件ID',
  `event_id` varchar(64) NOT NULL COMMENT '事件唯一ID',
  `game_code` varchar(64) NOT NULL COMMENT '游戏编码',
  `room_id` varchar(64) NOT NULL COMMENT '房间ID',
  `event_type` varchar(64) NOT NULL COMMENT '事件类型，如 GAME_FINISHED',
  `record_id` bigint DEFAULT NULL COMMENT '关联对局记录ID',
  `status` varchar(32) NOT NULL DEFAULT 'CREATED' COMMENT '事件状态 CREATED/MQ_SENT/MQ_PENDING/CONSUMED/DEAD',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT '重试次数',
  `last_error` varchar(512) DEFAULT NULL COMMENT '最近一次错误摘要',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除: 0否 1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_game_event_id` (`event_id`),
  UNIQUE KEY `uk_game_room_event` (`game_code`,`room_id`,`event_type`),
  KEY `idx_game_event_status` (`game_code`,`status`,`delete_state`,`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=34 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='游戏结算事件表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `game_tetris_pk_match_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `room_id` varchar(64) NOT NULL COMMENT '房间ID',
  `player1_user_id` bigint NOT NULL COMMENT '玩家1用户ID',
  `player2_user_id` bigint NOT NULL COMMENT '玩家2用户ID',
  `red_user_id` bigint NOT NULL COMMENT '红方用户ID',
  `blue_user_id` bigint NOT NULL COMMENT '蓝方用户ID',
  `winner_user_id` bigint DEFAULT NULL COMMENT '胜方用户ID',
  `loser_user_id` bigint DEFAULT NULL COMMENT '败方用户ID',
  `player1_score` int NOT NULL DEFAULT '0' COMMENT '玩家1得分',
  `player2_score` int NOT NULL DEFAULT '0' COMMENT '玩家2得分',
  `player1_lines` int NOT NULL DEFAULT '0' COMMENT '玩家1消行数',
  `player2_lines` int NOT NULL DEFAULT '0' COMMENT '玩家2消行数',
  `end_reason` varchar(32) NOT NULL DEFAULT '' COMMENT '结束原因',
  `score_delta` int NOT NULL DEFAULT '3' COMMENT '积分变动',
  `winner_score_delta` int NOT NULL DEFAULT '0' COMMENT '胜方本局排位分变化',
  `loser_score_delta` int NOT NULL DEFAULT '0' COMMENT '败方本局排位分变化，负数',
  `replay_payload` mediumtext COMMENT '回放JSON',
  `started_at` datetime NOT NULL COMMENT '开始时间',
  `ended_at` datetime NOT NULL COMMENT '结束时间',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除: 0否 1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_tetris_pk_room` (`room_id`),
  KEY `idx_tetris_pk_player1` (`player1_user_id`,`delete_state`,`ended_at`),
  KEY `idx_tetris_pk_player2` (`player2_user_id`,`delete_state`,`ended_at`)
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='俄罗斯方块PK对局记录';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `game_tetris_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `game_code` varchar(64) NOT NULL DEFAULT 'tetris' COMMENT '游戏编码',
  `score` int NOT NULL DEFAULT '0' COMMENT '本局分数',
  `level` int NOT NULL DEFAULT '1' COMMENT '结束时等级',
  `lines_cleared` int NOT NULL DEFAULT '0' COMMENT '总消行数',
  `duration_ms` bigint NOT NULL DEFAULT '0' COMMENT '局时长毫秒',
  `seed` bigint NOT NULL COMMENT '随机种子',
  `replay_payload` mediumtext NOT NULL COMMENT '回放JSON',
  `forum_points_awarded` int NOT NULL DEFAULT '0' COMMENT '本次论坛积分奖励',
  `validation_status` varchar(16) NOT NULL DEFAULT 'VALID' COMMENT '校验状态 VALID/REJECTED',
  `replay_score` int DEFAULT NULL COMMENT '服务端重放算出的分数; NULL 表示未校验',
  `started_at` datetime NOT NULL COMMENT '开局时间',
  `ended_at` datetime NOT NULL COMMENT '结束时间',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除: 0否 1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_tetris_user_time` (`user_id`,`delete_state`,`ended_at`),
  KEY `idx_tetris_score` (`game_code`,`delete_state`,`score`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='俄罗斯方块单人局记录';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `game_user_profile` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '资料ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `game_code` varchar(64) NOT NULL COMMENT '游戏编码',
  `score` int NOT NULL DEFAULT '1000' COMMENT '历史游戏分段积分，当前展示与结算使用论坛积分',
  `total_count` int NOT NULL DEFAULT '0' COMMENT '总对局数',
  `win_count` int NOT NULL DEFAULT '0' COMMENT '胜局数',
  `lose_count` int NOT NULL DEFAULT '0' COMMENT '负局数',
  `draw_count` int NOT NULL DEFAULT '0' COMMENT '平局数，预留',
  `current_status` varchar(32) NOT NULL DEFAULT 'IDLE' COMMENT '当前状态 IDLE/MATCHING/PLAYING',
  `current_room_id` varchar(64) DEFAULT NULL COMMENT '当前房间ID',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除: 0否 1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_game_user` (`user_id`,`game_code`),
  KEY `idx_game_score` (`game_code`,`score`,`delete_state`),
  KEY `idx_game_status` (`game_code`,`current_status`,`delete_state`)
) ENGINE=InnoDB AUTO_INCREMENT=36 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='游戏用户资料表';
/*!40101 SET character_set_client = @saved_cs_client */;
