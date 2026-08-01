-- content domain full schema
DROP DATABASE IF EXISTS `forum_content_db`;

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `forum_content_db` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `forum_content_db`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `article` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '帖子编号, 主键, 自增',
  `board_id` bigint NOT NULL COMMENT '关联板块编号',
  `user_id` bigint NOT NULL COMMENT '发帖人编号',
  `title` varchar(100) NOT NULL COMMENT '标题',
  `content` text NOT NULL COMMENT '帖子正文',
  `visit_count` int NOT NULL DEFAULT '0' COMMENT '访问量',
  `reply_count` int NOT NULL DEFAULT '0' COMMENT '回复数',
  `like_count` int NOT NULL DEFAULT '0' COMMENT '点赞数',
  `cover_img` varchar(255) DEFAULT NULL COMMENT '封面图URL',
  `media_type` tinyint NOT NULL DEFAULT '0' COMMENT '帖子媒体类型: 0图片相册 1视频(单个)',
  `video_url` varchar(500) DEFAULT NULL COMMENT '视频URL(仅 media_type=1 时有效, OSS: forum_vedio/article_vedio/)',
  `content_type` tinyint NOT NULL DEFAULT '0' COMMENT '内容类型: 0富文本 1Markdown',
  `article_type` tinyint NOT NULL DEFAULT '0' COMMENT '帖子业务类型: 0普通帖 1问答帖',
  `question_status` tinyint DEFAULT NULL COMMENT '问答状态: 0待解决 1已解决 2已关闭; 普通帖为空',
  `accepted_reply_id` bigint DEFAULT NULL COMMENT '最佳答案对应的一级回答ID',
  `favorite_count` int NOT NULL DEFAULT '0' COMMENT '收藏数(被加入收藏夹的总次数, 跨用户去重为 1)',
  `sub_reply_count` int NOT NULL DEFAULT '0' COMMENT '楼中楼回复数(独立于 reply_count, 楼层数仍只算一级回复)',
  `state` tinyint NOT NULL DEFAULT '0' COMMENT '审核状态: 0正常, 1禁用',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '发布状态: 0草稿 1审核中 2审核通过(瞬态,自动转5) 3审核未通过 4审核异常 5已发布',
  `audit_task_id` varchar(64) DEFAULT NULL COMMENT '当前审核任务ID(UUID); 与 Python LangGraph thread_id 关联',
  `audit_notify_email` tinyint NOT NULL DEFAULT '0' COMMENT '审核结果是否额外推邮件: 0否 1是; 站内信无论如何都发',
  `audit_retry_count` tinyint NOT NULL DEFAULT '0' COMMENT '当前累计提交审核次数(0~3); 达到上限提示联系管理员',
  `audit_result_message` varchar(500) DEFAULT NULL COMMENT '最近一次审核结论文本(通过原因/拒绝理由)',
  `audit_submitted_at` datetime DEFAULT NULL COMMENT '最近一次审核提交时间',
  `audit_finished_at` datetime DEFAULT NULL COMMENT '最近一次审核结束时间',
  `ip_region` varchar(32) DEFAULT NULL COMMENT '发帖时IP属地快照',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除: 0否 1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_audit_pending` (`status`,`audit_submitted_at`),
  KEY `idx_article_question_filter` (`article_type`,`question_status`,`status`,`delete_state`,`update_time`)
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='帖子表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `article_favorite` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号, 主键, 自增',
  `user_id` bigint NOT NULL COMMENT '收藏用户编号',
  `folder_id` bigint NOT NULL COMMENT '所属收藏夹ID',
  `article_id` bigint NOT NULL COMMENT '被收藏帖子编号',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除: 0否 1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uix_user_article_fav` (`user_id`,`article_id`),
  KEY `idx_folder_id` (`folder_id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='帖子收藏记录表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `article_image` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `article_id` bigint NOT NULL COMMENT '所属帖子ID',
  `image_url` varchar(500) NOT NULL COMMENT '相册图完整URL(前缀须为 OSS 帖子图目录, 见 Constant.OSS_PATH_ARTICLE_IMAGE)',
  `sort` int NOT NULL DEFAULT '0' COMMENT '相册内排序, 0 在最前',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除: 0否 1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_article_id` (`article_id`),
  KEY `idx_article_image_article_del` (`article_id`,`delete_state`)
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='帖子相册图片表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `article_like` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号, 主键, 自增',
  `user_id` bigint NOT NULL COMMENT '点赞用户编号',
  `article_id` bigint NOT NULL COMMENT '被点赞帖子编号',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uix_user_article` (`user_id`,`article_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='帖子点赞记录表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `article_reply` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号, 主键, 自增',
  `article_id` bigint NOT NULL COMMENT '关联帖子编号',
  `post_user_id` bigint NOT NULL COMMENT '回帖用户编号',
  `reply_id` bigint DEFAULT NULL COMMENT '关联回复编号, 支持楼中楼',
  `reply_user_id` bigint DEFAULT NULL COMMENT '被回复用户编号',
  `content` varchar(500) NOT NULL COMMENT '回帖内容',
  `ip_region` varchar(32) DEFAULT NULL COMMENT '评论时IP属地快照',
  `like_count` int NOT NULL DEFAULT '0' COMMENT '点赞数',
  `state` tinyint NOT NULL DEFAULT '0' COMMENT '状态: 0正常, 1禁用',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除: 0否 1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=23 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='帖子回复表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `article_reply_like` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '点赞用户',
  `reply_id` bigint NOT NULL COMMENT '一级评论ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uix_user_reply` (`user_id`,`reply_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='一级评论点赞记录';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `article_reply_media` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `reply_id` bigint DEFAULT NULL COMMENT '一级评论ID',
  `sub_reply_id` bigint DEFAULT NULL COMMENT '楼中楼评论ID',
  `media_type` tinyint NOT NULL COMMENT '1用户图片 2商城表情',
  `media_url` varchar(500) NOT NULL COMMENT '媒体URL',
  `shop_id` bigint DEFAULT NULL COMMENT '商城表情包ID(仅 type=2)',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_reply_id` (`reply_id`),
  KEY `idx_sub_reply_id` (`sub_reply_id`)
) ENGINE=InnoDB AUTO_INCREMENT=41 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='评论图片/表情附件';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `article_sub_reply` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `article_id` bigint NOT NULL COMMENT '所属帖子ID',
  `reply_id` bigint NOT NULL COMMENT '所属的一级回复(楼层)ID',
  `post_user_id` bigint NOT NULL COMMENT '当前发帖的用户ID',
  `reply_user_id` bigint DEFAULT NULL COMMENT '被回复的目标用户ID (用于显示 @昵称)',
  `content` text NOT NULL COMMENT '回复内容',
  `ip_region` varchar(32) DEFAULT NULL COMMENT '楼中楼回复时IP属地快照',
  `like_count` int NOT NULL DEFAULT '0' COMMENT '点赞数',
  `state` tinyint DEFAULT '0' COMMENT '状态: 0-正常, 1-禁用',
  `delete_state` tinyint DEFAULT '0' COMMENT '是否删除: 0-否, 1-是',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_reply_id` (`reply_id`),
  KEY `idx_article_id` (`article_id`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='楼中楼回复表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `article_sub_reply_like` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '点赞用户',
  `sub_reply_id` bigint NOT NULL COMMENT '楼中楼回复ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uix_user_sub_reply` (`user_id`,`sub_reply_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='楼中楼回复点赞记录';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `article_video_danmaku` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `article_id` bigint NOT NULL COMMENT '帖子ID，仅视频帖',
  `user_id` bigint NOT NULL COMMENT '发送用户ID',
  `video_time_ms` int NOT NULL COMMENT '弹幕对应视频时间点(毫秒)',
  `content` varchar(100) NOT NULL COMMENT '弹幕文本',
  `color_code` tinyint NOT NULL DEFAULT '0' COMMENT '预设颜色编码: 0白 1红 2黄 3绿 4蓝 5粉',
  `mode` tinyint NOT NULL DEFAULT '0' COMMENT '弹幕模式: 0滚动 1顶部 2底部',
  `font_size` tinyint NOT NULL DEFAULT '1' COMMENT '字号: 0小 1标准',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0正常 1已删除',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_article_time` (`article_id`,`video_time_ms`,`id`),
  KEY `idx_user_article_time` (`user_id`,`article_id`,`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='视频帖弹幕';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `board` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '版块编号, 主键, 自增',
  `name` varchar(50) NOT NULL COMMENT '版块名',
  `article_count` int NOT NULL DEFAULT '0' COMMENT '帖子数量',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序优先级, 升序',
  `category_id` bigint DEFAULT NULL COMMENT '所属分类ID',
  `state` tinyint NOT NULL DEFAULT '0' COMMENT '状态: 0正常, 1禁用',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除: 0否 1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=28 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='版块表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `category` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '分类编号, 主键, 自增',
  `name` varchar(50) NOT NULL COMMENT '分类名称',
  `description` varchar(200) DEFAULT NULL COMMENT '分类描述',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序优先级',
  `state` tinyint NOT NULL DEFAULT '0' COMMENT '状态: 0正常, 1禁用',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除: 0否 1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='分类表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `forum_article_ai_feature` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `article_id` bigint NOT NULL,
  `feature_json` mediumtext NOT NULL,
  `feature_version` varchar(32) NOT NULL,
  `content_hash` varchar(64) NOT NULL,
  `generated_by` varchar(32) NOT NULL,
  `delete_state` tinyint NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_article_ai_feature_article` (`article_id`),
  KEY `idx_article_ai_feature_state_time` (`delete_state`,`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='å¸–å­æŽ¨èAIç‰¹å¾';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `forum_article_tag` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '标签ID',
  `name` varchar(16) NOT NULL COMMENT '标签名',
  `color_key` varchar(24) NOT NULL DEFAULT 'sky' COMMENT 'sky|rose|amber|mint|violet|slate|orange|teal',
  `scope_type` tinyint NOT NULL DEFAULT '0' COMMENT '0全站 1分类 2版块',
  `scope_id` bigint NOT NULL DEFAULT '0' COMMENT '分类或版块ID',
  `sort` int NOT NULL DEFAULT '0',
  `state` tinyint NOT NULL DEFAULT '0' COMMENT '0正常 1禁用',
  `delete_state` tinyint NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tag_scope_name` (`scope_type`,`scope_id`,`name`),
  KEY `idx_tag_scope` (`scope_type`,`scope_id`,`delete_state`,`state`)
) ENGINE=InnoDB AUTO_INCREMENT=29 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='帖子预设标签';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `forum_article_tag_link` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `article_id` bigint NOT NULL,
  `tag_id` bigint NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_article_tag` (`article_id`,`tag_id`),
  KEY `idx_tag_article` (`tag_id`,`article_id`)
) ENGINE=InnoDB AUTO_INCREMENT=33 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='帖子标签关联';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `forum_article_tag_request` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `board_id` bigint NOT NULL,
  `category_id` bigint NOT NULL DEFAULT '0',
  `proposed_name` varchar(16) NOT NULL,
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0待审 1通过 2拒绝',
  `audit_message` varchar(200) DEFAULT NULL,
  `approved_tag_id` bigint DEFAULT NULL,
  `delete_state` tinyint NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tag_req_user` (`user_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户申请新标签';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `forum_user_ai_profile_snapshot` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `profile_version` bigint NOT NULL DEFAULT '0',
  `profile_json` mediumtext NOT NULL,
  `feature_version` varchar(32) NOT NULL,
  `source_window_start` datetime DEFAULT NULL,
  `source_window_end` datetime DEFAULT NULL,
  `refresh_after` datetime NOT NULL,
  `generated_by` varchar(32) NOT NULL,
  `delete_state` tinyint NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_ai_profile_snapshot_user` (`user_id`),
  KEY `idx_user_ai_profile_snapshot_refresh` (`delete_state`,`refresh_after`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ç”¨æˆ·æŽ¨èAIç”»åƒå¿«ç…§';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_favorite_folder` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号, 主键, 自增',
  `user_id` bigint NOT NULL COMMENT '所属用户编号',
  `name` varchar(50) NOT NULL COMMENT '收藏夹名称',
  `is_public` tinyint NOT NULL DEFAULT '1' COMMENT '公开性: 0私密 1公开',
  `is_default` tinyint NOT NULL DEFAULT '0' COMMENT '是否默认夹: 0否 1是; 默认夹不可删',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '夹排序(升序), 默认 0',
  `item_count` int NOT NULL DEFAULT '0' COMMENT '夹内帖子数快照, 收藏/取消时维护',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除: 0否 1是',
  `default_marker` bigint GENERATED ALWAYS AS (if(((`is_default` = 1) and (`delete_state` = 0)),`user_id`,NULL)) VIRTUAL COMMENT '生成列: 仅在默认夹且未删时等于 user_id, 否则 NULL; 由 uix_user_default 锁住每用户最多一个默认夹',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uix_user_default` (`default_marker`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_user_public` (`user_id`,`is_public`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户收藏夹表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_interest_preference` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ä¸»é”®',
  `user_id` bigint NOT NULL COMMENT 'ç”¨æˆ·ID',
  `board_id` bigint NOT NULL COMMENT 'ç»†åˆ†æ¿å—IDï¼›0è¡¨ç¤ºå½“å‰ç”¨æˆ·çš„ä¸ªæ€§åŒ–å¼€å…³è®°å½•',
  `personalized_enabled` tinyint NOT NULL DEFAULT '1' COMMENT 'ä¸ªæ€§åŒ–å¼€å…³ï¼š0å…³é—­ 1å¼€å¯',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT 'é€»è¾‘åˆ é™¤ï¼š0å¦ 1æ˜¯',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'åˆ›å»ºæ—¶é—´',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'æ›´æ–°æ—¶é—´',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_interest_board` (`user_id`,`board_id`),
  KEY `idx_user_interest_active` (`user_id`,`delete_state`,`board_id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ç”¨æˆ·æŽ¨èå…´è¶£åå¥½';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_recommend_feedback` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ä¸»é”®',
  `user_id` bigint NOT NULL COMMENT 'ç”¨æˆ·ID',
  `article_id` bigint NOT NULL COMMENT 'å¸–å­ID',
  `reason_code` varchar(32) NOT NULL DEFAULT 'UNRELATED',
  `reason_detail` varchar(200) DEFAULT NULL,
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT 'é€»è¾‘åˆ é™¤ï¼š0å¦ 1æ˜¯',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'åˆ›å»ºæ—¶é—´',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'æ›´æ–°æ—¶é—´',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_recommend_feedback_article` (`user_id`,`article_id`),
  KEY `idx_user_recommend_feedback_active` (`user_id`,`delete_state`,`article_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ç”¨æˆ·æŽ¨èå¸–å­åé¦ˆ';
/*!40101 SET character_set_client = @saved_cs_client */;
