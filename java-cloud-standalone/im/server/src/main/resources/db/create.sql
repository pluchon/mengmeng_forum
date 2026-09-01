-- IM 域最终空库基线，已合并历史增量；本文件不会删除已有数据库或表。
-- 仅对全新空库执行；已有表时应失败并改用经过审核的前向迁移。

-- 必须先声明会话字符集：下方 mysqldump 风格的 character_set_client 只在每个 CREATE TABLE
-- 前后成对生效，到 seed INSERT 时已还原成客户端默认值。若客户端默认是 latin1，
-- 所有中文初始化数据会被按 latin1 写入而变成乱码。
SET NAMES utf8mb4;

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
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `event_id` varchar(128) NOT NULL COMMENT '业务事件ID',
  `routing_key` varchar(64) NOT NULL COMMENT 'RabbitMQ routing key',
  `payload_json` mediumtext NOT NULL COMMENT '消息体 JSON',
  `message_state` tinyint NOT NULL DEFAULT '0' COMMENT '0待投递 1已投递 2已消费 3失败 4死信',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT '重试次数',
  `last_error` varchar(200) DEFAULT NULL COMMENT '最近错误摘要',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '0否 1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_outbox_event_id` (`event_id`),
  KEY `idx_outbox_state_time` (`message_state`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='MQ本地消息表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `group_chat` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '群聊ID',
  `owner_user_id` bigint NOT NULL COMMENT '群主用户ID',
  `name` varchar(24) NOT NULL COMMENT '群名称',
  `avatar_url` varchar(512) DEFAULT NULL COMMENT '群头像URL',
  `intro` varchar(120) DEFAULT NULL COMMENT '群简介',
  `group_type` tinyint NOT NULL DEFAULT '0' COMMENT '群类型: 0公开 1私有',
  `member_limit` int NOT NULL DEFAULT '100' COMMENT '当前身份对应人数上限快照',
  `member_count` int NOT NULL DEFAULT '0' COMMENT '当前成员数',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '群状态: 0正常 1满员 2超额锁定 3已解散 4违规封禁',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除: 0否 1是',
  PRIMARY KEY (`id`),
  KEY `idx_group_owner_status` (`owner_user_id`,`status`,`delete_state`),
  KEY `idx_group_public` (`group_type`,`status`,`delete_state`,`update_time`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='群聊主表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `group_chat_join_request` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '申请ID',
  `group_id` bigint NOT NULL COMMENT '群聊ID',
  `target_user_id` bigint NOT NULL COMMENT '目标用户ID',
  `initiator_user_id` bigint NOT NULL COMMENT '发起人用户ID',
  `owner_user_id` bigint NOT NULL COMMENT '群主用户ID',
  `request_type` tinyint NOT NULL COMMENT '请求类型: 0申请加群 1邀请入群',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '处理状态: 0待处理 1已同意 2已拒绝',
  `owner_read_state` tinyint NOT NULL DEFAULT '0' COMMENT '群主查看状态: 0未读 1已读',
  `applicant_read_state` tinyint NOT NULL DEFAULT '1' COMMENT '申请人查看处理结果: 0未读 1已读',
  `handled_by_user_id` bigint DEFAULT NULL COMMENT '处理人用户ID',
  `handle_time` datetime DEFAULT NULL COMMENT '处理时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除: 0否 1是',
  PRIMARY KEY (`id`),
  KEY `idx_group_status` (`group_id`,`status`,`delete_state`,`create_time`),
  KEY `idx_target_status` (`target_user_id`,`status`,`delete_state`,`create_time`),
  KEY `idx_owner_type_status` (`owner_user_id`,`request_type`,`status`,`delete_state`,`create_time`),
  KEY `idx_applicant_result` (`target_user_id`,`request_type`,`applicant_read_state`,`status`,`delete_state`,`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='群加入申请与邀请表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `group_chat_member` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '成员记录ID',
  `group_id` bigint NOT NULL COMMENT '群聊ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `role` tinyint NOT NULL DEFAULT '1' COMMENT '角色: 0群主 1成员 2管理员',
  `remark_name` varchar(24) DEFAULT NULL COMMENT '群内备注昵称',
  `notify_mode` tinyint NOT NULL DEFAULT '0' COMMENT '提醒模式: 0正常 1仅@提醒 2完全不提醒',
  `mute_until` datetime DEFAULT NULL COMMENT '禁言截止时间',
  `last_read_message_id` bigint NOT NULL DEFAULT '0' COMMENT '最后已读群消息ID',
  `join_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态: 0正常 1已退出 2被移除',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除: 0否 1是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_member` (`group_id`,`user_id`,`delete_state`),
  KEY `idx_member_user_status` (`user_id`,`status`,`delete_state`),
  KEY `idx_member_group_status` (`group_id`,`status`,`delete_state`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='群聊成员表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `group_chat_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '群消息ID',
  `group_id` bigint NOT NULL COMMENT '群聊ID',
  `sender_user_id` bigint DEFAULT NULL COMMENT '发送者用户ID，系统消息为空',
  `message_type` tinyint NOT NULL DEFAULT '0' COMMENT '消息类型: 0文本 1表情 2图片 4图集 9系统',
  `content` varchar(500) NOT NULL COMMENT '消息内容',
  `reply_message_id` bigint DEFAULT NULL COMMENT '回复的群消息ID',
  `reply_sender_name` varchar(64) DEFAULT NULL COMMENT '被回复消息发送者昵称快照',
  `reply_content` varchar(200) DEFAULT NULL COMMENT '被回复消息内容快照',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态: 0正常 2删除 3撤回',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除: 0否 1是',
  PRIMARY KEY (`id`),
  KEY `idx_group_message` (`group_id`,`delete_state`,`id`),
  KEY `idx_sender_time` (`sender_user_id`,`delete_state`,`create_time`),
  KEY `idx_group_reply_message` (`reply_message_id`)
) ENGINE=InnoDB AUTO_INCREMENT=40 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='群聊消息表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `group_chat_message_album_image` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号, 主键, 自增',
  `message_id` bigint NOT NULL COMMENT '所属群聊消息ID',
  `media_url` varchar(500) NOT NULL COMMENT '媒体URL(OSS)',
  `media_mime` varchar(50) DEFAULT NULL COMMENT '媒体MIME',
  `media_size` bigint DEFAULT NULL COMMENT '媒体字节大小',
  `media_width` int DEFAULT NULL COMMENT '媒体像素宽',
  `media_height` int DEFAULT NULL COMMENT '媒体像素高',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '图集内展示顺序, 从0开始',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0否 1是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_album_message_sort` (`message_id`,`sort_order`),
  KEY `idx_group_album_message_state_sort` (`message_id`,`delete_state`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='群聊图集图片表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号, 主键, 自增',
  `post_user_id` bigint NOT NULL COMMENT '发送者编号',
  `receive_user_id` bigint NOT NULL COMMENT '接收者编号',
  `message_type` tinyint NOT NULL DEFAULT '0' COMMENT '消息类型: 0文本 1图片 2GIF 4图集',
  `content` varchar(500) DEFAULT NULL COMMENT '文本内容; 单图/GIF 消息为空; 图集可存说明文字',
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
CREATE TABLE `message_album_image` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号, 主键, 自增',
  `message_id` bigint NOT NULL COMMENT '所属私信消息ID',
  `media_url` varchar(500) NOT NULL COMMENT '媒体URL(OSS)',
  `media_mime` varchar(50) DEFAULT NULL COMMENT '媒体MIME',
  `media_size` bigint DEFAULT NULL COMMENT '媒体字节大小',
  `media_width` int DEFAULT NULL COMMENT '媒体像素宽',
  `media_height` int DEFAULT NULL COMMENT '媒体像素高',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '图集内展示顺序, 从0开始',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0否 1是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_message_album_image_message_sort` (`message_id`,`sort_order`),
  KEY `idx_message_album_image_message_state_sort` (`message_id`,`delete_state`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='私信图集图片表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `message_session_visibility` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号, 主键, 自增',
  `user_id` bigint NOT NULL COMMENT '状态所属用户ID',
  `peer_user_id` bigint NOT NULL COMMENT '私信对方用户ID',
  `hidden_state` tinyint NOT NULL DEFAULT '0' COMMENT '是否在会话列表隐藏: 0否 1是',
  `pinned_at` datetime DEFAULT NULL COMMENT '置顶时刻; NULL 表示未置顶',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0否 1是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_message_session_visibility_user_peer` (`user_id`,`peer_user_id`),
  KEY `idx_message_session_visibility_user_hidden_time` (`user_id`,`hidden_state`,`delete_state`,`update_time`),
  KEY `idx_message_session_visibility_user_pinned` (`user_id`,`pinned_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='私信会话用户视角显示状态';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号, 主键, 自增',
  `receive_user_id` bigint NOT NULL COMMENT '接收者用户ID',
  `type` tinyint NOT NULL COMMENT '系统消息类型: 1审核通过 2审核未通过 3审核异常 4注册入站引导 99公告(预留)',
  `title` varchar(100) NOT NULL COMMENT '消息标题(展示用)',
  `content` varchar(500) NOT NULL COMMENT '消息正文(展示用)',
  `search_text` varchar(200) DEFAULT NULL COMMENT '通知业务搜索文本',
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

CREATE TABLE `im_ai_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_id` varchar(64) NOT NULL,
  `task_type` tinyint NOT NULL DEFAULT '1',
  `target_type` tinyint NOT NULL,
  `target_id` bigint NOT NULL,
  `content_hash` varchar(64) NOT NULL,
  `trigger_user_id` bigint DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT '0',
  `retry_count` int NOT NULL DEFAULT '0',
  `result_code` varchar(64) DEFAULT NULL,
  `result_reason` varchar(500) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `delete_state` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_im_ai_task_id` (`task_id`),
  KEY `idx_im_ai_task_pending` (`status`,`delete_state`,`update_time`),
  KEY `idx_im_ai_task_target` (`target_type`,`target_id`,`content_hash`,`delete_state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='IM域AI异步任务';

CREATE TABLE `chat_message_report` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `reporter_user_id` bigint NOT NULL,
  `conversation_type` tinyint NOT NULL,
  `message_id` bigint NOT NULL,
  `content_hash` varchar(64) NOT NULL,
  `reason` varchar(200) NOT NULL,
  `task_id` varchar(64) NOT NULL,
  `result_status` tinyint NOT NULL DEFAULT '0',
  `result_message` varchar(500) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `delete_state` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_chat_report_idempotent` (`reporter_user_id`,`conversation_type`,`message_id`,`content_hash`),
  KEY `idx_chat_report_task` (`task_id`,`result_status`,`delete_state`),
  KEY `idx_chat_report_user_time` (`reporter_user_id`,`delete_state`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='私信与群消息举报记录';
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
  `origin_message_id` bigint DEFAULT NULL COMMENT '来源私信消息ID(收藏自私信聊天图片时)',
  `origin_group_message_id` bigint DEFAULT NULL COMMENT '来源群聊消息ID(收藏自群聊图片时); 与 origin_message_id 互斥',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除: 0否 1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uix_user_url` (`user_id`,`media_url`),
  KEY `idx_user_create` (`user_id`,`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=20 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户聊天表情收藏表';
/*!40101 SET character_set_client = @saved_cs_client */;

-- ----------------------------
-- Demo seeds: forum_notice
-- ----------------------------
INSERT INTO `forum_notice` (`notice_kind`, `category_scope`, `template_id`, `sidebar_key`, `title`, `subtitle`, `content_markdown`, `body_json`, `sort`, `pin_top`, `publish_state`, `delete_state`) VALUES
(0, 0, 'welcome_hero_right', 'onboarding_welcome', '欢迎来到萌萌论坛', '在这里，发现更有趣的社区生活',
 '# 欢迎加入萌萌论坛\n\n请先阅读下方说明，遵守社区规范。发帖、评论与私信均需实名登录账号。',
 JSON_OBJECT(
   'highlights', JSON_ARRAY(
     JSON_OBJECT('label', '友好互动', 'labelColor', '#f53f3f', 'text', '尊重他人，拒绝人身攻击与引战。'),
     JSON_OBJECT('label', '优质内容', 'labelColor', '#00b42a', 'text', '鼓励原创与深度分享，少灌水多干货。'),
     JSON_OBJECT('label', '安全上网', 'labelColor', '#165dff', 'text', '勿泄露隐私，勿传播违法与低俗内容。')
   ),
   'coverImageUrl', ''
 ),
 0, 1, 1, 0),
(1, 0, 'plain_sections', 'activity_lottery', '积分幸运抽上线', '单次 30 积分，十连有稀有保底',
 '## 活动说明\n\n- 单次抽奖消耗 **30 积分**，积分奖即时到账。\n- **十连**时至少获得 1 件稀有档（大奖 / 周边 / VIP 体验）。\n- 累计 **50 抽**未中神秘大奖时，下一次必出神秘大奖档。\n- 周边与 VIP 类奖品通过站内信发放，请留意通知中心。',
 JSON_OBJECT('sections', JSON_ARRAY(
   JSON_OBJECT('title', '奖池', 'body', '谢谢参与、积分奖、安慰奖、周边、神秘大奖（含 VIP/高积分子项）。'),
   JSON_OBJECT('title', '概率', 'body', '按活动权重动态计算；某档售罄后自动剔除并重算。')
 )),
 10, 0, 1, 0),
(1, 0, 'plain_sections', 'activity_checkin', '每日签到', '连续签到积分更多',
 '## 签到规则\n\n每日签到可获得积分，连续签到天数越高奖励越多（具体数额以签到页展示为准）。积分可用于抽奖、表情商城与 AI 功能。',
 JSON_OBJECT('sections', JSON_ARRAY()),
 11, 0, 1, 0),
(4, 0, 'plain_sections', 'rules_general', '全站发帖与评论规范', '适用于所有分类与版块',
 '## 全站规范\n\n1. 禁止违法、色情、暴力、广告刷屏与盗图盗文。\n2. 标题需与正文相关，勿标题党。\n3. 引战、歧视、泄露他人隐私一律删除并视情节禁言。\n4. 帖子发布后进入审核，通过后方公开展示。',
 JSON_OBJECT('sections', JSON_ARRAY()),
 20, 0, 1, 0),
(4, 0, 'plain_sections', 'rules_post_tag', '帖子标签说明', '每帖最多 5 个标签',
 '## 标签用法\n\n发帖时可选择版块推荐标签，也可提交新标签（AI 审核通过后入库）。标签会用于搜索与推荐，请勿恶意堆砌无关标签。',
 JSON_OBJECT('sections', JSON_ARRAY()),
 21, 0, 1, 0),
(4, 1, 'plain_sections', 'rules_category_acg', '「动画番剧」版块说明', '同人、番剧、COS 等内容规范',
 '## 本分类补充\n\n- 转载需注明出处，尊重版权与角色名。\n- COS / 返图请避免无关广告引流。\n- 剧透内容请在标题或开头标注。',
 JSON_OBJECT('sections', JSON_ARRAY()),
 30, 0, 1, 0);

