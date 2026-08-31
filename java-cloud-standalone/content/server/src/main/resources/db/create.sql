-- content 域最终空库基线，已合并历史增量；本文件不会删除已有数据库或表。
-- 仅对全新空库执行；已有表时应失败并改用经过审核的前向迁移。

-- 必须先声明会话字符集：下方 mysqldump 风格的 character_set_client 只在每个 CREATE TABLE
-- 前后成对生效，到 seed INSERT 时已还原成客户端默认值。若客户端默认是 latin1，
-- 所有中文初始化数据会被按 latin1 写入而变成乱码。
SET NAMES utf8mb4;

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
  `hls_url` varchar(500) DEFAULT NULL COMMENT 'HLS m3u8地址(仅 media_type=1)',
  `video_transcode_status` tinyint NOT NULL DEFAULT '0' COMMENT 'HLS转码: 0NONE 1PROCESSING 2READY 3FAILED',
  `music_key` varchar(128) DEFAULT NULL COMMENT '曲库键(与 OSS music_info 文件名 stem 对齐)',
  `music_title` varchar(100) DEFAULT NULL COMMENT '帖子配乐歌名',
  `music_cover_url` varchar(500) DEFAULT NULL COMMENT '配乐封面URL OSS: music/music_avatar/',
  `music_audio_url` varchar(500) DEFAULT NULL COMMENT '配乐音频URL OSS: music/music_info/',
  `music_lrc_url` varchar(500) DEFAULT NULL COMMENT '配乐歌词URL OSS: music/music_lrc/',
  `content_type` tinyint NOT NULL DEFAULT '0' COMMENT '内容类型: 0富文本 1Markdown',
  `article_type` tinyint NOT NULL DEFAULT '0' COMMENT '帖子业务类型: 0普通帖 1问答帖',
  `question_status` tinyint DEFAULT NULL COMMENT '问答状态: 0待解决 1已解决; 2已关闭(历史兼容,新流程不再写入); 普通帖为空',
  `accepted_reply_id` bigint DEFAULT NULL COMMENT '历史最佳答案一级回答ID(兼容字段,新采纳写入 article_question_accept)',
  `favorite_count` int NOT NULL DEFAULT '0' COMMENT '收藏数(被加入收藏夹的总次数, 跨用户去重为 1)',
  `sub_reply_count` int NOT NULL DEFAULT '0' COMMENT '楼中楼回复数(独立于 reply_count, 楼层数仍只算一级回复)',
  `state` tinyint NOT NULL DEFAULT '0' COMMENT '审核状态: 0正常, 1禁用',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '发布状态: 0草稿 1审核中 2审核通过(瞬态,自动转5) 3审核未通过 4审核异常 5已发布',
  `audit_task_id` varchar(64) DEFAULT NULL COMMENT '当前审核任务ID(UUID); 与 Python LangGraph thread_id 关联',
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
CREATE TABLE `article_question_accept` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键，自增',
  `article_id` bigint NOT NULL COMMENT '问答帖 ID',
  `reply_id` bigint DEFAULT NULL COMMENT '一级回答 ID，与 sub_reply_id 互斥',
  `sub_reply_id` bigint DEFAULT NULL COMMENT '楼中楼 ID，与 reply_id 互斥',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `delete_state` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0=正常 1=已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_aqa_article_reply` (`article_id`,`reply_id`),
  UNIQUE KEY `uk_aqa_article_sub_reply` (`article_id`,`sub_reply_id`),
  KEY `idx_aqa_article_id` (`article_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='问答帖采纳记录（可多条）';
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
  `like_count` int NOT NULL DEFAULT '0' COMMENT '点赞数',
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
CREATE TABLE `article_video_danmaku_like` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `danmaku_id` bigint NOT NULL COMMENT '弹幕ID',
  `user_id` bigint NOT NULL COMMENT '点赞用户',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uix_danmaku_user` (`danmaku_id`,`user_id`),
  KEY `idx_user_danmaku` (`user_id`,`danmaku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='视频弹幕点赞';
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
) ENGINE=InnoDB AUTO_INCREMENT=50 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='版块表';
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
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='分类表';

-- 二次元社区默认分区（参考 Bangumi ACGN 五大域 + 同人/Cos/V 圈 + 内容分享）
INSERT INTO `category` (`id`, `name`, `description`, `sort`, `state`, `delete_state`) VALUES
(1, '动画番剧', '新番追更、经典补番、国创与剧场版讨论', 1, 0, 0),
(2, '漫画轻小说', '日漫国漫、轻小说与同人小说交流', 2, 0, 0),
(3, '游戏电玩', '二次元手游、Gal、主机与乙女养成', 3, 0, 0),
(4, '同人创作', '绘画、手工、AI 绘图与创作互助', 4, 0, 0),
(5, '内容分享', '插画、壁纸、立绘截图与表情包分享', 5, 0, 0),
(6, 'Cosplay周边', 'Cos 摄影妆造、手办模型与周边开箱', 6, 0, 0),
(7, '音乐声优', '动漫音乐、声优话题与翻唱演奏', 7, 0, 0),
(8, '虚拟主播', 'VTuber、切片杂谈与歌回分享', 8, 0, 0),
(9, '资讯站务', '次元资讯、日常萌物与站务公告', 9, 0, 0),
(10, '科技数码', '电脑、手机、软件工具与前沿科技交流', 10, 0, 0),
(11, '生活兴趣', '美食、旅行、摄影与运动生活分享', 11, 0, 0),
(12, '学习成长', '校园、职场、语言与知识问答交流', 12, 0, 0);

INSERT INTO `board` (`id`, `name`, `article_count`, `sort`, `category_id`, `state`, `delete_state`) VALUES
(1, '当季新番', 0, 1, 1, 0, 0),
(2, '经典补番', 0, 2, 1, 0, 0),
(3, '国创动画', 0, 3, 1, 0, 0),
(4, '剧场版OVA', 0, 4, 1, 0, 0),
(5, '动画情报', 0, 5, 1, 0, 0),
(6, '日漫连载', 0, 1, 2, 0, 0),
(7, '国漫条漫', 0, 2, 2, 0, 0),
(8, '轻小说', 0, 3, 2, 0, 0),
(9, '同人文', 0, 4, 2, 0, 0),
(10, '二次元手游', 0, 1, 3, 0, 0),
(11, 'Galgame', 0, 2, 3, 0, 0),
(12, '主机单机', 0, 3, 3, 0, 0),
(13, '乙女养成', 0, 4, 3, 0, 0),
(14, '游戏攻略', 0, 5, 3, 0, 0),
(15, '同人绘画', 0, 1, 4, 0, 0),
(16, '手工模型', 0, 2, 4, 0, 0),
(17, 'AI绘画交流', 0, 3, 4, 0, 0),
(18, '创作求助', 0, 4, 4, 0, 0),
(19, '插画分享', 0, 1, 5, 0, 0),
(20, '壁纸美图', 0, 2, 5, 0, 0),
(21, '角色立绘', 0, 3, 5, 0, 0),
(22, '截图分享', 0, 4, 5, 0, 0),
(23, '表情包', 0, 5, 5, 0, 0),
(24, 'Cos摄影', 0, 1, 6, 0, 0),
(25, '妆造服装', 0, 2, 6, 0, 0),
(26, '手办开箱', 0, 3, 6, 0, 0),
(27, '周边收藏', 0, 4, 6, 0, 0),
(28, '动漫音乐', 0, 1, 7, 0, 0),
(29, '声优话题', 0, 2, 7, 0, 0),
(30, '翻唱演奏', 0, 3, 7, 0, 0),
(31, '虚拟主播', 0, 1, 8, 0, 0),
(32, '直播切片', 0, 2, 8, 0, 0),
(33, '歌回杂谈', 0, 3, 8, 0, 0),
(34, '次元资讯', 0, 1, 9, 0, 0),
(35, '萌物日常', 0, 2, 9, 0, 0),
(36, '提问求助', 0, 3, 9, 0, 0),
(37, '站务公告', 0, 4, 9, 0, 0),
(38, '电脑硬件', 0, 1, 10, 0, 0),
(39, '手机数码', 0, 2, 10, 0, 0),
(40, '软件工具', 0, 3, 10, 0, 0),
(41, 'AI科技', 0, 4, 10, 0, 0),
(42, '美食探店', 0, 1, 11, 0, 0),
(43, '旅行户外', 0, 2, 11, 0, 0),
(44, '摄影记录', 0, 3, 11, 0, 0),
(45, '运动健康', 0, 4, 11, 0, 0),
(46, '校园学习', 0, 1, 12, 0, 0),
(47, '职场成长', 0, 2, 12, 0, 0),
(48, '语言学习', 0, 3, 12, 0, 0),
(49, '知识问答', 0, 4, 12, 0, 0);
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `forum_article_ai_feature` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `article_id` bigint NOT NULL,
  `feature_json` mediumtext NOT NULL,
  `summary_text` text DEFAULT NULL COMMENT '帖子AI总结',
  `summary_status` tinyint NOT NULL DEFAULT '0' COMMENT '总结状态: 0未就绪 1处理中 2可用 3失败 4正文过短',
  `summary_content_hash` varchar(64) DEFAULT NULL COMMENT '总结对应正文哈希',
  `summary_generated_at` datetime DEFAULT NULL COMMENT '总结生成时间',
  `feature_version` varchar(32) NOT NULL,
  `content_hash` varchar(64) NOT NULL,
  `generated_by` varchar(32) NOT NULL,
  `delete_state` tinyint NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_article_ai_feature_article` (`article_id`),
  KEY `idx_article_ai_feature_state_time` (`delete_state`,`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='帖子推荐AI特征';

CREATE TABLE `content_ai_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_id` varchar(64) NOT NULL,
  `task_type` tinyint NOT NULL,
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
  UNIQUE KEY `uk_content_ai_task_id` (`task_id`),
  KEY `idx_content_ai_task_pending` (`status`,`delete_state`,`update_time`),
  KEY `idx_content_ai_task_target` (`target_type`,`target_id`,`content_hash`,`delete_state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='内容域AI异步任务';

CREATE TABLE `content_report` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `reporter_user_id` bigint NOT NULL,
  `target_type` tinyint NOT NULL,
  `target_id` bigint NOT NULL,
  `content_hash` varchar(64) NOT NULL,
  `reason` varchar(200) NOT NULL,
  `task_id` varchar(64) NOT NULL,
  `result_status` tinyint NOT NULL DEFAULT '0',
  `result_message` varchar(500) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `delete_state` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_content_report_idempotent` (`reporter_user_id`,`target_type`,`target_id`,`content_hash`),
  KEY `idx_content_report_task` (`task_id`,`result_status`,`delete_state`),
  KEY `idx_content_report_user_time` (`reporter_user_id`,`delete_state`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='帖子与评论举报记录';
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

-- 默认标签：0全站；1分类（对齐当前二次元分区 id）
INSERT INTO `forum_article_tag` (`name`, `color_key`, `scope_type`, `scope_id`, `sort`, `state`, `delete_state`) VALUES
('吐槽', 'rose', 0, 0, 1, 0, 0),
('安利', 'sky', 0, 0, 2, 0, 0),
('求推荐', 'mint', 0, 0, 3, 0, 0),
('晒图', 'amber', 0, 0, 4, 0, 0),
('杂谈', 'slate', 0, 0, 5, 0, 0),
('无剧透', 'mint', 1, 1, 10, 0, 0),
('补番清单', 'sky', 1, 1, 11, 0, 0),
('本子安利', 'violet', 1, 2, 10, 0, 0),
('抽卡晒欧', 'amber', 1, 3, 10, 0, 0),
('攻略', 'teal', 1, 3, 11, 0, 0),
('约稿交流', 'sky', 1, 4, 10, 0, 0),
('壁纸向', 'amber', 1, 5, 10, 0, 0),
('场照', 'violet', 1, 6, 10, 0, 0),
('开箱', 'sky', 1, 6, 11, 0, 0),
('翻唱', 'rose', 1, 7, 10, 0, 0),
('切片安利', 'sky', 1, 8, 10, 0, 0),
('树洞', 'slate', 1, 9, 10, 0, 0);

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
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户推荐AI画像快照';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_favorite_folder` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号, 主键, 自增',
  `user_id` bigint NOT NULL COMMENT '所属用户编号',
  `name` varchar(50) NOT NULL COMMENT '收藏夹名称',
  `cover_url` varchar(500) DEFAULT NULL COMMENT '收藏夹封面OSS地址',
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
CREATE TABLE `user_recommend_feedback` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `article_id` bigint NOT NULL COMMENT '帖子ID',
  `reason_code` varchar(32) NOT NULL DEFAULT 'UNRELATED',
  `reason_detail` varchar(200) DEFAULT NULL,
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否 1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_recommend_feedback_article` (`user_id`,`article_id`),
  KEY `idx_user_recommend_feedback_active` (`user_id`,`delete_state`,`article_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户推荐帖子反馈';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_recommendation_setting` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `personalized_enabled` tinyint NOT NULL DEFAULT '1' COMMENT '个性化推荐开关: 0关闭 1开启',
  `interest_board_ids` varchar(128) DEFAULT NULL COMMENT '手选兴趣版块ID JSON数组，最多5个',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否 1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_recommendation_setting_user` (`user_id`),
  KEY `idx_user_recommendation_setting_active` (`user_id`,`delete_state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户个性化推荐开关';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_music` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '上传用户',
  `music_key` varchar(128) NOT NULL COMMENT 'OSS stem: 歌名_用户ID_yyyyMMddHHmmss',
  `title` varchar(100) NOT NULL COMMENT '歌名',
  `artist` varchar(100) NOT NULL COMMENT '歌手',
  `album` varchar(100) DEFAULT NULL COMMENT '专辑',
  `duration_text` varchar(16) DEFAULT NULL COMMENT '时长 mm:ss',
  `cover_url` varchar(500) DEFAULT NULL COMMENT '封面 OSS music/music_avatar/',
  `audio_url` varchar(500) NOT NULL COMMENT '音频 OSS music/music_info/',
  `lrc_url` varchar(500) DEFAULT NULL COMMENT '歌词 OSS music/music_lrc/',
  `lyric_text` mediumtext COMMENT '歌词正文',
  `mood_tags` varchar(256) DEFAULT NULL COMMENT '氛围标签 JSON 数组',
  `ai_profile` json DEFAULT NULL COMMENT 'AI曲风分析结果',
  `review_result` json DEFAULT NULL COMMENT '审核结论',
  `ai_analyzed_at` datetime DEFAULT NULL COMMENT 'AI分析完成时间',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0未发布 1审核中 2已发布 3未通过',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除: 0否 1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_music_key` (`music_key`),
  KEY `idx_user_music_user_status` (`user_id`,`status`,`delete_state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户上传歌曲';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `music_mood_tag` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(16) NOT NULL COMMENT '标签名',
  `source` varchar(8) NOT NULL DEFAULT 'AI' COMMENT '来源 BUILTIN内置 AI补充 USER创作者创建',
  `create_user_id` bigint DEFAULT NULL COMMENT '创建者，仅 source=USER 有值',
  `use_count` int NOT NULL DEFAULT '0' COMMENT '被歌曲使用次数，筛选栏按此降序',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否可用 0否 1是',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除: 0否 1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_music_mood_tag_name` (`name`),
  KEY `idx_music_mood_tag_rank` (`enabled`,`delete_state`,`use_count`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='音乐氛围标签池';

-- 「热门」是默认态而非真实氛围，不入池
INSERT INTO `music_mood_tag` (`name`, `source`, `use_count`) VALUES
  ('治愈', 'BUILTIN', 0),
  ('清新', 'BUILTIN', 0),
  ('浪漫', 'BUILTIN', 0),
  ('轻松', 'BUILTIN', 0),
  ('深夜', 'BUILTIN', 0),
  ('轻音乐', 'BUILTIN', 0),
  ('适合配图', 'BUILTIN', 0);

CREATE TABLE `user_music_favorite` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '收藏用户',
  `music_key` varchar(128) NOT NULL COMMENT '曲库键',
  `title` varchar(100) NOT NULL COMMENT '歌名快照',
  `artist` varchar(100) DEFAULT NULL COMMENT '歌手快照',
  `album` varchar(100) DEFAULT NULL COMMENT '专辑快照',
  `duration_text` varchar(16) DEFAULT NULL COMMENT '时长快照',
  `cover_url` varchar(500) DEFAULT NULL COMMENT '封面快照',
  `audio_url` varchar(500) NOT NULL COMMENT '音频快照',
  `lrc_url` varchar(500) DEFAULT NULL COMMENT '歌词快照',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除: 0否 1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_music_fav` (`user_id`,`music_key`),
  KEY `idx_user_music_fav_user` (`user_id`,`delete_state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户歌曲收藏';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_music_play_history` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '播放用户',
  `music_key` varchar(128) NOT NULL COMMENT '曲库键',
  `title` varchar(100) NOT NULL COMMENT '歌名快照',
  `artist` varchar(100) DEFAULT NULL COMMENT '歌手快照',
  `album` varchar(100) DEFAULT NULL COMMENT '专辑快照',
  `duration_text` varchar(16) DEFAULT NULL COMMENT '时长快照',
  `cover_url` varchar(500) DEFAULT NULL COMMENT '封面快照',
  `audio_url` varchar(500) NOT NULL COMMENT '音频快照',
  `lrc_url` varchar(500) DEFAULT NULL COMMENT '歌词快照',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除: 0否 1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次播放时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最近播放时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_music_play` (`user_id`,`music_key`),
  KEY `idx_user_music_play_user` (`user_id`,`delete_state`,`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户歌曲最近播放';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_music_play_stat` (
  `music_key` varchar(128) NOT NULL COMMENT '曲库键',
  `play_count` bigint NOT NULL DEFAULT '0' COMMENT '全站累计播放',
  `weekly_play_count` bigint NOT NULL DEFAULT '0' COMMENT '当前自然周播放',
  `week_start` date NOT NULL COMMENT '当前统计周起始周一',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最近播放时间',
  PRIMARY KEY (`music_key`),
  KEY `idx_user_music_play_stat_weekly` (`week_start`,`weekly_play_count`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='歌曲全站播放统计';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_music_recommend_slate` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `period_key` varchar(32) NOT NULL COMMENT '周期键，如 2026-W34',
  `source` varchar(16) NOT NULL DEFAULT 'RULE' COMMENT '来源 AI/RULE',
  `music_keys_json` json NOT NULL COMMENT '有序 musicKey 列表，目标 30 首',
  `expire_time` datetime NOT NULL COMMENT '过期时间',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除 0否 1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_music_recommend_slate_user_period` (`user_id`,`period_key`),
  KEY `idx_user_music_recommend_slate_expire` (`user_id`,`delete_state`,`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户音乐推荐片单';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `creator_daily_metric` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '创作者用户ID',
  `stat_date` date NOT NULL COMMENT '东八区统计日',
  `read_count` int NOT NULL DEFAULT '0' COMMENT '当日新增阅读数',
  `like_count` int NOT NULL DEFAULT '0' COMMENT '当日新增点赞数',
  `publish_count` int NOT NULL DEFAULT '0' COMMENT '当日新增发布作品数',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `delete_state` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_creator_daily_metric_user_date` (`user_id`,`stat_date`),
  KEY `idx_creator_daily_metric_date` (`stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='创作中心按日互动统计';
/*!40101 SET character_set_client = @saved_cs_client */;
