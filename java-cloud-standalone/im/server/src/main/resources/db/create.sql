-- im domain full schema
DROP DATABASE IF EXISTS `forum_im_db`;

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `forum_im_db` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `forum_im_db`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `forum_notice` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `notice_kind` tinyint NOT NULL COMMENT '0新用户入站 1活动 2纪律 3系统更新 4版规',
  `category_scope` bigint NOT NULL DEFAULT '0' COMMENT '版规时: 0全站通用; 非0=category.id。其它公告类型填0',
  `template_id` varchar(64) NOT NULL COMMENT '前端模板标识,如 welcome_hero_right / plain_sections',
  `sidebar_key` varchar(64) NOT NULL COMMENT '公告中心侧栏 slug,用于定位本篇',
  `title` varchar(200) NOT NULL COMMENT '主标题',
  `subtitle` varchar(500) DEFAULT NULL COMMENT '副标题/摘要',
  `content_markdown` text NOT NULL COMMENT '正文 Markdown，用户端主要阅读区',
  `body_json` json NOT NULL COMMENT '模板扩展(JSON): highlights、coverImageUrl 等',
  `sort` int NOT NULL DEFAULT '0' COMMENT '兼容占位，默认0',
  `pin_top` tinyint NOT NULL DEFAULT '0' COMMENT '1置顶：同类型同分类范围下排最前',
  `publish_state` tinyint NOT NULL DEFAULT '0' COMMENT '0草稿 1已发布',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '0否 1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notice_kind_scope_sidebar` (`notice_kind`,`category_scope`,`sidebar_key`),
  KEY `idx_notice_list` (`notice_kind`,`category_scope`,`publish_state`,`delete_state`,`pin_top`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='论坛公告中心(模板化内容)';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `forum_outbox_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ä¸»é”®',
  `event_id` varchar(128) NOT NULL COMMENT 'ä¸šåŠ¡äº‹ä»¶ID',
  `routing_key` varchar(64) NOT NULL COMMENT 'RabbitMQ routing key',
  `payload_json` mediumtext NOT NULL COMMENT 'æ¶ˆæ¯ä½“ JSON',
  `message_state` tinyint NOT NULL DEFAULT '0' COMMENT '0å¾…æŠ•é€’ 1å·²æŠ•é€’ 2å·²æ¶ˆè´¹ 3å¤±è´¥ 4æ­»ä¿¡',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT 'é‡è¯•æ¬¡æ•°',
  `last_error` varchar(200) DEFAULT NULL COMMENT 'æœ€è¿‘é”™è¯¯æ‘˜è¦',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '0å¦ 1æ˜¯',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'åˆ›å»ºæ—¶é—´',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'æ›´æ–°æ—¶é—´',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_outbox_event_id` (`event_id`),
  KEY `idx_outbox_state_time` (`message_state`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='MQæœ¬åœ°æ¶ˆæ¯è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `group_chat` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ç¾¤èŠID',
  `owner_user_id` bigint NOT NULL COMMENT 'ç¾¤ä¸»ç”¨æˆ·ID',
  `name` varchar(24) NOT NULL COMMENT 'ç¾¤åç§°',
  `avatar_url` varchar(512) DEFAULT NULL COMMENT 'ç¾¤å¤´åƒURL',
  `intro` varchar(120) DEFAULT NULL COMMENT 'ç¾¤ç®€ä»‹',
  `group_type` tinyint NOT NULL DEFAULT '0' COMMENT 'ç¾¤ç±»åž‹: 0å…¬å¼€ 1ç§æœ‰',
  `member_limit` int NOT NULL DEFAULT '100' COMMENT 'å½“å‰èº«ä»½å¯¹åº”äººæ•°ä¸Šé™å¿«ç…§',
  `member_count` int NOT NULL DEFAULT '0' COMMENT 'å½“å‰æˆå‘˜æ•°',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT 'ç¾¤çŠ¶æ€: 0æ­£å¸¸ 1æ»¡å‘˜ 2è¶…é¢é”å®š 3å·²è§£æ•£ 4è¿è§„å°ç¦',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'åˆ›å»ºæ—¶é—´',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'æ›´æ–°æ—¶é—´',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT 'æ˜¯å¦åˆ é™¤: 0å¦ 1æ˜¯',
  PRIMARY KEY (`id`),
  KEY `idx_group_owner_status` (`owner_user_id`,`status`,`delete_state`),
  KEY `idx_group_public` (`group_type`,`status`,`delete_state`,`update_time`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ç¾¤èŠä¸»è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `group_chat_join_request` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '鐢宠?ID',
  `group_id` bigint NOT NULL COMMENT '缇よ亰ID',
  `target_user_id` bigint NOT NULL COMMENT '鐩?爣鐢ㄦ埛ID',
  `initiator_user_id` bigint NOT NULL COMMENT '鍙戣捣浜虹敤鎴稩D',
  `owner_user_id` bigint NOT NULL COMMENT '缇や富鐢ㄦ埛ID',
  `request_type` tinyint NOT NULL COMMENT '璇锋眰绫诲瀷: 0鐢宠?鍔犵兢 1閭??鍏ョ兢',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '澶勭悊鐘舵?: 0寰呭?鐞?1宸插悓鎰?2宸叉嫆缁',
  `owner_read_state` tinyint NOT NULL DEFAULT '0' COMMENT '缇や富鏌ョ湅鐘舵?: 0鏈?? 1宸茶?',
  `handled_by_user_id` bigint DEFAULT NULL COMMENT '澶勭悊浜虹敤鎴稩D',
  `handle_time` datetime DEFAULT NULL COMMENT '澶勭悊鏃堕棿',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '鏄?惁鍒犻櫎: 0鍚?1鏄',
  PRIMARY KEY (`id`),
  KEY `idx_group_status` (`group_id`,`status`,`delete_state`,`create_time`),
  KEY `idx_target_status` (`target_user_id`,`status`,`delete_state`,`create_time`),
  KEY `idx_owner_type_status` (`owner_user_id`,`request_type`,`status`,`delete_state`,`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='缇ゅ姞鍏ョ敵璇蜂笌閭??琛';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `group_chat_member` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'æˆå‘˜è®°å½•ID',
  `group_id` bigint NOT NULL COMMENT 'ç¾¤èŠID',
  `user_id` bigint NOT NULL COMMENT 'ç”¨æˆ·ID',
  `role` tinyint NOT NULL DEFAULT '1' COMMENT '瑙掕壊: 0缇や富 1鎴愬憳 2绠＄悊鍛',
  `remark_name` varchar(24) DEFAULT NULL COMMENT '群内备注昵称',
  `notify_mode` tinyint NOT NULL DEFAULT '0' COMMENT 'æé†’æ¨¡å¼: 0æ­£å¸¸ 1ä»…@æé†’ 2å®Œå…¨ä¸æé†’',
  `mute_until` datetime DEFAULT NULL COMMENT 'ç¦è¨€æˆªæ­¢æ—¶é—´',
  `last_read_message_id` bigint NOT NULL DEFAULT '0' COMMENT 'æœ€åŽå·²è¯»ç¾¤æ¶ˆæ¯ID',
  `join_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'åŠ å…¥æ—¶é—´',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT 'çŠ¶æ€: 0æ­£å¸¸ 1å·²é€€å‡º 2è¢«ç§»é™¤',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'åˆ›å»ºæ—¶é—´',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'æ›´æ–°æ—¶é—´',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT 'æ˜¯å¦åˆ é™¤: 0å¦ 1æ˜¯',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_member` (`group_id`,`user_id`,`delete_state`),
  KEY `idx_member_user_status` (`user_id`,`status`,`delete_state`),
  KEY `idx_member_group_status` (`group_id`,`status`,`delete_state`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ç¾¤èŠæˆå‘˜è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `group_chat_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ç¾¤æ¶ˆæ¯ID',
  `group_id` bigint NOT NULL COMMENT 'ç¾¤èŠID',
  `sender_user_id` bigint DEFAULT NULL COMMENT 'å‘é€è€…ç”¨æˆ·IDï¼Œç³»ç»Ÿæ¶ˆæ¯ä¸ºç©º',
  `message_type` tinyint NOT NULL DEFAULT '0' COMMENT '消息类型: 0文本 1表情 2图片 9系统',
  `content` varchar(500) NOT NULL COMMENT 'æ¶ˆæ¯å†…å®¹',
  `reply_message_id` bigint DEFAULT NULL COMMENT '回复的群消息ID',
  `reply_sender_name` varchar(64) DEFAULT NULL COMMENT '被回复消息发送者昵称快照',
  `reply_content` varchar(200) DEFAULT NULL COMMENT '被回复消息内容快照',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT 'çŠ¶æ€: 0æ­£å¸¸ 1ä¸¾æŠ¥éšè— 2åˆ é™¤',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'åˆ›å»ºæ—¶é—´',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'æ›´æ–°æ—¶é—´',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT 'æ˜¯å¦åˆ é™¤: 0å¦ 1æ˜¯',
  PRIMARY KEY (`id`),
  KEY `idx_group_message` (`group_id`,`delete_state`,`id`),
  KEY `idx_sender_time` (`sender_user_id`,`delete_state`,`create_time`),
  KEY `idx_group_reply_message` (`reply_message_id`)
) ENGINE=InnoDB AUTO_INCREMENT=40 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ç¾¤èŠæ¶ˆæ¯è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `group_chat_report` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ä¸¾æŠ¥è®°å½•ID',
  `group_id` bigint NOT NULL COMMENT 'ç¾¤èŠID',
  `message_id` bigint NOT NULL COMMENT 'ç¾¤æ¶ˆæ¯ID',
  `reporter_user_id` bigint NOT NULL COMMENT 'ä¸¾æŠ¥äººç”¨æˆ·ID',
  `target_user_id` bigint NOT NULL COMMENT 'è¢«ä¸¾æŠ¥ç”¨æˆ·ID',
  `reason` varchar(200) NOT NULL COMMENT 'ä¸¾æŠ¥åŽŸå› ',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT 'å¤„ç†çŠ¶æ€: 0å¾…å¤„ç† 1å·²å¤„ç† 2é©³å›ž',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'åˆ›å»ºæ—¶é—´',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'æ›´æ–°æ—¶é—´',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT 'æ˜¯å¦åˆ é™¤: 0å¦ 1æ˜¯',
  PRIMARY KEY (`id`),
  KEY `idx_group_report` (`group_id`,`status`,`delete_state`,`create_time`),
  KEY `idx_message_report` (`message_id`,`delete_state`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ç¾¤èŠä¸¾æŠ¥è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号, 主键, 自增',
  `post_user_id` bigint NOT NULL COMMENT '发送者编号',
  `receive_user_id` bigint NOT NULL COMMENT '接收者编号',
  `message_type` tinyint NOT NULL DEFAULT '0' COMMENT '消息类型: 0文本 1图片 2GIF',
  `content` varchar(500) DEFAULT NULL COMMENT '文本内容; 图片/GIF 消息为空',
  `media_url` varchar(500) DEFAULT NULL COMMENT '媒体URL(OSS), 图片/GIF 消息必填',
  `media_mime` varchar(50) DEFAULT NULL COMMENT '媒体MIME(image/jpeg/png/gif)',
  `media_size` bigint DEFAULT NULL COMMENT '媒体字节大小',
  `media_width` int DEFAULT NULL COMMENT '媒体像素宽',
  `media_height` int DEFAULT NULL COMMENT '媒体像素高',
  `state` tinyint NOT NULL DEFAULT '0' COMMENT '状态: 0未读, 1已读, 2已撤回',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除: 0否 1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_session_pair` (`post_user_id`,`receive_user_id`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=26 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='站内信表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号, 主键, 自增',
  `receive_user_id` bigint NOT NULL COMMENT '接收者用户ID',
  `type` tinyint NOT NULL COMMENT '系统消息类型: 1审核通过 2审核未通过 3审核异常 4注册入站引导 99公告(预留)',
  `title` varchar(100) NOT NULL COMMENT '消息标题(展示用)',
  `content` varchar(500) NOT NULL COMMENT '消息正文(展示用)',
  `related_id` bigint DEFAULT NULL COMMENT '关联业务ID(如审核类: articleId)',
  `payload` varchar(1000) DEFAULT NULL COMMENT '附加结构化数据(JSON字符串)',
  `state` tinyint NOT NULL DEFAULT '0' COMMENT '状态: 0未读 1已读',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除: 0否 1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_state` (`receive_user_id`,`state`,`id`),
  KEY `idx_user_time` (`receive_user_id`,`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统消息表(审核结果/公告等)';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_chat_emoji` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号, 主键, 自增',
  `user_id` bigint NOT NULL COMMENT '所属用户编号',
  `media_url` varchar(500) NOT NULL COMMENT '表情图URL(OSS)',
  `media_type` tinyint NOT NULL DEFAULT '0' COMMENT '类型: 0静态图 1GIF',
  `media_mime` varchar(50) DEFAULT NULL COMMENT 'MIME类型',
  `media_size` bigint DEFAULT NULL COMMENT '字节大小',
  `origin_message_id` bigint DEFAULT NULL COMMENT '来源消息ID(收藏自他人聊天图片时)',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除: 0否 1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uix_user_url` (`user_id`,`media_url`),
  KEY `idx_user_create` (`user_id`,`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=20 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户聊天表情收藏表';
/*!40101 SET character_set_client = @saved_cs_client */;
