-- ai domain full schema
DROP DATABASE IF EXISTS `forum_ai_db`;

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `forum_ai_db` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `forum_ai_db`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_usage_daily` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `usage_date` date NOT NULL COMMENT '用量归属日',
  `qwen_flash_used` int NOT NULL DEFAULT '0' COMMENT 'DeepSeek写作已用次数(普通用户上限10)',
  `advanced_llm_used` int NOT NULL DEFAULT '0' COMMENT '高级大模型写作已用次数',
  `image_normal_used` int NOT NULL DEFAULT '0' COMMENT 'AI生图普通档已用次数',
  `image_premium_used` int NOT NULL DEFAULT '0' COMMENT 'AI生图高级档已用次数',
  `companion_normal_used` int NOT NULL DEFAULT '0' COMMENT 'AI伴读普通档已用(预留)',
  `companion_premium_used` int NOT NULL DEFAULT '0' COMMENT 'AI伴读高级档已用(预留)',
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
CREATE TABLE `drift_bottle` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'æ¼‚æµç“¶ID',
  `user_id` bigint NOT NULL COMMENT 'çœŸå®žä½œè€…ç”¨æˆ·ID',
  `content` varchar(500) NOT NULL COMMENT 'ç“¶å­å†…å®¹',
  `mood_type` varchar(20) NOT NULL COMMENT 'å¿ƒæƒ…æ ‡ç­¾',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT 'çŠ¶æ€: 0å¯è§ 1éšè— 2åˆ é™¤',
  `comment_count` int NOT NULL DEFAULT '0' COMMENT 'è¯„è®ºæ•°é‡',
  `picked_count` int NOT NULL DEFAULT '0' COMMENT 'è¢«æžæ¬¡æ•°',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'åˆ›å»ºæ—¶é—´',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'æ›´æ–°æ—¶é—´',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT 'é€»è¾‘åˆ é™¤: 0å¦ 1æ˜¯',
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`,`create_time`),
  KEY `idx_visible_time` (`status`,`delete_state`,`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='æ¼‚æµç“¶ä¸»è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `drift_bottle_comment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'è¯„è®ºID',
  `bottle_id` bigint NOT NULL COMMENT 'æ¼‚æµç“¶ID',
  `user_id` bigint NOT NULL COMMENT 'çœŸå®žè¯„è®ºç”¨æˆ·ID',
  `content` varchar(200) NOT NULL COMMENT 'è¯„è®ºå†…å®¹',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT 'çŠ¶æ€: 0å¯è§ 1éšè— 2åˆ é™¤',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'åˆ›å»ºæ—¶é—´',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'æ›´æ–°æ—¶é—´',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT 'é€»è¾‘åˆ é™¤: 0å¦ 1æ˜¯',
  PRIMARY KEY (`id`),
  KEY `idx_bottle_time` (`bottle_id`,`status`,`delete_state`,`create_time`),
  KEY `idx_user_time` (`user_id`,`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='æ¼‚æµç“¶è¯„è®ºè¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `drift_bottle_pick_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'æ‰“æžè®°å½•ID',
  `bottle_id` bigint NOT NULL COMMENT 'æ¼‚æµç“¶ID',
  `user_id` bigint NOT NULL COMMENT 'æ‰“æžç”¨æˆ·ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'åˆ›å»ºæ—¶é—´',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'æ›´æ–°æ—¶é—´',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT 'é€»è¾‘åˆ é™¤: 0å¦ 1æ˜¯',
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`,`create_time`),
  KEY `idx_user_bottle` (`user_id`,`bottle_id`)
) ENGINE=InnoDB AUTO_INCREMENT=37 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='æ¼‚æµç“¶æ‰“æžè®°å½•è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `drift_bottle_report` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ä¸¾æŠ¥ID',
  `target_type` tinyint NOT NULL COMMENT 'ç›®æ ‡ç±»åž‹: 0ç“¶å­ 1è¯„è®º',
  `target_id` bigint NOT NULL COMMENT 'ç›®æ ‡ID',
  `report_user_id` bigint NOT NULL COMMENT 'ä¸¾æŠ¥ç”¨æˆ·ID',
  `reason_type` varchar(30) NOT NULL COMMENT 'ä¸¾æŠ¥åŽŸå› ç±»åž‹',
  `reason_detail` varchar(200) DEFAULT NULL COMMENT 'ä¸¾æŠ¥è¡¥å……è¯´æ˜Ž',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT 'å¤„ç†çŠ¶æ€: 0å¾…å¤„ç† 1å·²å¤„ç† 2å·²é©³å›ž',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'åˆ›å»ºæ—¶é—´',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'æ›´æ–°æ—¶é—´',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT 'é€»è¾‘åˆ é™¤: 0å¦ 1æ˜¯',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_report_once` (`target_type`,`target_id`,`report_user_id`,`delete_state`),
  KEY `idx_target_status` (`target_type`,`target_id`,`status`,`delete_state`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='æ¼‚æµç“¶ä¸¾æŠ¥è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `forum_ai_call_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ä¸»é”®',
  `user_id` bigint NOT NULL COMMENT 'ç”¨æˆ·ID',
  `feature_code` varchar(64) NOT NULL COMMENT 'åŠŸèƒ½ç¼–ç ',
  `client_request_id` varchar(64) NOT NULL COMMENT 'å®¢æˆ·ç«¯å¹‚ç­‰é”®',
  `model_code` varchar(64) DEFAULT NULL COMMENT 'è®¡åˆ’è°ƒç”¨æ¨¡åž‹',
  `call_state` tinyint NOT NULL DEFAULT '0' COMMENT '0å¾…è°ƒç”¨ 1æˆåŠŸ 2å¤±è´¥ 3è¶…æ—¶ 4åœæ­¢ 5æ–­å¼€',
  `estimated_points` int NOT NULL DEFAULT '0' COMMENT 'é¢„ä¼°ç§¯åˆ†',
  `points_charged` int NOT NULL DEFAULT '0' COMMENT 'å®žé™…æ‰£é™¤ç§¯åˆ†',
  `input_tokens` int NOT NULL DEFAULT '0' COMMENT 'è¾“å…¥token',
  `output_tokens` int NOT NULL DEFAULT '0' COMMENT 'è¾“å‡ºtoken',
  `error_summary` varchar(200) DEFAULT NULL COMMENT 'å¤±è´¥æ‘˜è¦(è„±æ•)',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '0å¦ 1æ˜¯',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'åˆ›å»ºæ—¶é—´',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'æ›´æ–°æ—¶é—´',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_call_user_feature_request` (`user_id`,`feature_code`,`client_request_id`),
  KEY `idx_ai_call_user_time` (`user_id`,`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AIè°ƒç”¨é¢„è®°å½•è¡¨';
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
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AIåˆ›ä½œäº§ç‰©ç‰ˆæœ¬';
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
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AIåˆ›ä½œå·¥ä½œåŒº';
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
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AIä¼šå‘˜é•¿æœŸè®°å¿†';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `forum_ai_model_price` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `model_code` varchar(64) NOT NULL COMMENT '模型标识,与埋点一致',
  `provider` varchar(32) NOT NULL DEFAULT 'dashscope' COMMENT 'dashscope|deepseek|huanapi',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AIä»»åŠ¡ä¼šè¯çŠ¶æ€';
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
CREATE TABLE `forum_companion_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '消息ID',
  `session_id` bigint NOT NULL COMMENT '会话ID',
  `role` varchar(16) NOT NULL COMMENT 'user|assistant',
  `content` text COMMENT '文本内容',
  `msg_type` varchar(16) NOT NULL DEFAULT 'text' COMMENT 'text|image',
  `image_url` varchar(1024) DEFAULT NULL COMMENT '生图URL(OSS)；text消息时可存联网检索配图',
  `metadata_json` mediumtext COMMENT 'æ¶ˆæ¯æ‰©å±•å…ƒæ•°æ®ï¼ˆè”ç½‘å›¾é›†ã€ä¸Šä¸‹æ–‡æ‘˜è¦æ¥æºç­‰ï¼‰',
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
CREATE TABLE `forum_mascot_model` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(64) NOT NULL COMMENT '唯一标识，对应 oh-my-live2d model.name',
  `name` varchar(128) NOT NULL COMMENT '展示名称',
  `model_rel_path` varchar(512) NOT NULL COMMENT '相对仓库 live2d/live2d-master 的路径，如 live2d_3/model/.../x.model3.json',
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
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='看板娘模型库';
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
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='çœ‹æ¿å¨˜å·²ç¡®è®¤ç›¸å…³å¸–å­æ£€ç´¢';
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
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='çœ‹æ¿å¨˜ç›¸å…³å¸–å­æ£€ç´¢ç»“æžœé¡¹';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_mascot_preference` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ä¸»é”®',
  `user_id` bigint NOT NULL COMMENT 'ç”¨æˆ·ID',
  `mascot_model_id` bigint DEFAULT NULL COMMENT 'forum_mascot_model.id',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'åˆ›å»ºæ—¶é—´',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'æ›´æ–°æ—¶é—´',
  `delete_state` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'é€»è¾‘åˆ é™¤ï¼š0=æ­£å¸¸ 1=å·²åˆ é™¤',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_mascot_preference_user_id` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ç”¨æˆ·çœ‹æ¿å¨˜åå¥½ï¼ˆai æƒå¨ï¼‰';
/*!40101 SET character_set_client = @saved_cs_client */;
