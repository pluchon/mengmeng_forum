-- AI 域最终空库基线，已合并历史增量；本文件不会删除已有数据库或表。
-- 仅对全新空库执行；已有表时应失败并改用经过审核的前向迁移。

-- 必须先声明会话字符集：下方 mysqldump 风格的 character_set_client 只在每个 CREATE TABLE
-- 前后成对生效，到 seed INSERT 时已还原成客户端默认值。若客户端默认是 latin1，
-- 所有中文初始化数据会被按 latin1 写入而变成乱码。
SET NAMES utf8mb4;

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `forum_ai_db` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `forum_ai_db`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_usage_daily` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `usage_date` date NOT NULL COMMENT '用量归属日',
  `cover_hint_used` int NOT NULL DEFAULT '0' COMMENT '封面「推荐配图要点」调用次数(不计入写作配额,仅审计)',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除: 0否 1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uix_user_usage_date` (`user_id`,`usage_date`),
  KEY `idx_usage_date` (`usage_date`)
) ENGINE=InnoDB AUTO_INCREMENT=23 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI能力每日用量';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `forum_ai_call_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `feature_code` varchar(64) NOT NULL COMMENT '功能编码',
  `client_request_id` varchar(64) NOT NULL COMMENT '客户端幂等键',
  `model_code` varchar(64) DEFAULT NULL COMMENT '计划调用模型',
  `call_state` tinyint NOT NULL DEFAULT '0' COMMENT '0待调用 1成功 2失败 3超时 4停止 5断开',
  `estimated_points` int NOT NULL DEFAULT '0' COMMENT '预估积分',
  `points_charged` int NOT NULL DEFAULT '0' COMMENT '实际扣除积分',
  `input_tokens` int NOT NULL DEFAULT '0' COMMENT '输入token',
  `output_tokens` int NOT NULL DEFAULT '0' COMMENT '输出token',
  `error_summary` varchar(200) DEFAULT NULL COMMENT '失败摘要(脱敏)',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '0否 1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_call_user_feature_request` (`user_id`,`feature_code`,`client_request_id`),
  KEY `idx_ai_call_user_time` (`user_id`,`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI调用预记录表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `forum_ai_creation_version` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `workspace_id` bigint NOT NULL,
  `parent_version_id` bigint DEFAULT NULL,
  `artifact_type` varchar(32) NOT NULL,
  `version_no` int NOT NULL,
  `artifact_json` mediumtext NOT NULL,
  `selected` tinyint NOT NULL DEFAULT '0',
  `delete_state` tinyint NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_creation_version` (`workspace_id`,`artifact_type`,`version_no`),
  KEY `idx_ai_creation_version_workspace` (`workspace_id`,`delete_state`,`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI创作产物版本';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `forum_ai_creation_workspace` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `companion_session_id` bigint DEFAULT NULL,
  `workspace_state` varchar(24) NOT NULL DEFAULT 'ACTIVE',
  `selected_version_id` bigint DEFAULT NULL,
  `checkpoint_id` varchar(128) DEFAULT NULL,
  `delete_state` tinyint NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ai_workspace_user_time` (`user_id`,`delete_state`,`update_time`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI创作工作区';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `forum_ai_long_term_memory` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `source_session_id` bigint DEFAULT NULL,
  `memory_type` varchar(32) NOT NULL,
  `content` varchar(1000) NOT NULL,
  `enabled` tinyint NOT NULL DEFAULT '1',
  `delete_state` tinyint NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ai_memory_user` (`user_id`,`enabled`,`delete_state`,`update_time`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI会员长期记忆';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `forum_ai_model_price` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `model_code` varchar(64) NOT NULL COMMENT '模型标识,与埋点一致',
  `provider` varchar(32) NOT NULL DEFAULT 'dashscope' COMMENT 'dashscope|deepseek',
  `bill_unit` varchar(32) NOT NULL COMMENT 'per_1m_input|per_1m_output|per_image|per_call',
  `price_yuan` decimal(12,6) NOT NULL COMMENT '单价(元),按 bill_unit 计量',
  `vip_only` tinyint NOT NULL DEFAULT '0' COMMENT '1=仅VIP可用深度档等',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '1启用 0停用',
  `remark` varchar(200) DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_model_bill_unit` (`model_code`,`bill_unit`)
) ENGINE=InnoDB AUTO_INCREMENT=23 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI模型单价目录';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `forum_ai_model_usage_daily` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `stat_date` date NOT NULL COMMENT '统计日(东八区日历日)',
  `model_code` varchar(64) NOT NULL COMMENT '模型标识',
  `call_count` int NOT NULL DEFAULT '0' COMMENT '当日调用次数',
  `points_spent` bigint NOT NULL DEFAULT '0' COMMENT '当日消耗积分合计',
  `input_tokens` bigint NOT NULL DEFAULT '0' COMMENT '输入token合计',
  `output_tokens` bigint NOT NULL DEFAULT '0' COMMENT '输出token合计',
  `image_count` int NOT NULL DEFAULT '0' COMMENT '生图张数合计',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_usage_day_model` (`stat_date`,`model_code`),
  KEY `idx_ai_usage_stat_date` (`stat_date`)
) ENGINE=InnoDB AUTO_INCREMENT=30 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI模型调用按日汇总';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `forum_ai_task_session` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `companion_session_id` bigint DEFAULT NULL,
  `workspace_id` bigint DEFAULT NULL,
  `active_module` varchar(64) DEFAULT NULL,
  `active_worker` varchar(64) DEFAULT NULL,
  `checkpoint_id` varchar(128) DEFAULT NULL,
  `task_mode` varchar(24) NOT NULL DEFAULT 'ASSISTANT',
  `task_state` varchar(24) NOT NULL DEFAULT 'ACTIVE',
  `delete_state` tinyint NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ai_task_session_user` (`user_id`,`task_state`,`delete_state`,`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI任务会话状态';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `forum_ai_usage_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `feature_code` varchar(64) NOT NULL COMMENT 'companion_writing|companion_help|companion_image|ai_write等',
  `model_code` varchar(64) NOT NULL COMMENT '实际模型',
  `input_tokens` int NOT NULL DEFAULT '0' COMMENT '输入token',
  `output_tokens` int NOT NULL DEFAULT '0' COMMENT '输出token',
  `image_count` int NOT NULL DEFAULT '0' COMMENT '图片张数',
  `points_cost` int NOT NULL DEFAULT '0' COMMENT '扣除积分',
  `estimated` tinyint NOT NULL DEFAULT '0' COMMENT '1=用量为估算',
  `billable_state` tinyint NOT NULL DEFAULT '0' COMMENT '收费白名单: 0平台免费 1扣会员配额',
  `cost_yuan` decimal(12,8) NOT NULL DEFAULT '0.00000000' COMMENT '该底层调用实际成本',
  `quota_period_key` varchar(32) DEFAULT NULL COMMENT '会员配额周期键',
  `related_id` varchar(64) DEFAULT NULL COMMENT '会话或业务关联ID',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '0否 1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_usage_user_feature_related` (`user_id`,`feature_code`,`related_id`),
  KEY `idx_ai_usage_log_user_time` (`user_id`,`create_time`),
  KEY `idx_ai_usage_log_feature` (`feature_code`,`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=30 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI调用积分明细';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `forum_ai_quota_period_usage` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `quota_period_key` varchar(32) NOT NULL COMMENT '方案额度周期键',
  `qwen_used_micros` bigint NOT NULL DEFAULT '0' COMMENT 'Qwen已结算成本(人民币微元)',
  `qwen_reserved_micros` bigint NOT NULL DEFAULT '0' COMMENT 'Qwen并发预占成本(人民币微元)',
  `wan_used_count` int NOT NULL DEFAULT '0' COMMENT 'Wan已结算张数',
  `wan_reserved_count` int NOT NULL DEFAULT '0' COMMENT 'Wan并发预占张数',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '0否 1是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_quota_period_user` (`user_id`,`quota_period_key`),
  KEY `idx_ai_quota_period_key` (`quota_period_key`,`delete_state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI周期额度汇总与并发预占';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `forum_companion_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '消息ID',
  `session_id` bigint NOT NULL COMMENT '会话ID',
  `role` varchar(16) NOT NULL COMMENT 'user|assistant',
  `content` text COMMENT '文本内容',
  `msg_type` varchar(16) NOT NULL DEFAULT 'text' COMMENT 'text|image',
  `image_url` varchar(1024) DEFAULT NULL COMMENT '生图URL(OSS)；text消息时可存联网检索配图',
  `metadata_json` mediumtext COMMENT '消息扩展元数据（联网图集、上下文摘要来源等）',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '0否 1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_companion_msg_session` (`session_id`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=62 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='陪伴助手消息';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `forum_companion_session` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '会话ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `skill` varchar(32) NOT NULL COMMENT 'writing|help|drawing|reading',
  `title` varchar(120) DEFAULT NULL COMMENT '会话标题(首条用户消息摘要)',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '0否 1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后活跃',
  PRIMARY KEY (`id`),
  KEY `idx_companion_sess_user_skill` (`user_id`,`skill`,`update_time`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='陪伴助手会话';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `forum_mascot_intent` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `intent_kind` varchar(16) NOT NULL COMMENT 'seek=想找人 offer=能帮人',
  `intent_text` varchar(200) NOT NULL COMMENT '用户确认过的那句意愿描述',
  `source_session_id` bigint DEFAULT NULL COMMENT '来自哪个看板娘会话，用于「同一会话不重复问」',
  `state` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE|MATCHED|EXPIRED|CANCELLED',
  `expire_at` datetime NOT NULL COMMENT '过期时间；到期自动作废，避免拿着半年前的需求去牵线',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `delete_state` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0=正常 1=已删除',
  PRIMARY KEY (`id`),
  KEY `idx_mascot_intent_user` (`user_id`,`state`,`delete_state`),
  KEY `idx_mascot_intent_session` (`source_session_id`,`delete_state`),
  KEY `idx_mascot_intent_pool` (`state`,`expire_at`,`delete_state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='看板娘牵线意愿池';
CREATE TABLE `forum_mascot_intent_match` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `intent_a_id` bigint NOT NULL COMMENT '意愿A',
  `intent_b_id` bigint NOT NULL COMMENT '意愿B',
  `user_a_id` bigint NOT NULL COMMENT '意愿A的主人',
  `user_b_id` bigint NOT NULL COMMENT '意愿B的主人',
  `reason` varchar(200) NOT NULL COMMENT '交集描述；双方看到的是同一句，不含身份信息',
  `a_state` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING|ACCEPTED|DECLINED',
  `b_state` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING|ACCEPTED|DECLINED',
  `state` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING|CONNECTED|CLOSED',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `delete_state` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0=正常 1=已删除',
  PRIMARY KEY (`id`),
  -- 同一对意愿只配一次，避免定时任务重复推
  UNIQUE KEY `uk_mascot_match_pair` (`intent_a_id`,`intent_b_id`),
  KEY `idx_mascot_match_user_a` (`user_a_id`,`state`,`delete_state`),
  KEY `idx_mascot_match_user_b` (`user_b_id`,`state`,`delete_state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='看板娘牵线匹配';
CREATE TABLE `forum_mascot_memory` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `summary` varchar(500) NOT NULL DEFAULT '' COMMENT '长期记忆摘要',
  `facts_json` text COMMENT '短事实 JSON 数组',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '0否 1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mascot_memory_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='看板娘长期记忆';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `forum_mascot_model` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(64) NOT NULL COMMENT '看板娘唯一标识',
  `name` varchar(128) NOT NULL COMMENT '展示名称',
  `model_rel_path` varchar(512) NOT NULL COMMENT '前端静态资源相对路径',
  `model_scale` decimal(8,4) NOT NULL DEFAULT '0.1000' COMMENT '模型缩放',
  `pos_x` int NOT NULL DEFAULT '0' COMMENT '模型位置 X',
  `pos_y` int NOT NULL DEFAULT '72' COMMENT '模型位置 Y',
  `stage_width` int NOT NULL DEFAULT '260' COMMENT '舞台宽度 px',
  `stage_height` int NOT NULL DEFAULT '320' COMMENT '舞台高度 px',
  `shelf_status` tinyint NOT NULL DEFAULT '1' COMMENT '0草稿 1上架 2下架（默认上架）',
  `sort_order` int NOT NULL DEFAULT '0',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '0正常 1软删',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mascot_code` (`code`),
  KEY `idx_mascot_shelf_del` (`shelf_status`,`delete_state`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='看板娘模型库';

-- 旧 Live2D 形象保留历史引用但下架软删，小萌为当前唯一上架形象
INSERT INTO `forum_mascot_model`
(`id`, `code`, `name`, `model_rel_path`, `model_scale`, `pos_x`, `pos_y`, `stage_width`, `stage_height`, `shelf_status`, `sort_order`, `delete_state`)
VALUES
(1, 'snow_miku', 'snow_miku', 'model/snow_miku/model.json', 0.1400, 0, 72, 340, 380, 2, 20, 1),
(2, 'xiaomai', 'xiaomai', 'model/xiaomai/xiaomai.model.json', 0.1400, 0, 72, 340, 380, 2, 30, 1),
(3, 'xiaomeng', '小萌', 'mascot-assets/xiaomeng/sprite-manifest.json', 0.5000, 0, 12, 96, 104, 1, 0, 0);
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `forum_mascot_related_recommendation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `companion_session_id` bigint NOT NULL,
  `source_message_id` bigint DEFAULT NULL,
  `query` varchar(500) NOT NULL,
  `result_state` varchar(16) NOT NULL,
  `result_count` int NOT NULL DEFAULT '0',
  `delete_state` tinyint NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_mascot_related_user_session_time` (`user_id`,`companion_session_id`,`delete_state`,`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='看板娘已确认相关帖子检索';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `forum_mascot_related_recommendation_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `recommendation_id` bigint NOT NULL,
  `article_id` bigint NOT NULL,
  `display_order` int NOT NULL,
  `selection_reason` varchar(16) NOT NULL,
  `delete_state` tinyint NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mascot_related_recommendation_article` (`recommendation_id`,`article_id`),
  KEY `idx_mascot_related_item_recommendation` (`recommendation_id`,`delete_state`,`display_order`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='看板娘相关帖子检索结果项';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_mascot_preference` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `mascot_model_id` bigint DEFAULT NULL COMMENT 'forum_mascot_model.id',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `delete_state` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0=正常 1=已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_mascot_preference_user_id` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户看板娘偏好（ai 权威）';
/*!40101 SET character_set_client = @saved_cs_client */;

-- ----------------------------
-- Demo seeds: AI model price catalog
-- ----------------------------
INSERT INTO `forum_ai_model_price` (`model_code`, `provider`, `bill_unit`, `price_yuan`, `vip_only`, `enabled`, `remark`) VALUES
('qwen3.6-flash', 'dashscope', 'per_1m_input', 1.200000, 0, 0, '中国内地'),
('qwen3.6-flash', 'dashscope', 'per_1m_output', 7.200000, 0, 0, '中国内地'),
('qwen3.7-flash', 'dashscope', 'per_1m_input', 0.200000, 0, 1, '中国内地<=32K'),
('qwen3.7-flash', 'dashscope', 'per_1m_output', 0.800000, 0, 1, '中国内地<=32K'),
('qwen3.7-max', 'dashscope', 'per_1m_input', 12.000000, 0, 1, '中国内地标准价'),
('qwen3.7-max', 'dashscope', 'per_1m_output', 36.000000, 0, 1, '中国内地标准价'),
('wan2.7-image', 'dashscope', 'per_image', 0.200000, 0, 1, '通义万相2.7生图'),
('qwen3-vl-flash', 'dashscope', 'per_1m_input', 0.150000, 0, 1, '视觉审核'),
('qwen3-vl-flash', 'dashscope', 'per_1m_output', 1.500000, 0, 1, '视觉审核'),
('qwen3-vl-plus', 'dashscope', 'per_1m_input', 1.000000, 0, 1, '视觉兜底'),
('qwen3-vl-plus', 'dashscope', 'per_1m_output', 10.000000, 0, 1, '视觉兜底'),
('tongyi-embedding-vision-flash', 'dashscope', 'per_1m_input', 0.150000, 0, 0, 'RAG向量'),
('z-image-turbo', 'dashscope', 'per_image', 0.100000, 0, 0, 'prompt_extend=false'),
('wanx2.1-t2i-plus', 'dashscope', 'per_image', 0.100000, 1, 0, '通义万相进阶生图(兜底)'),
('wan2.7-image-pro', 'dashscope', 'per_image', 0.500000, 1, 1, '万相2.7进阶生图，额度按两张扣'),
('tavily-search', 'tavily', 'per_call', 0.058000, 0, 1, 'Tavily 联网检索，按次计费');

