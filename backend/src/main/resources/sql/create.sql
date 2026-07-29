-- 全库初始化脚本（执行即删库重建 forum_db；勿与 PostgreSQL 的 postgres_ai_session.sql 混跑）
-- 结构：DROP/CREATE 全部表 + 少量示例/配置种子（看板娘、分类版块、签到兜底、公告、AI 单价、VIP 配额、抽奖演示）。
-- 不含用户/帖子等业务数据；生产数据请走用户端注册与日常运营维护。
-- 结构变更：全新环境请整库重跑本脚本；已有库请执行同目录 incremental_concurrency.sql（MySQL）与 postgres_ai_session.sql（PostgreSQL）。
-- 并发幂等（Phase 01~05）：points_log.idempotency_key、lottery_draw_request、forum_ai_usage_log(related_id 唯一)、user_follow 唯一、article_like 唯一。
DROP DATABASE IF EXISTS `forum_db`;
CREATE DATABASE `forum_db` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

USE `forum_db`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 1. 用户表 (user)
-- ----------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
                        `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户编号, 主键, 自增',
                        `username` varchar(20) NOT NULL COMMENT '用户名, 非空, 唯一',
                        `password` varchar(255) NOT NULL COMMENT 'BCrypt 哈希（约60字符）',
                        `nickname` varchar(50) NOT NULL COMMENT '昵称, 非空',
                        `phone_num` varchar(255) DEFAULT NULL COMMENT '手机号密文',
                        `phone_hash` varchar(64) DEFAULT NULL COMMENT '手机号HMAC，用于等值查询',
                        `email` varchar(255) DEFAULT NULL COMMENT '邮箱密文',
                        `email_hash` varchar(64) DEFAULT NULL COMMENT '邮箱HMAC，用于等值查询',
                        `gender` tinyint NOT NULL DEFAULT 2 COMMENT '0女 1男 2保密, 非空, 默认2',
                        `salt` varchar(32) NOT NULL DEFAULT '' COMMENT '历史 MD5 盐；BCrypt 用户为空串',
                        `avatar_url` varchar(255) DEFAULT NULL COMMENT '用户头像URL',
                        `background_url` varchar(500) DEFAULT NULL COMMENT '用户主页背景图URL',
                        `article_count` int NOT NULL DEFAULT 0 COMMENT '发帖数量',
                        `is_admin` tinyint NOT NULL DEFAULT 0 COMMENT '是否管理员, 0否 1是',
                        `points` int NOT NULL DEFAULT 0 COMMENT '积分钱包余额(签到入账+商城消费)',
                        `vip_tier` tinyint NOT NULL DEFAULT 0 COMMENT 'VIP档位: 0普通 1PRO 2MAX',
                        `vip_expire_at` datetime DEFAULT NULL COMMENT 'VIP到期时间; NULL且vip_tier>0可视为运营期内不限期占位',
                        `mascot_model_id` bigint DEFAULT NULL COMMENT '用户选择的看板娘模型 forum_mascot_model.id',
                        `remark` varchar(1000) DEFAULT NULL COMMENT '备注, 自我介绍',
                        `ip_region` varchar(32) DEFAULT NULL COMMENT '最近登录IP属地(省份/国家)',
                        `state` tinyint NOT NULL DEFAULT 0 COMMENT '状态: 0正常, 1禁言',
                        `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除: 0否 1是',
                        `lottery_pity_draws` int NOT NULL DEFAULT 0 COMMENT '抽奖硬保底计数：连续未中神秘大奖(is_jackpot)父档的次数，命中后归零',
                        `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                        `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                        PRIMARY KEY (`id`),
                        UNIQUE INDEX `user_username_uindex` (`username`),
                        UNIQUE INDEX `user_phone_hash_uindex` (`phone_hash`),
                        UNIQUE INDEX `user_email_hash_uindex` (`email_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';

-- ----------------------------
-- 1c. 用户登录日志表 (user_login_log)
-- ----------------------------
DROP TABLE IF EXISTS `user_login_log`;
CREATE TABLE `user_login_log` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` bigint NOT NULL COMMENT '用户ID',
    `login_type` varchar(16) NOT NULL COMMENT 'password/mail/sms',
    `ip_address` varchar(64) DEFAULT NULL COMMENT '登录IP',
    `user_agent` varchar(512) DEFAULT NULL COMMENT 'UA摘要',
    `login_status` tinyint NOT NULL DEFAULT 1 COMMENT '1成功 0失败',
    `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '0否 1是',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_login_log_user_time` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户登录日志';

-- ----------------------------
-- 1d. 用户关注关系表 (user_follow)
-- ----------------------------
DROP TABLE IF EXISTS `user_follow`;
CREATE TABLE `user_follow` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `follower_id` bigint NOT NULL COMMENT '关注者用户ID',
    `followee_id` bigint NOT NULL COMMENT '被关注者用户ID',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '关注时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uix_follower_followee` (`follower_id`, `followee_id`),
    KEY `idx_followee_id` (`followee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户关注关系表';

-- ----------------------------
-- 1b. 看板娘 Live2D 模型库（用户端仅展示上架；资源由 Vite /live2d-assets 映射至 live2d-master）
-- ----------------------------
DROP TABLE IF EXISTS `forum_mascot_model`;
CREATE TABLE `forum_mascot_model` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `code` varchar(64) NOT NULL COMMENT '唯一标识，对应 oh-my-live2d model.name',
    `name` varchar(128) NOT NULL COMMENT '展示名称',
    `model_rel_path` varchar(512) NOT NULL COMMENT '相对仓库 live2d/live2d-master 的路径，如 live2d_3/model/.../x.model3.json',
    `model_scale` decimal(8,4) NOT NULL DEFAULT 0.1000 COMMENT '模型缩放',
    `pos_x` int NOT NULL DEFAULT 0 COMMENT '模型位置 X',
    `pos_y` int NOT NULL DEFAULT 72 COMMENT '模型位置 Y',
    `stage_width` int NOT NULL DEFAULT 260 COMMENT '舞台宽度 px',
    `stage_height` int NOT NULL DEFAULT 320 COMMENT '舞台高度 px',
    `shelf_status` tinyint NOT NULL DEFAULT 1 COMMENT '0草稿 1上架 2下架（默认上架）',
    `sort_order` int NOT NULL DEFAULT 0,
    `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '0正常 1软删',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_mascot_code` (`code`),
    KEY `idx_mascot_shelf_del` (`shelf_status`, `delete_state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='看板娘模型库';

-- 上架种子：live2d/live2d-master/model 下模型（路径相对仓库 live2d/live2d-master/），默认 shelf_status=1
INSERT INTO `forum_mascot_model` (`code`, `name`, `model_rel_path`, `model_scale`, `pos_x`, `pos_y`, `stage_width`, `stage_height`, `shelf_status`, `sort_order`, `delete_state`) VALUES
    ('snow_miku', 'snow_miku', 'model/snow_miku/model.json', 0.1400, 0, 72, 340, 380, 1, 20, 0),
    ('xiaomai', 'xiaomai', 'model/xiaomai/xiaomai.model.json', 0.1400, 0, 72, 340, 380, 1, 30, 0);

-- ----------------------------
-- 2. 版块表 (board)
-- ----------------------------
DROP TABLE IF EXISTS `board`;
CREATE TABLE `board` (
                         `id` bigint NOT NULL AUTO_INCREMENT COMMENT '版块编号, 主键, 自增',
                         `name` varchar(50) NOT NULL COMMENT '版块名',
                         `article_count` int NOT NULL DEFAULT 0 COMMENT '帖子数量',
                         `sort` int NOT NULL DEFAULT 0 COMMENT '排序优先级, 升序',
                         `category_id` bigint DEFAULT NULL COMMENT '所属分类ID',
                         `state` tinyint NOT NULL DEFAULT 0 COMMENT '状态: 0正常, 1禁用',
                         `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除: 0否 1是',
                         `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                         `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                         PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='版块表';

-- ----------------------------
-- 3. 帖子表 (article)
-- ----------------------------
DROP TABLE IF EXISTS `article`;
CREATE TABLE `article` (
                           `id` bigint NOT NULL AUTO_INCREMENT COMMENT '帖子编号, 主键, 自增',
                           `board_id` bigint NOT NULL COMMENT '关联板块编号',
                           `user_id` bigint NOT NULL COMMENT '发帖人编号',
                           `title` varchar(100) NOT NULL COMMENT '标题',
                           `content` text NOT NULL COMMENT '帖子正文',
                           `visit_count` int NOT NULL DEFAULT 0 COMMENT '访问量',
                           `reply_count` int NOT NULL DEFAULT 0 COMMENT '回复数',
                           `like_count` int NOT NULL DEFAULT 0 COMMENT '点赞数',
                           `cover_img` varchar(255) DEFAULT NULL COMMENT '封面图URL',
                           `media_type` tinyint NOT NULL DEFAULT 0 COMMENT '帖子媒体类型: 0图片相册 1视频(单个)',
                           `video_url` varchar(500) DEFAULT NULL COMMENT '视频URL(仅 media_type=1 时有效, OSS: forum_vedio/article_vedio/)',
                           `content_type` tinyint NOT NULL DEFAULT 0 COMMENT '内容类型: 0富文本 1Markdown',
                           `article_type` tinyint NOT NULL DEFAULT 0 COMMENT '帖子业务类型: 0普通帖 1问答帖',
                           `question_status` tinyint DEFAULT NULL COMMENT '问答状态: 0待解决 1已解决 2已关闭; 普通帖为空',
                           `accepted_reply_id` bigint DEFAULT NULL COMMENT '最佳答案对应的一级回答ID',
                           `favorite_count` int NOT NULL DEFAULT 0 COMMENT '收藏数(被加入收藏夹的总次数, 跨用户去重为 1)',
                           `sub_reply_count` int NOT NULL DEFAULT 0 COMMENT '楼中楼回复数(独立于 reply_count, 楼层数仍只算一级回复)',
                           `state` tinyint NOT NULL DEFAULT 0 COMMENT '审核状态: 0正常, 1禁用',
                           `status` tinyint NOT NULL DEFAULT 0 COMMENT '发布状态: 0草稿 1审核中 2审核通过(瞬态,自动转5) 3审核未通过 4审核异常 5已发布',
                           `audit_task_id` varchar(64) DEFAULT NULL COMMENT '当前审核任务ID(UUID); 与 Python LangGraph thread_id 关联',
                           `audit_notify_email` tinyint NOT NULL DEFAULT 0 COMMENT '审核结果是否额外推邮件: 0否 1是; 站内信无论如何都发',
                           `audit_retry_count` tinyint NOT NULL DEFAULT 0 COMMENT '当前累计提交审核次数(0~3); 达到上限提示联系管理员',
                           `audit_result_message` varchar(500) DEFAULT NULL COMMENT '最近一次审核结论文本(通过原因/拒绝理由)',
                           `audit_submitted_at` datetime DEFAULT NULL COMMENT '最近一次审核提交时间',
                           `audit_finished_at` datetime DEFAULT NULL COMMENT '最近一次审核结束时间',
                           `ip_region` varchar(32) DEFAULT NULL COMMENT '发帖时IP属地快照',
                           `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除: 0否 1是',
                           `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                           `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                           PRIMARY KEY (`id`),
                           INDEX `idx_audit_pending` (`status`, `audit_submitted_at`),
                           INDEX `idx_article_question_filter` (`article_type`, `question_status`, `status`, `delete_state`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='帖子表';

-- ----------------------------
-- 3.0 帖子搜索与 RAG / Redis（forum-demo + ai-server；非本脚本创建的 KV/向量结构）
--   已发布帖子仅将「标题 + 检索词扩展」写入向量索引（不含正文），模型 qwen3-vl-embedding；用户所选标签名会并入检索词。
--   用户端「AI 搜索」模式请求 forum-demo /search/article?ai=1 时将跳过标题 LIKE，直接走 RAG 召回链路。
-- ----------------------------


-- ----------------------------
-- 3.0a 帖子标签（预设 + 用户反馈 + 关联，每帖最多 5 个由业务层约束）
-- ----------------------------
DROP TABLE IF EXISTS `forum_article_tag_link`;
DROP TABLE IF EXISTS `forum_article_tag_request`;
DROP TABLE IF EXISTS `forum_article_tag`;
CREATE TABLE `forum_article_tag` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '标签ID',
    `name` varchar(16) NOT NULL COMMENT '标签名',
    `color_key` varchar(24) NOT NULL DEFAULT 'sky' COMMENT 'sky|rose|amber|mint|violet|slate|orange|teal',
    `scope_type` tinyint NOT NULL DEFAULT 0 COMMENT '0全站 1分类 2版块',
    `scope_id` bigint NOT NULL DEFAULT 0 COMMENT '分类或版块ID',
    `sort` int NOT NULL DEFAULT 0,
    `state` tinyint NOT NULL DEFAULT 0 COMMENT '0正常 1禁用',
    `delete_state` tinyint NOT NULL DEFAULT 0,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tag_scope_name` (`scope_type`, `scope_id`, `name`),
    KEY `idx_tag_scope` (`scope_type`, `scope_id`, `delete_state`, `state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='帖子预设标签';

CREATE TABLE `forum_article_tag_link` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `article_id` bigint NOT NULL,
    `tag_id` bigint NOT NULL,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_article_tag` (`article_id`, `tag_id`),
    KEY `idx_tag_article` (`tag_id`, `article_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='帖子标签关联';

CREATE TABLE `forum_article_tag_request` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `user_id` bigint NOT NULL,
    `board_id` bigint NOT NULL,
    `category_id` bigint NOT NULL DEFAULT 0,
    `proposed_name` varchar(16) NOT NULL,
    `status` tinyint NOT NULL DEFAULT 0 COMMENT '0待审 1通过 2拒绝',
    `audit_message` varchar(200) DEFAULT NULL,
    `approved_tag_id` bigint DEFAULT NULL,
    `delete_state` tinyint NOT NULL DEFAULT 0,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_tag_req_user` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户申请新标签';

-- ----------------------------
-- 3.1 帖子相册图片表 (article_image)
-- 帖子正文之外的"小红书式"相册图; 单个帖子最多 15 张, 由业务层 ArticleService 强约束.
-- 同一 image_url 允许在同一 article_id 下重复(用户可能用同图做首尾呼应), 不加唯一键.
-- 业务规则: 保存相册时正文必须 ≥ 10 字, 否则拒绝(详见 ArticleService.replaceArticleImages).
-- 相册图 OSS 路径前缀须与 Java Constant.OSS_PATH_ARTICLE_IMAGE 一致; 提交时 old/new URL 差集删除 OSS 对象、仅增量入库由业务层实现。
-- ----------------------------
DROP TABLE IF EXISTS `article_image`;
CREATE TABLE `article_image` (
                                 `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
                                 `article_id` bigint NOT NULL COMMENT '所属帖子ID',
                                 `image_url` varchar(500) NOT NULL COMMENT '相册图完整URL(前缀须为 OSS 帖子图目录, 见 Constant.OSS_PATH_ARTICLE_IMAGE)',
                                 `sort` int NOT NULL DEFAULT 0 COMMENT '相册内排序, 0 在最前',
                                 `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除: 0否 1是',
                                 `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                 `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                 PRIMARY KEY (`id`),
                                 INDEX `idx_article_id` (`article_id`),
                                 INDEX `idx_article_image_article_del` (`article_id`, `delete_state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='帖子相册图片表';


-- ----------------------------
-- 4. 帖子回复表 (article_reply)
-- ----------------------------
DROP TABLE IF EXISTS `article_reply`;
CREATE TABLE `article_reply` (
                                 `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号, 主键, 自增',
                                 `article_id` bigint NOT NULL COMMENT '关联帖子编号',
                                 `post_user_id` bigint NOT NULL COMMENT '回帖用户编号',
                                 `reply_id` bigint DEFAULT NULL COMMENT '关联回复编号, 支持楼中楼',
                                 `reply_user_id` bigint DEFAULT NULL COMMENT '被回复用户编号',
                                 `content` varchar(500) NOT NULL COMMENT '回帖内容',
                                 `ip_region` varchar(32) DEFAULT NULL COMMENT '评论时IP属地快照',
                                 `like_count` int NOT NULL DEFAULT 0 COMMENT '点赞数',
                                 `state` tinyint NOT NULL DEFAULT 0 COMMENT '状态: 0正常, 1禁用',
                                 `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除: 0否 1是',
                                 `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                 `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                 PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='帖子回复表';

-- ----------------------------
-- 5. 站内信表 (message)
-- 支持多种类型: 0=文本, 1=图片(JPG/PNG), 2=GIF
-- 文本消息: content 必填, media_* 全部为空
-- 图片消息: content 必为空, media_url 必填(指向 OSS 聊天图目录)
-- ----------------------------
DROP TABLE IF EXISTS `message`;
CREATE TABLE `message` (
                           `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号, 主键, 自增',
                           `post_user_id` bigint NOT NULL COMMENT '发送者编号',
                           `receive_user_id` bigint NOT NULL COMMENT '接收者编号',
                           `message_type` tinyint NOT NULL DEFAULT 0 COMMENT '消息类型: 0文本 1图片 2GIF',
                           `content` varchar(500) DEFAULT NULL COMMENT '文本内容; 图片/GIF 消息为空',
                           `media_url` varchar(500) DEFAULT NULL COMMENT '媒体URL(OSS), 图片/GIF 消息必填',
                           `media_mime` varchar(50) DEFAULT NULL COMMENT '媒体MIME(image/jpeg/png/gif)',
                           `media_size` bigint DEFAULT NULL COMMENT '媒体字节大小',
                           `media_width` int DEFAULT NULL COMMENT '媒体像素宽',
                           `media_height` int DEFAULT NULL COMMENT '媒体像素高',
                           `state` tinyint NOT NULL DEFAULT 0 COMMENT '状态: 0未读, 1已读, 2已撤回',
                           `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除: 0否 1是',
                           `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                           `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                           PRIMARY KEY (`id`),
                           INDEX `idx_session_pair` (`post_user_id`, `receive_user_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='站内信表';

-- ----------------------------
-- 6. 帖子点赞记录表 (article_like)
-- ----------------------------
DROP TABLE IF EXISTS `article_like`;
CREATE TABLE `article_like` (
                                `id`         bigint   NOT NULL AUTO_INCREMENT COMMENT '编号, 主键, 自增',
                                `user_id`    bigint   NOT NULL COMMENT '点赞用户编号',
                                `article_id` bigint   NOT NULL COMMENT '被点赞帖子编号',
                                `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
                                PRIMARY KEY (`id`),
    -- 联合唯一索引：从数据库层面保证同一用户对同一帖子只能点赞一次
                                UNIQUE INDEX `uix_user_article` (`user_id`, `article_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='帖子点赞记录表';

-- ----------------------------
-- 7. 楼中楼（二级回复）表（article_sub_reply）
-- ----------------------------
CREATE TABLE `article_sub_reply` (
                                     `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                     `article_id` BIGINT NOT NULL COMMENT '所属帖子ID',
                                     `reply_id` BIGINT NOT NULL COMMENT '所属的一级回复(楼层)ID',
                                     `post_user_id` BIGINT NOT NULL COMMENT '当前发帖的用户ID',
                                     `reply_user_id` BIGINT NOT NULL COMMENT '被回复的目标用户ID (用于显示 @昵称)',
                                     `content` TEXT NOT NULL COMMENT '回复内容',
                                     `ip_region` varchar(32) DEFAULT NULL COMMENT '楼中楼回复时IP属地快照',
                                     `like_count` int NOT NULL DEFAULT 0 COMMENT '点赞数',
                                     `state` TINYINT DEFAULT 0 COMMENT '状态: 0-正常, 1-禁用',
                                     `delete_state` TINYINT DEFAULT 0 COMMENT '是否删除: 0-否, 1-是',
                                     `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                     `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                     PRIMARY KEY (`id`),
                                     INDEX `idx_reply_id` (`reply_id`),
                                     INDEX `idx_article_id` (`article_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='楼中楼回复表';

ALTER TABLE `article_sub_reply`
    MODIFY COLUMN `reply_user_id` BIGINT NULL COMMENT '被回复的目标用户ID (用于显示 @昵称)';

-- ----------------------------
-- 7b. 一级评论点赞记录 (article_reply_like)
-- ----------------------------
DROP TABLE IF EXISTS `article_reply_like`;
CREATE TABLE `article_reply_like` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` bigint NOT NULL COMMENT '点赞用户',
    `reply_id` bigint NOT NULL COMMENT '一级评论ID',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uix_user_reply` (`user_id`, `reply_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='一级评论点赞记录';

-- ----------------------------
-- 7c. 楼中楼回复点赞记录 (article_sub_reply_like)
-- ----------------------------
DROP TABLE IF EXISTS `article_sub_reply_like`;
CREATE TABLE `article_sub_reply_like` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` bigint NOT NULL COMMENT '点赞用户',
    `sub_reply_id` bigint NOT NULL COMMENT '楼中楼回复ID',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uix_user_sub_reply` (`user_id`, `sub_reply_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='楼中楼回复点赞记录';

-- ----------------------------
-- 7d. 评论附件 (article_reply_media)
-- ----------------------------
DROP TABLE IF EXISTS `article_reply_media`;
CREATE TABLE `article_reply_media` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `reply_id` bigint DEFAULT NULL COMMENT '一级评论ID',
    `sub_reply_id` bigint DEFAULT NULL COMMENT '楼中楼评论ID',
    `media_type` tinyint NOT NULL COMMENT '1用户图片 2商城表情',
    `media_url` varchar(500) NOT NULL COMMENT '媒体URL',
    `shop_id` bigint DEFAULT NULL COMMENT '商城表情包ID(仅 type=2)',
    `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_reply_id` (`reply_id`),
    KEY `idx_sub_reply_id` (`sub_reply_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评论图片/表情附件';

-- ----------------------------
-- 7e. 视频帖弹幕 (article_video_danmaku)
-- ----------------------------
DROP TABLE IF EXISTS `article_video_danmaku`;
CREATE TABLE `article_video_danmaku` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `article_id` bigint NOT NULL COMMENT '帖子ID，仅视频帖',
    `user_id` bigint NOT NULL COMMENT '发送用户ID',
    `video_time_ms` int NOT NULL COMMENT '弹幕对应视频时间点(毫秒)',
    `content` varchar(100) NOT NULL COMMENT '弹幕文本',
    `color_code` tinyint NOT NULL DEFAULT 0 COMMENT '预设颜色编码: 0白 1红 2黄 3绿 4蓝 5粉 6橙 7紫 8青',
    `mode` tinyint NOT NULL DEFAULT 0 COMMENT '弹幕模式: 0滚动 1顶部 2底部',
    `font_size` tinyint NOT NULL DEFAULT 1 COMMENT '字号: 0小 1标准',
    `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0正常 1已删除',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_article_time` (`article_id`, `video_time_ms`, `id`),
    KEY `idx_user_article_time` (`user_id`, `article_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='视频帖弹幕';

-- ----------------------------
-- 8. 分类表 (category)
-- ----------------------------
DROP TABLE IF EXISTS `category`;
CREATE TABLE `category` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '分类编号, 主键, 自增',
    `name` varchar(50) NOT NULL COMMENT '分类名称',
    `description` varchar(200) DEFAULT NULL COMMENT '分类描述',
    `sort` int NOT NULL DEFAULT 0 COMMENT '排序优先级',
    `state` tinyint NOT NULL DEFAULT 0 COMMENT '状态: 0正常, 1禁用',
    `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除: 0否 1是',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='分类表';

-- 分类 + 版块（大众化社区结构；全新库执行本脚本后分类 ID=1～8、版块 ID=1～27 与下列顺序一致）
INSERT INTO `category` (`name`, `description`, `sort`) VALUES
('二次元', '番剧动画、同人、COS、手办与宅文化', 1),
('游戏', '手游、单机、电竞与攻略交流', 2),
('生活日常', '穿搭美妆、美食、旅行与居家', 3),
('影视综艺', '电影剧集、综艺与音乐', 4),
('学习职场', '考研考公、职场与自学', 5),
('情感树洞', '倾诉、恋爱与人际', 6),
('科技数码', '手机电脑、装机与 AI 玩机', 7),
('萌宠', '猫狗异宠与养宠日常', 8);

INSERT INTO `board` (`name`, `category_id`, `article_count`, `sort`, `state`, `delete_state`) VALUES
('新番讨论',   1, 0, 1, 0, 0),
('同人安利',   1, 0, 2, 0, 0),
('COS返图',    1, 0, 3, 0, 0),
('手办模玩',   1, 0, 4, 0, 0),
('声优宅舞',   1, 0, 5, 0, 0),
('手游',       2, 0, 1, 0, 0),
('单机主机',   2, 0, 2, 0, 0),
('电竞赛事',   2, 0, 3, 0, 0),
('游戏攻略',   2, 0, 4, 0, 0),
('穿搭美妆',   3, 0, 1, 0, 0),
('美食探店',   3, 0, 2, 0, 0),
('旅行打卡',   3, 0, 3, 0, 0),
('家居收纳',   3, 0, 4, 0, 0),
('影视综评',   4, 0, 1, 0, 0),
('综艺吐槽',   4, 0, 2, 0, 0),
('音乐分享',   4, 0, 3, 0, 0),
('考研考公',   5, 0, 1, 0, 0),
('职场交流',   5, 0, 2, 0, 0),
('自学技能',   5, 0, 3, 0, 0),
('倾诉树洞',   6, 0, 1, 0, 0),
('恋爱八卦',   6, 0, 2, 0, 0),
('手机平板',   7, 0, 1, 0, 0),
('电脑装机',   7, 0, 2, 0, 0),
('AI玩机',     7, 0, 3, 0, 0),
('云吸猫',     8, 0, 1, 0, 0),
('遛狗日记',   8, 0, 2, 0, 0),
('养宠问答',   8, 0, 3, 0, 0);

-- 帖子标签种子（scope_type: 0全站 1分类 2版块）
INSERT INTO `forum_article_tag` (`name`, `color_key`, `scope_type`, `scope_id`, `sort`, `state`, `delete_state`) VALUES
('吐槽', 'rose', 0, 0, 1, 0, 0),
('安利', 'sky', 0, 0, 2, 0, 0),
('求推荐', 'mint', 0, 0, 3, 0, 0),
('晒图', 'amber', 0, 0, 4, 0, 0),
('杂谈', 'slate', 0, 0, 5, 0, 0),
('无剧透', 'mint', 1, 1, 10, 0, 0),
('补番清单', 'sky', 1, 1, 11, 0, 0),
('本子安利', 'violet', 1, 1, 12, 0, 0),
('抽卡晒欧', 'amber', 1, 2, 10, 0, 0),
('攻略', 'teal', 1, 2, 11, 0, 0),
('ootd', 'rose', 1, 3, 10, 0, 0),
('探店', 'orange', 1, 3, 11, 0, 0),
('剧评', 'slate', 1, 4, 10, 0, 0),
('备考', 'violet', 1, 5, 10, 0, 0),
('树洞', 'slate', 1, 6, 10, 0, 0),
('开箱', 'sky', 1, 7, 10, 0, 0),
('云吸猫', 'rose', 1, 8, 10, 0, 0),
('本周新番', 'sky', 2, 1, 1, 0, 0),
('CP向', 'rose', 2, 2, 1, 0, 0),
('场照', 'violet', 2, 3, 1, 0, 0),
('景品', 'amber', 2, 4, 1, 0, 0),
('原神', 'sky', 2, 6, 1, 0, 0),
('Steam', 'slate', 2, 7, 1, 0, 0),
('低卡食谱', 'mint', 2, 11, 1, 0, 0),
('穷游', 'teal', 2, 12, 1, 0, 0),
('考研', 'violet', 2, 17, 1, 0, 0),
('英短', 'rose', 2, 25, 1, 0, 0),
('柯基', 'orange', 2, 26, 1, 0, 0);

-- ----------------------------
-- 9.1 收藏夹表 (user_favorite_folder)
-- 业务约束:
--   * 每个用户拥有自己的多个收藏夹
--   * is_default=1 在同一用户名下最多一条, 由 DB 唯一索引 + ensureDefaultFolder() 双保险
--   * 默认夹不允许被删除, 但允许改名 / 改公开性
--   * default_marker 是 VIRTUAL 生成列: is_default=1 且未删除时 = user_id, 否则 NULL;
--     配合 UNIQUE INDEX 可在 DB 层保证"每用户至多一个未删除的默认夹", 防并发兜底.
-- ----------------------------
DROP TABLE IF EXISTS `user_favorite_folder`;
CREATE TABLE `user_favorite_folder` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号, 主键, 自增',
    `user_id` bigint NOT NULL COMMENT '所属用户编号',
    `name` varchar(50) NOT NULL COMMENT '收藏夹名称',
    `is_public` tinyint NOT NULL DEFAULT 1 COMMENT '公开性: 0私密 1公开',
    `is_default` tinyint NOT NULL DEFAULT 0 COMMENT '是否默认夹: 0否 1是; 默认夹不可删',
    `sort_order` int NOT NULL DEFAULT 0 COMMENT '夹排序(升序), 默认 0',
    `item_count` int NOT NULL DEFAULT 0 COMMENT '夹内帖子数快照, 收藏/取消时维护',
    `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除: 0否 1是',
    `default_marker` bigint GENERATED ALWAYS AS (IF(`is_default` = 1 AND `delete_state` = 0, `user_id`, NULL)) VIRTUAL
        COMMENT '生成列: 仅在默认夹且未删时等于 user_id, 否则 NULL; 由 uix_user_default 锁住每用户最多一个默认夹',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_user_public` (`user_id`, `is_public`),
    UNIQUE INDEX `uix_user_default` (`default_marker`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户收藏夹表';


-- ----------------------------
-- 9.2 帖子收藏记录表 (article_favorite)
-- 业务约束:
--   * (user_id, article_id) 唯一: 同一用户对同一帖子只能收藏一次, 跨夹改归属要走 move 接口
--   * folder_id 必填, 由业务层在保存时回填 (空则落到默认夹)
-- ----------------------------
DROP TABLE IF EXISTS `article_favorite`;
CREATE TABLE `article_favorite` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号, 主键, 自增',
    `user_id` bigint NOT NULL COMMENT '收藏用户编号',
    `folder_id` bigint NOT NULL COMMENT '所属收藏夹ID',
    `article_id` bigint NOT NULL COMMENT '被收藏帖子编号',
    `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除: 0否 1是',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uix_user_article_fav` (`user_id`, `article_id`),
    INDEX `idx_folder_id` (`folder_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='帖子收藏记录表';

-- ----------------------------
-- 10. 签到积分规则表 (checkin_rule)
-- 存放每个月每天的签到积分规则
-- ----------------------------
DROP TABLE IF EXISTS `checkin_rule`;
CREATE TABLE `checkin_rule` (
                                `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号, 主键, 自增',
                                `month` tinyint NOT NULL DEFAULT 0 COMMENT '月份, 0表示默认规则, 1-12表示具体月份',
                                `day_number` tinyint NOT NULL COMMENT '当月第几天, 1-31',
                                `points` int NOT NULL DEFAULT 0 COMMENT '签到获得积分',
                                `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除: 0否 1是',
                                `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                PRIMARY KEY (`id`),
                                UNIQUE INDEX `uix_month_day` (`month`, `day_number`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='签到积分规则表';

-- 兜底规则 month=0（未配置 1~12 月时回退）；可按月插入 1~12 覆盖
INSERT INTO checkin_rule (month, day_number, points) VALUES
(0,1,50),(0,2,50),(0,3,50),(0,4,50),(0,5,50),(0,6,50),(0,7,50),
(0,8,50),(0,9,50),(0,10,50),(0,11,50),(0,12,50),(0,13,50),(0,14,50),
(0,15,50),(0,16,50),(0,17,50),(0,18,50),(0,19,50),(0,20,50),(0,21,50),
(0,22,50),(0,23,50),(0,24,50),(0,25,50),(0,26,50),(0,27,50),(0,28,50),
(0,29,50),(0,30,50),(0,31,50);

-- ----------------------------
-- 11. 连续签到奖励表 (checkin_streak_reward)
-- 连续签到达到指定天数时额外奖励
-- ----------------------------
DROP TABLE IF EXISTS `checkin_streak_reward`;
CREATE TABLE `checkin_streak_reward` (
                                         `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号, 主键, 自增',
                                         `streak_days` int NOT NULL COMMENT '连续签到天数门槛',
                                         `bonus_points` int NOT NULL DEFAULT 0 COMMENT '额外奖励积分',
                                         `description` varchar(100) DEFAULT NULL COMMENT '奖励描述',
                                         `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除: 0否 1是',
                                         `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                         `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                         PRIMARY KEY (`id`),
                                         UNIQUE INDEX `uix_streak_days` (`streak_days`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='连续签到奖励表';

INSERT INTO `checkin_streak_reward` (`streak_days`, `bonus_points`, `description`) VALUES
                                                                                       (7,  50,  '连续签到7天额外奖励'),
                                                                                       (14, 120, '连续签到14天额外奖励'),
                                                                                       (30, 300, '连续签到30天额外奖励');


-- ----------------------------
-- 12. 用户签到状态表 (user_checkin_info)
-- 每个用户一行，记录签到统计状态
-- ----------------------------
DROP TABLE IF EXISTS `user_checkin_info`;
CREATE TABLE `user_checkin_info` (
                                     `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号, 主键, 自增',
                                     `user_id` bigint NOT NULL COMMENT '用户编号',
                                     `total_days` int NOT NULL DEFAULT 0 COMMENT '累计签到天数',
                                     `streak_days` int NOT NULL DEFAULT 0 COMMENT '当前连续签到天数',
                                     `total_points` int NOT NULL DEFAULT 0 COMMENT '签到累计获得积分',
                                     `last_checkin` date DEFAULT NULL COMMENT '最后一次签到日期',
                                     `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除: 0否 1是',
                                     `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                     `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                     PRIMARY KEY (`id`),
                                     UNIQUE INDEX `uix_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户签到状态表';


-- ----------------------------
-- 13. 签到流水表 (checkin_log)
-- 每次签到一条记录，用于历史展示
-- ----------------------------
DROP TABLE IF EXISTS `checkin_log`;
CREATE TABLE `checkin_log` (
                               `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号, 主键, 自增',
                               `user_id` bigint NOT NULL COMMENT '用户编号',
                               `checkin_date` date NOT NULL COMMENT '签到日期',
                               `points` int NOT NULL DEFAULT 0 COMMENT '本次签到获得基础积分',
                               `bonus_points` int NOT NULL DEFAULT 0 COMMENT '本次连续签到额外奖励积分',
                               `streak_days` int NOT NULL DEFAULT 0 COMMENT '签到时的连续天数快照',
                               `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除: 0否 1是',
                               `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                               `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                               PRIMARY KEY (`id`),
                               UNIQUE INDEX `uix_user_date` (`user_id`, `checkin_date`),
                               INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='签到流水表';


-- ----------------------------
-- 14. 用户聊天表情收藏表 (user_chat_emoji)
-- 来源 1: 用户主动上传的图片 (走 /file/uploadChatEmoji, 落到 OSS .../emoji/ 子目录)
-- 来源 2: 把对方在聊天里发过来的图片消息收藏下来 (origin_message_id 非空, media_url 仍指向 .../message/ 子目录, 无需复制 OSS 对象)
-- 同一用户对同一 media_url 只允许存一份, 由 uix_user_url 唯一键兜底
-- ----------------------------
DROP TABLE IF EXISTS `user_chat_emoji`;
CREATE TABLE `user_chat_emoji` (
                                   `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号, 主键, 自增',
                                   `user_id` bigint NOT NULL COMMENT '所属用户编号',
                                   `media_url` varchar(500) NOT NULL COMMENT '表情图URL(OSS)',
                                   `media_type` tinyint NOT NULL DEFAULT 0 COMMENT '类型: 0静态图 1GIF',
                                   `media_mime` varchar(50) DEFAULT NULL COMMENT 'MIME类型',
                                   `media_size` bigint DEFAULT NULL COMMENT '字节大小',
                                   `origin_message_id` bigint DEFAULT NULL COMMENT '来源消息ID(收藏自他人聊天图片时)',
                                   `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除: 0否 1是',
                                   `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                   `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                   PRIMARY KEY (`id`),
                                   UNIQUE INDEX `uix_user_url` (`user_id`, `media_url`),
                                   INDEX `idx_user_create` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户聊天表情收藏表';


-- ----------------------------
-- 15. 表情包商品表 (emoji_shop)
-- upload_user_id IS NULL 表示站长推荐, 非空表示用户上传
-- status: 0待审核(用户上传过 AI 后直接落 1, 该值仅做字段预留) 1上架 2下架
-- ----------------------------
DROP TABLE IF EXISTS `emoji_shop`;
CREATE TABLE `emoji_shop` (
                              `id` bigint NOT NULL AUTO_INCREMENT COMMENT '商品ID',
                              `name` varchar(100) NOT NULL COMMENT '表情包名称',
                              `description` varchar(100) DEFAULT NULL COMMENT '表情包说明(上传者填写, 展示于详情, 最多100字)',
                              `cover_url` varchar(500) NOT NULL COMMENT '封面预览图URL',
                              `price` int NOT NULL DEFAULT 0 COMMENT '售价积分',
                              `upload_user_id` bigint DEFAULT NULL COMMENT '上传者ID, NULL 表示站长推荐',
                              `sales_count` int NOT NULL DEFAULT 0 COMMENT '销售数量',
                              `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态: 0待审核 1上架 2下架',
                              `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除: 0否 1是',
                              `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                              `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                              PRIMARY KEY (`id`),
                              INDEX `idx_upload_user_id` (`upload_user_id`),
                              INDEX `idx_status_sales` (`status`, `sales_count`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='表情包商品表';


-- ----------------------------
-- 16. 表情包图片表 (emoji_item)
-- 业务规则: 单图删/增仅允许在「上架前 或 下架后」操作, 已上架期间禁止改动,
--          以保证已购用户图片可见性稳定. 当前版本只保留字段, 不开放增删图接口.
-- ----------------------------
DROP TABLE IF EXISTS `emoji_item`;
CREATE TABLE `emoji_item` (
                              `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
                              `shop_id` bigint NOT NULL COMMENT '所属商品ID',
                              `image_url` varchar(500) NOT NULL COMMENT '表情图片URL',
                              `sort` int NOT NULL DEFAULT 0 COMMENT '排序(升序), 默认 0',
                              `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除: 0否 1是',
                              `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                              `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                              PRIMARY KEY (`id`),
                              INDEX `idx_shop_id` (`shop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='表情包图片表';


-- ----------------------------
-- 17. 用户已购表情包表 (user_emoji)
-- 同一用户对同一 shop 只能购买一次, 由 uix_user_shop 唯一键兜底.
-- 当前版本不开放退款; delete_state 仅做字段预留, 若用户想"在已购里隐藏"由前端控制视图.
-- ----------------------------
DROP TABLE IF EXISTS `user_emoji`;
CREATE TABLE `user_emoji` (
                              `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
                              `user_id` bigint NOT NULL COMMENT '用户ID',
                              `shop_id` bigint NOT NULL COMMENT '购买的商品ID',
                              `price_paid` int NOT NULL DEFAULT 0 COMMENT '实际支付积分(预留优惠券抵扣)',
                              `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除: 0否 1是',
                              `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '购买时间',
                              `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                              PRIMARY KEY (`id`),
                              UNIQUE INDEX `uix_user_shop` (`user_id`, `shop_id`),
                              INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户已购表情包表';


-- ----------------------------
-- 18. 积分流水表 (points_log)
-- 钱包级流水: 任何 user.points 变动都要在同一事务里 INSERT 一条; 用于前端 ECharts 渲染.
-- source_type: 0签到基础 / 1签到连签奖励 / 2商城购买 / 3退款回补 / 4抽奖消耗 / 5抽奖积分奖励 / 6注册赠送 / 7VIP订阅扣款 / 8抽奖页彩蛋 / 9AI陪伴消耗 / 10AI生图消耗 / 99管理员调整
-- related_id: 关联业务行ID(checkin_log.id / user_emoji.id / lottery_draw_record.id 等), 仅做溯源, 允许空
-- idempotency_key: 一次性积分变动幂等键(非空时 uk_points_user_idempotency 约束); 示例:
--   vip_sub:{userId}:{requestId} / lottery_cost:{userId}:{requestId} / ai_bill:{userId}:{relatedId}
--   game:gobang:win:{roomId} / game:gobang:lose:{roomId} 等
-- delta:      正数=入账, 负数=消费; balance_after=变动后余额
-- ----------------------------
DROP TABLE IF EXISTS `points_log`;
CREATE TABLE `points_log` (
                              `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
                              `user_id` bigint NOT NULL COMMENT '用户ID',
                              `delta` int NOT NULL COMMENT '本次变动量(正数入账, 负数消费)',
                              `balance_after` int NOT NULL COMMENT '变动后余额快照',
                              `source_type` tinyint NOT NULL COMMENT '来源: 0签到基础 1连签奖励 2商城 3退款 4抽奖消耗 5抽奖奖励 6注册 7VIP订阅 8抽奖彩蛋 9AI陪伴 10AI生图 99管理员',
                              `related_id` bigint DEFAULT NULL COMMENT '关联业务行ID(可空)',
                              `idempotency_key` varchar(128) DEFAULT NULL COMMENT '业务幂等键，一次性变动必填',
                              `remark` varchar(200) DEFAULT NULL COMMENT '人类可读描述',
                              `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除: 0否 1是',
                              `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                              `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                              PRIMARY KEY (`id`),
                              UNIQUE KEY `uk_points_user_idempotency` (`user_id`, `idempotency_key`),
                              INDEX `idx_user_time` (`user_id`, `create_time`),
                              INDEX `idx_user_source` (`user_id`, `source_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='积分钱包流水表';


-- ----------------------------
-- 19. 站内系统消息表 (system_message)
-- 与用户私信表 message 物理隔离: 私信表只保留用户间互动, 系统消息(审核结果/公告/封禁通知...)走这张.
-- 前端在站内信界面把两张表 union 渲染, 互不打架, 互不污染索引.
--
-- type 当前已用枚举:
--   1 帖子审核通过
--   2 帖子审核未通过
--   3 帖子审核异常
--   4 注册入站引导(注册成功后推送一条未读系统消息,引导用户前往公告中心查看「入站必看」;
--       建议 title/content 由字典 FORUM_REGISTER_SYSMSG 组装; payload JSON 示例:
--       {"action":"open_notice_center","noticeKind":0,"sidebarKey":"onboarding_welcome"} 由业务约定)
--   99 公告/运营推送等(预留; 可与 forum_notice.id 或外链关联)
--
-- payload 字段: 关联资源 ID 等结构化数据(articleId, retryCount 等), 由消费者塞 JSON 字符串
-- ----------------------------
DROP TABLE IF EXISTS `system_message`;
CREATE TABLE `system_message` (
                                  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号, 主键, 自增',
                                  `receive_user_id` bigint NOT NULL COMMENT '接收者用户ID',
                                  `type` tinyint NOT NULL COMMENT '系统消息类型: 1审核通过 2审核未通过 3审核异常 4注册入站引导 99公告(预留)',
                                  `title` varchar(100) NOT NULL COMMENT '消息标题(展示用)',
                                  `content` varchar(500) NOT NULL COMMENT '消息正文(展示用)',
                                  `related_id` bigint DEFAULT NULL COMMENT '关联业务ID(如审核类: articleId)',
                                  `payload` varchar(1000) DEFAULT NULL COMMENT '附加结构化数据(JSON字符串)',
                                  `state` tinyint NOT NULL DEFAULT 0 COMMENT '状态: 0未读 1已读',
                                  `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除: 0否 1是',
                                  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                  PRIMARY KEY (`id`),
                                  INDEX `idx_user_state` (`receive_user_id`, `state`, `id`),
                                  INDEX `idx_user_time` (`receive_user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统消息表(审核结果/公告等)';


-- ----------------------------
-- 19.1 论坛公告中心内容表 (forum_notice)
-- 管理后台维护、用户端「公告中心」只读展示; 支持多模板( template_id 对应前端布局组件 ).
--
-- notice_kind 公告大类(与产品约定一致):
--   0 新用户入站公告 → 注册后可配合 system_message.type=4 引导查看「入站必看」
--   1 活动公告       → 签到/抽奖/积分活动等
--   2 纪律公告       → 封号/禁言/违规处理说明
--   3 系统更新公告   → 新功能/版本/维护通知
--   4 版规公告       → 发帖规范; category_scope 见下
--
-- category_scope (版规 notice_kind=4 专用语义; 其它类型固定填 0):
--   0     全站通用版规
--   >0   对应 category.id, 表示该「分类」下的补充版规(同一分类下多版块共用一套规则时使用)
--
-- sidebar_key: 用户端侧栏与路由 slug, 同一 (notice_kind, category_scope) 下建议唯一
-- body_json: 模板扩展 JSON（封面图、亮点等）; 正文主体见 content_markdown
-- pin_top: 1 置顶，同 notice_kind + category_scope 下始终排最前
-- sort: 兼容字段，后台写 0；列表按 pin_top、id 倒序
-- ----------------------------
DROP TABLE IF EXISTS `forum_notice`;
CREATE TABLE `forum_notice` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `notice_kind` tinyint NOT NULL COMMENT '0新用户入站 1活动 2纪律 3系统更新 4版规',
    `category_scope` bigint NOT NULL DEFAULT 0 COMMENT '版规时: 0全站通用; 非0=category.id。其它公告类型填0',
    `template_id` varchar(64) NOT NULL COMMENT '前端模板标识,如 welcome_hero_right / plain_sections',
    `sidebar_key` varchar(64) NOT NULL COMMENT '公告中心侧栏 slug,用于定位本篇',
    `title` varchar(200) NOT NULL COMMENT '主标题',
    `subtitle` varchar(500) DEFAULT NULL COMMENT '副标题/摘要',
    `content_markdown` text NOT NULL COMMENT '正文 Markdown，用户端主要阅读区',
    `body_json` json NOT NULL COMMENT '模板扩展(JSON): highlights、coverImageUrl 等',
    `sort` int NOT NULL DEFAULT 0 COMMENT '兼容占位，默认0',
    `pin_top` tinyint NOT NULL DEFAULT 0 COMMENT '1置顶：同类型同分类范围下排最前',
    `publish_state` tinyint NOT NULL DEFAULT 0 COMMENT '0草稿 1已发布',
    `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '0否 1是',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_notice_kind_scope_sidebar` (`notice_kind`, `category_scope`, `sidebar_key`),
    KEY `idx_notice_list` (`notice_kind`, `category_scope`, `publish_state`, `delete_state`, `pin_top`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='论坛公告中心(模板化内容)';

-- 公告中心种子（notice_kind: 0入站 1活动 4规范；注册引导会指向 notice_kind=0）
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
(4, 1, 'plain_sections', 'rules_category_acg', '「二次元」版块说明', '同人、番剧、COS 等内容规范',
 '## 本分类补充\n\n- 转载需注明出处，尊重版权与角色名。\n- COS / 返图请避免无关广告引流。\n- 剧透内容请在标题或开头标注。',
 JSON_OBJECT('sections', JSON_ARRAY()),
 30, 0, 1, 0);


-- ----------------------------
-- 19.2 AI 模型单价目录 (forum_ai_model_price)
-- bill_unit: per_1m_input | per_1m_output | per_image | per_call
-- 积分折算: 1元 = 100积分 (业务层 calcPoints 向上取整)
-- ----------------------------
DROP TABLE IF EXISTS `forum_ai_model_price`;
CREATE TABLE `forum_ai_model_price` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `model_code` varchar(64) NOT NULL COMMENT '模型标识,与埋点一致',
    `provider` varchar(32) NOT NULL DEFAULT 'dashscope' COMMENT 'dashscope|huanapi',
    `bill_unit` varchar(32) NOT NULL COMMENT 'per_1m_input|per_1m_output|per_image|per_call',
    `price_yuan` decimal(12,6) NOT NULL COMMENT '单价(元),按 bill_unit 计量',
    `vip_only` tinyint NOT NULL DEFAULT 0 COMMENT '1=仅VIP可用深度档等',
    `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    `remark` varchar(200) DEFAULT NULL COMMENT '备注',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_model_bill_unit` (`model_code`, `bill_unit`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI模型单价目录';

-- 配置种子：AI 计费单价（非业务演示数据，上线前请按厂商价目维护）
INSERT INTO `forum_ai_model_price` (`model_code`, `provider`, `bill_unit`, `price_yuan`, `vip_only`, `enabled`, `remark`) VALUES
('qwen3.6-flash', 'dashscope', 'per_1m_input', 1.200000, 0, 1, '中国内地'),
('qwen3.6-flash', 'dashscope', 'per_1m_output', 7.200000, 0, 1, '中国内地'),
('qwen3.7-max', 'dashscope', 'per_1m_input', 9.000000, 1, 1, '中国内地<=128K'),
('qwen3.7-max', 'dashscope', 'per_1m_output', 54.000000, 1, 1, '中国内地<=128K'),
('qwen3-vl-flash', 'dashscope', 'per_1m_input', 0.150000, 0, 1, '视觉审核'),
('qwen3-vl-flash', 'dashscope', 'per_1m_output', 1.500000, 0, 1, '视觉审核'),
('qwen3-vl-plus', 'dashscope', 'per_1m_input', 1.000000, 0, 1, '视觉兜底'),
('qwen3-vl-plus', 'dashscope', 'per_1m_output', 10.000000, 0, 1, '视觉兜底'),
('tongyi-embedding-vision-flash', 'dashscope', 'per_1m_input', 0.150000, 0, 1, 'RAG向量'),
('z-image-turbo', 'dashscope', 'per_image', 0.100000, 0, 1, 'prompt_extend=false'),
('wanx2.1-t2i-plus', 'dashscope', 'per_image', 0.100000, 1, 1, '通义万相进阶生图(兜底)'),
('gpt-image-2', 'huanapi', 'per_image', 0.200000, 1, 1, 'GPT Image 进阶生图');

-- ----------------------------
-- 19.3 AI 调用明细 (forum_ai_usage_log)
-- related_id: 会话/业务关联 ID；与 (user_id, feature_code) 组成计费幂等键（AiPointsBillingService）
-- ----------------------------
DROP TABLE IF EXISTS `forum_ai_usage_log`;
CREATE TABLE `forum_ai_usage_log` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` bigint NOT NULL COMMENT '用户ID',
    `feature_code` varchar(64) NOT NULL COMMENT 'companion_writing|companion_help|companion_image|ai_write等',
    `model_code` varchar(64) NOT NULL COMMENT '实际模型',
    `input_tokens` int NOT NULL DEFAULT 0 COMMENT '输入token',
    `output_tokens` int NOT NULL DEFAULT 0 COMMENT '输出token',
    `image_count` int NOT NULL DEFAULT 0 COMMENT '图片张数',
    `points_cost` int NOT NULL DEFAULT 0 COMMENT '扣除积分',
    `estimated` tinyint NOT NULL DEFAULT 0 COMMENT '1=用量为估算',
    `related_id` varchar(64) DEFAULT NULL COMMENT '会话或业务关联ID(有值时参与计费幂等)',
    `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '0否 1是',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ai_usage_user_feature_related` (`user_id`, `feature_code`, `related_id`),
    KEY `idx_ai_usage_log_user_time` (`user_id`, `create_time`),
    KEY `idx_ai_usage_log_feature` (`feature_code`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI调用积分明细';

-- ----------------------------
-- 19.3.1 AI 调用预记录 (forum_ai_call_record)
-- 调用前 PENDING，结算后 SUCCESS/FAILED/TIMEOUT/STOPPED/DISCONNECTED
-- ----------------------------
DROP TABLE IF EXISTS `forum_ai_call_record`;
CREATE TABLE `forum_ai_call_record` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` bigint NOT NULL COMMENT '用户ID',
    `feature_code` varchar(64) NOT NULL COMMENT '功能编码',
    `client_request_id` varchar(64) NOT NULL COMMENT '客户端幂等键',
    `model_code` varchar(64) DEFAULT NULL COMMENT '计划调用模型',
    `call_state` tinyint NOT NULL DEFAULT 0 COMMENT '0待调用 1成功 2失败 3超时 4停止 5断开',
    `estimated_points` int NOT NULL DEFAULT 0 COMMENT '预估积分',
    `points_charged` int NOT NULL DEFAULT 0 COMMENT '实际扣除积分',
    `input_tokens` int NOT NULL DEFAULT 0 COMMENT '输入token',
    `output_tokens` int NOT NULL DEFAULT 0 COMMENT '输出token',
    `error_summary` varchar(200) DEFAULT NULL COMMENT '失败摘要(脱敏)',
    `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '0否 1是',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ai_call_user_feature_request` (`user_id`, `feature_code`, `client_request_id`),
    KEY `idx_ai_call_user_time` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI调用预记录表';

-- ----------------------------
-- 19.3.2 MQ 本地消息表 (forum_outbox_message)
-- ----------------------------
DROP TABLE IF EXISTS `forum_outbox_message`;
CREATE TABLE `forum_outbox_message` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `event_id` varchar(128) NOT NULL COMMENT '业务事件ID',
    `routing_key` varchar(64) NOT NULL COMMENT 'RabbitMQ routing key',
    `payload_json` mediumtext NOT NULL COMMENT '消息体 JSON',
    `message_state` tinyint NOT NULL DEFAULT 0 COMMENT '0待投递 1已投递 2已消费 3失败 4死信',
    `retry_count` int NOT NULL DEFAULT 0 COMMENT '重试次数',
    `last_error` varchar(200) DEFAULT NULL COMMENT '最近错误摘要',
    `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '0否 1是',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_outbox_event_id` (`event_id`),
    KEY `idx_outbox_state_time` (`message_state`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='MQ本地消息表';

-- ----------------------------
-- 19.4 AI 模型调用按日汇总 (forum_ai_model_usage_daily)
-- ----------------------------
DROP TABLE IF EXISTS `forum_ai_model_usage_daily`;
CREATE TABLE `forum_ai_model_usage_daily` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `stat_date` date NOT NULL COMMENT '统计日(东八区日历日)',
    `model_code` varchar(64) NOT NULL COMMENT '模型标识',
    `call_count` int NOT NULL DEFAULT 0 COMMENT '当日调用次数',
    `points_spent` bigint NOT NULL DEFAULT 0 COMMENT '当日消耗积分合计',
    `input_tokens` bigint NOT NULL DEFAULT 0 COMMENT '输入token合计',
    `output_tokens` bigint NOT NULL DEFAULT 0 COMMENT '输出token合计',
    `image_count` int NOT NULL DEFAULT 0 COMMENT '生图张数合计',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ai_usage_day_model` (`stat_date`, `model_code`),
    KEY `idx_ai_usage_stat_date` (`stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI模型调用按日汇总';

-- ----------------------------
-- 19.5 陪伴助手会话与消息（按功能独立会话，MySQL 持久化；与 ai-server Postgres LangGraph 审核库分离）
-- ----------------------------
DROP TABLE IF EXISTS `forum_companion_message`;
DROP TABLE IF EXISTS `forum_companion_session`;
CREATE TABLE `forum_companion_session` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '会话ID',
    `user_id` bigint NOT NULL COMMENT '用户ID',
    `skill` varchar(32) NOT NULL COMMENT 'writing|help|drawing|reading',
    `title` varchar(120) DEFAULT NULL COMMENT '会话标题(首条用户消息摘要)',
    `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '0否 1是',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后活跃',
    PRIMARY KEY (`id`),
    KEY `idx_companion_sess_user_skill` (`user_id`, `skill`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='陪伴助手会话';

CREATE TABLE `forum_companion_message` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '消息ID',
    `session_id` bigint NOT NULL COMMENT '会话ID',
    `role` varchar(16) NOT NULL COMMENT 'user|assistant',
    `content` text COMMENT '文本内容',
    `msg_type` varchar(16) NOT NULL DEFAULT 'text' COMMENT 'text|image',
    `image_url` varchar(1024) DEFAULT NULL COMMENT '生图URL(OSS)；text消息时可存联网检索配图',
    `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '0否 1是',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_companion_msg_session` (`session_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='陪伴助手消息';


-- ----------------------------
-- 20. 抽奖奖品表 (lottery_prize)
-- prize_type: 0谢谢参与 / 1大奖(单活动仅一档) / 2小奖 / 3安慰奖 / 4积分奖(prize_value=积分,单档≤100) / 5VIP体验天(仅记录)
-- catalog_status: 0草稿 1上架(可被活动选用) 2下架
-- is_mystery_bundle: 1 且 prize_type=1 时, 开奖从 lottery_prize_mystery_item 加权抽取子项(VIP天/积分)
-- image_path: 相对 OSS 路径, 命名建议 forum_db_item/forum_prize_picture/{活动ID}_{奖品ID}_{yyyyMMddHHmmss}.ext (活动侧可覆盖到 lottery_activity_prize.image_path)
-- ----------------------------
DROP TABLE IF EXISTS `lottery_draw_hourly_stat`;
DROP TABLE IF EXISTS `lottery_draw_record`;
DROP TABLE IF EXISTS `lottery_draw_request`;
DROP TABLE IF EXISTS `lottery_activity_prize`;
DROP TABLE IF EXISTS `lottery_activity`;
DROP TABLE IF EXISTS `lottery_prize_mystery_item`;
DROP TABLE IF EXISTS `lottery_prize`;
CREATE TABLE `lottery_prize` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '奖品ID',
    `name` varchar(100) NOT NULL COMMENT '奖品名称',
    `prize_type` tinyint NOT NULL COMMENT '0谢谢 1大奖 2小奖 3安慰奖 4积分 5VIP天',
    `prize_value` int NOT NULL DEFAULT 0 COMMENT '积分额或VIP天数,其它为0',
    `stock_quantity` int NOT NULL DEFAULT -1 COMMENT '奖品库库存,-1表示不限量',
    `catalog_status` tinyint NOT NULL DEFAULT 1 COMMENT '0草稿 1上架 2下架',
    `is_mystery_bundle` tinyint NOT NULL DEFAULT 0 COMMENT '1=神秘大奖(多维子项开奖)',
    `image_path` varchar(512) DEFAULT NULL COMMENT '奖品图相对路径 forum_db_item/forum_prize_picture/...',
    `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除: 0否 1是',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_lottery_prize_cat` (`catalog_status`, `delete_state`, `prize_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='抽奖奖品表(奖品库+活动引用)';


-- ----------------------------
-- 20.1 神秘大奖子项(仅 is_mystery_bundle=1 且 prize_type=1 时使用)
-- item_type: 4积分 / 5VIP天; item_value: 积分数或天数; 单项积分须 ≤100
-- ----------------------------
CREATE TABLE `lottery_prize_mystery_item` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `prize_id` bigint NOT NULL COMMENT '父奖品 lottery_prize.id',
    `item_type` tinyint NOT NULL COMMENT '4积分 5VIP天',
    `item_value` int NOT NULL COMMENT '积分数或VIP天数',
    `weight` int NOT NULL DEFAULT 1 COMMENT '开奖权重',
    `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '0否 1是',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_mystery_prize` (`prize_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='神秘大奖子奖项';


-- ----------------------------
-- 21. 抽奖活动表 (lottery_activity)
-- status: 1对用户端展示 0关闭; phase: 0筹划中 1进行中 2已截止; delete_state 软删
-- publisher_id: 创建活动的管理员 user.id
-- cover_image_url: 建议 forum_db_item/forum_activity_picture/{活动ID}_{publisherId}_{yyyyMMddHHmmss}.ext
-- ----------------------------
CREATE TABLE `lottery_activity` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '活动ID',
    `title` varchar(100) NOT NULL COMMENT '活动标题',
    `description` varchar(2000) DEFAULT NULL COMMENT '活动说明(可较长)',
    `cover_image_url` varchar(512) DEFAULT NULL COMMENT '活动封面相对路径(OSS)',
    `publisher_id` bigint DEFAULT NULL COMMENT '创建人(管理员)用户ID',
    `cost_points_per_draw` int NOT NULL DEFAULT 30 COMMENT '单次抽奖消耗积分',
    `status` tinyint NOT NULL DEFAULT 1 COMMENT '1对用户端开放 0关闭',
    `phase` tinyint NOT NULL DEFAULT 0 COMMENT '0筹划中 1进行中 2已截止',
    `start_time` datetime DEFAULT NULL COMMENT '开始时间(可空)',
    `end_time` datetime DEFAULT NULL COMMENT '结束时间(可空)',
    `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除: 0否 1是',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_lottery_act_phase` (`phase`, `delete_state`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='抽奖活动表';


-- ----------------------------
-- 22. 活动奖品关联表 (lottery_activity_prize)
-- image_path: 可覆盖奖品库默认图, 命名建议 forum_db_item/forum_prize_picture/{活动ID}_{奖品ID}_{yyyyMMddHHmmss}.ext
-- ----------------------------
CREATE TABLE `lottery_activity_prize` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '关联ID',
    `activity_id` bigint NOT NULL COMMENT '活动ID',
    `prize_id` bigint NOT NULL COMMENT '奖品ID',
    `weight` int NOT NULL DEFAULT 1 COMMENT '权重',
    `stock_remaining` int NOT NULL DEFAULT -1 COMMENT '剩余库存,-1不限量',
    `is_jackpot` tinyint NOT NULL DEFAULT 0 COMMENT '是否头奖(大奖动效)',
    `image_path` varchar(512) DEFAULT NULL COMMENT '本活动该行奖品图(可空则回落 lottery_prize.image_path)',
    `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除: 0否 1是',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_activity_id` (`activity_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='活动奖品关联表';


-- ----------------------------
-- 22.9 抽奖请求幂等表 (lottery_draw_request)
-- 客户端 requestId 重试时返回同批次结果，不重复扣积分
-- ----------------------------
CREATE TABLE `lottery_draw_request` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` bigint NOT NULL COMMENT '用户ID',
    `activity_id` bigint NOT NULL COMMENT '活动ID',
    `request_id` varchar(64) NOT NULL COMMENT '客户端幂等键',
    `times` int NOT NULL COMMENT '抽奖次数 1或10',
    `batch_key` varchar(40) DEFAULT NULL COMMENT '批次键，关联 lottery_draw_record.draw_batch_key',
    `pity_after` int DEFAULT NULL COMMENT '批次结束后的硬保底计数',
    `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除: 0否 1是',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_lottery_user_request` (`user_id`, `request_id`),
    KEY `idx_lottery_request_batch` (`batch_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='抽奖请求幂等表';

-- ----------------------------
-- 23. 抽奖记录表 (lottery_draw_record)
-- mystery_item_*: 神秘大奖开奖后实际子项快照; 非神秘时为 NULL
-- draw_batch_key: 与 lottery_draw_request.batch_key / request_id 对应
-- ----------------------------
CREATE TABLE `lottery_draw_record` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `user_id` bigint NOT NULL COMMENT '用户ID',
    `activity_id` bigint NOT NULL COMMENT '活动ID',
    `activity_prize_id` bigint NOT NULL COMMENT '活动奖品关联ID',
    `prize_id` bigint NOT NULL COMMENT '奖品ID',
    `prize_name` varchar(100) NOT NULL COMMENT '奖品名称快照',
    `prize_type` tinyint NOT NULL COMMENT '奖品类型快照',
    `prize_value` int NOT NULL DEFAULT 0 COMMENT '奖品数值快照',
    `grant_points` int NOT NULL DEFAULT 0 COMMENT '本次发放积分(仅积分奖)',
    `is_jackpot` tinyint NOT NULL DEFAULT 0 COMMENT '是否头奖快照',
    `mystery_item_type` tinyint DEFAULT NULL COMMENT '神秘子项类型 4积分/5VIP天',
    `mystery_item_value` int DEFAULT NULL COMMENT '神秘子项数值',
    `draw_batch_key` varchar(40) DEFAULT NULL COMMENT '十连批次UUID',
    `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除: 0否 1是',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_time` (`user_id`, `create_time`),
    KEY `idx_activity_id` (`activity_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='抽奖记录表';


-- ----------------------------
-- 23.1 抽奖按小时汇总(每次抽奖写入 UPSERT)
-- ----------------------------
CREATE TABLE `lottery_draw_hourly_stat` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `activity_id` bigint NOT NULL COMMENT '活动ID',
    `stat_hour` datetime NOT NULL COMMENT '东八区整点(如 2026-05-12 14:00:00)',
    `draw_count` int NOT NULL DEFAULT 0 COMMENT '该小时抽奖次数(单抽+十连每抽计1)',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_lottery_act_hour` (`activity_id`, `stat_hour`),
    KEY `idx_lottery_hour_act` (`activity_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='抽奖活动按小时参与次数';


-- ----------------------------
-- 抽奖演示数据（phase=1 进行中，供用户端 / 首页趋势）
-- ----------------------------
INSERT INTO `lottery_prize` (`id`, `name`, `prize_type`, `prize_value`, `stock_quantity`, `catalog_status`, `is_mystery_bundle`, `image_path`) VALUES
    (1, '谢谢参与', 0, 0, -1, 1, 0, NULL),
    (2, '神秘大奖', 1, 0, -1, 1, 1, NULL),
    (3, '周边小礼品A', 2, 0, -1, 1, 0, NULL),
    (4, '安慰奖', 3, 0, -1, 1, 0, NULL),
    (5, '10积分', 4, 10, -1, 1, 0, NULL),
    (6, '50积分', 4, 50, -1, 1, 0, NULL),
    (7, 'VIP体验1天', 5, 1, -1, 1, 0, NULL),
    (8, 'VIP体验3天', 5, 3, -1, 1, 0, NULL);

INSERT INTO `lottery_prize_mystery_item` (`id`, `prize_id`, `item_type`, `item_value`, `weight`) VALUES
    (1, 2, 5, 4, 1),
    (2, 2, 4, 100, 1),
    (3, 2, 4, 80, 1);

INSERT INTO `lottery_activity` (`id`, `title`, `description`, `cover_image_url`, `publisher_id`, `cost_points_per_draw`, `status`, `phase`, `start_time`, `end_time`, `delete_state`)
VALUES (1, '积分幸运抽',
        '单次消耗积分参与抽奖；积分奖即时到账，其它奖品以站内通知为准。概率按活动权重动态计算（售罄档位自动剔除并重算）。十连 Soft：至少 1 件稀有档（大奖/周边/VIP）；累计 50 抽未出神秘大奖则下一次必出神秘大奖档。',
        NULL, NULL, 30, 1, 1, NULL, NULL, 0);

INSERT INTO `lottery_activity_prize` (`id`, `activity_id`, `prize_id`, `weight`, `stock_remaining`, `is_jackpot`, `image_path`) VALUES
    (1, 1, 1, 5000, -1, 0, NULL),
    (2, 1, 2, 20, 1, 1, NULL),
    (3, 1, 3, 500, 200, 0, NULL),
    (4, 1, 4, 2000, -1, 0, NULL),
    (5, 1, 5, 1500, -1, 0, NULL),
    (6, 1, 6, 800, 800, 0, NULL),
    (7, 1, 7, 180, 150, 0, NULL);

-- ----------------------------
-- 23.2 游戏定义表 (game_definition)
-- ----------------------------
DROP TABLE IF EXISTS `game_definition`;
CREATE TABLE `game_definition` (
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
VALUES
    ('gobang', '五子棋', NULL, 1, 10, 0),
    ('jinzi', '井字棋', NULL, 1, 20, 0),
    ('tetris', '俄罗斯方块', NULL, 1, 30, 0),
    ('tetris_pk', '俄罗斯方块PK', NULL, 1, 31, 0);

-- ----------------------------
-- 23.3 游戏用户资料表 (game_user_profile)
-- ----------------------------
DROP TABLE IF EXISTS `game_user_profile`;
CREATE TABLE `game_user_profile` (
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

-- ----------------------------
-- 23.4 五子棋对局记录表 (game_gobang_match_record)
-- ----------------------------
DROP TABLE IF EXISTS `game_gobang_match_record`;
CREATE TABLE `game_gobang_match_record` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `room_id` varchar(64) NOT NULL COMMENT '房间ID',
    `black_user_id` bigint NOT NULL COMMENT '黑方用户ID',
    `white_user_id` bigint NOT NULL COMMENT '白方用户ID',
    `winner_user_id` bigint DEFAULT NULL COMMENT '胜方用户ID',
    `loser_user_id` bigint DEFAULT NULL COMMENT '负方用户ID',
    `end_reason` varchar(32) NOT NULL COMMENT '结束原因 FIVE/SURRENDER/DISCONNECT/TIMEOUT/ABNORMAL',
    `score_delta` int NOT NULL DEFAULT 10 COMMENT '本局胜负积分变化绝对值',
    `winner_score_delta` int NOT NULL DEFAULT 0 COMMENT '胜方本局排位分变化',
    `loser_score_delta` int NOT NULL DEFAULT 0 COMMENT '败方本局排位分变化，负数',
    `started_at` datetime NOT NULL COMMENT '对局开始时间',
    `ended_at` datetime NOT NULL COMMENT '对局结束时间',
    `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除: 0否 1是',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_gobang_room_record` (`room_id`),
    KEY `idx_gobang_black_time` (`black_user_id`, `ended_at`),
    KEY `idx_gobang_white_time` (`white_user_id`, `ended_at`),
    KEY `idx_gobang_winner_time` (`winner_user_id`, `ended_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='五子棋对局记录表';

-- ----------------------------
-- 23.4.1 井字棋对局记录表 (game_jinzi_match_record)
-- ----------------------------
DROP TABLE IF EXISTS `game_jinzi_match_record`;
CREATE TABLE `game_jinzi_match_record` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `room_id` varchar(64) NOT NULL COMMENT '房间ID',
    `black_user_id` bigint NOT NULL COMMENT '黑方用户ID',
    `white_user_id` bigint NOT NULL COMMENT '白方用户ID',
    `winner_user_id` bigint DEFAULT NULL COMMENT '胜方用户ID',
    `loser_user_id` bigint DEFAULT NULL COMMENT '负方用户ID',
    `end_reason` varchar(32) NOT NULL COMMENT '结束原因 THREE/SURRENDER/DISCONNECT/TIMEOUT/ABNORMAL/DRAW',
    `score_delta` int NOT NULL DEFAULT 10 COMMENT '本局胜负积分变化绝对值',
    `winner_score_delta` int NOT NULL DEFAULT 0 COMMENT '胜方本局排位分变化',
    `loser_score_delta` int NOT NULL DEFAULT 0 COMMENT '败方本局排位分变化，负数',
    `started_at` datetime NOT NULL COMMENT '对局开始时间',
    `ended_at` datetime NOT NULL COMMENT '对局结束时间',
    `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除: 0否 1是',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_jinzi_room_record` (`room_id`),
    KEY `idx_jinzi_black_time` (`black_user_id`, `ended_at`),
    KEY `idx_jinzi_white_time` (`white_user_id`, `ended_at`),
    KEY `idx_jinzi_winner_time` (`winner_user_id`, `ended_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='井字棋对局记录表';

-- ----------------------------
-- 23.5 游戏结算事件表 (game_settlement_event)
-- ----------------------------
DROP TABLE IF EXISTS `game_settlement_event`;
CREATE TABLE `game_settlement_event` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '事件ID',
    `event_id` varchar(64) NOT NULL COMMENT '事件唯一ID',
    `game_code` varchar(64) NOT NULL COMMENT '游戏编码',
    `room_id` varchar(64) NOT NULL COMMENT '房间ID',
    `event_type` varchar(64) NOT NULL COMMENT '事件类型，如 GAME_FINISHED',
    `record_id` bigint DEFAULT NULL COMMENT '关联对局记录ID',
    `status` varchar(32) NOT NULL DEFAULT 'CREATED' COMMENT '事件状态 CREATED/MQ_SENT/MQ_PENDING/CONSUMED/DEAD',
    `retry_count` int NOT NULL DEFAULT 0 COMMENT '重试次数',
    `last_error` varchar(512) DEFAULT NULL COMMENT '最近一次错误摘要',
    `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除: 0否 1是',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_game_event_id` (`event_id`),
    UNIQUE KEY `uk_game_room_event` (`game_code`, `room_id`, `event_type`),
    KEY `idx_game_event_status` (`game_code`, `status`, `delete_state`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='游戏结算事件表';

-- ----------------------------
-- 23.6 游戏房间玩家映射表 (game_room_player)
-- ----------------------------
DROP TABLE IF EXISTS `game_room_player`;
CREATE TABLE `game_room_player` (
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

-- ----------------------------
-- 23.7 五子棋房间落子记录表 (game_gobang_room_move)
-- ----------------------------
DROP TABLE IF EXISTS `game_gobang_room_move`;
CREATE TABLE `game_gobang_room_move` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录ID',
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
    UNIQUE KEY `uk_gobang_room_move_no` (`room_id`, `move_no`),
    KEY `idx_gobang_room_moves` (`room_id`, `delete_state`, `move_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='五子棋房间落子记录表';

-- ----------------------------
-- 23.7.1 井字棋房间落子记录表 (game_jinzi_room_move)
-- ----------------------------
DROP TABLE IF EXISTS `game_jinzi_room_move`;
CREATE TABLE `game_jinzi_room_move` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录ID',
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
    UNIQUE KEY `uk_jinzi_room_move_no` (`room_id`, `move_no`),
    KEY `idx_jinzi_room_moves` (`room_id`, `delete_state`, `move_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='井字棋房间落子记录表';

-- ----------------------------
-- 23.7.2 俄罗斯方块单人局记录表 (game_tetris_record)
-- ----------------------------
DROP TABLE IF EXISTS `game_tetris_record`;
CREATE TABLE `game_tetris_record` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `user_id` bigint NOT NULL COMMENT '用户ID',
    `game_code` varchar(64) NOT NULL DEFAULT 'tetris' COMMENT '游戏编码',
    `score` int NOT NULL DEFAULT 0 COMMENT '本局分数',
    `level` int NOT NULL DEFAULT 1 COMMENT '结束时等级',
    `lines_cleared` int NOT NULL DEFAULT 0 COMMENT '总消行数',
    `duration_ms` bigint NOT NULL DEFAULT 0 COMMENT '局时长毫秒',
    `seed` bigint NOT NULL COMMENT '随机种子',
    `replay_payload` mediumtext NOT NULL COMMENT '回放JSON',
    `forum_points_awarded` int NOT NULL DEFAULT 0 COMMENT '本次论坛积分奖励',
    `validation_status` varchar(16) NOT NULL DEFAULT 'VALID' COMMENT '校验状态 VALID/REJECTED',
    `started_at` datetime NOT NULL COMMENT '开局时间',
    `ended_at` datetime NOT NULL COMMENT '结束时间',
    `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除: 0否 1是',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tetris_user_time` (`user_id`, `delete_state`, `ended_at`),
    KEY `idx_tetris_score` (`game_code`, `delete_state`, `score`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='俄罗斯方块单人局记录';

-- ----------------------------
-- 23.7.3 俄罗斯方块 PK 对局记录表 (game_tetris_pk_match_record)
-- ----------------------------
DROP TABLE IF EXISTS `game_tetris_pk_match_record`;
CREATE TABLE `game_tetris_pk_match_record` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `room_id` varchar(64) NOT NULL COMMENT '房间ID',
    `player1_user_id` bigint NOT NULL COMMENT '玩家1用户ID',
    `player2_user_id` bigint NOT NULL COMMENT '玩家2用户ID',
    `red_user_id` bigint NOT NULL COMMENT '红方用户ID',
    `blue_user_id` bigint NOT NULL COMMENT '蓝方用户ID',
    `winner_user_id` bigint DEFAULT NULL COMMENT '胜方用户ID',
    `loser_user_id` bigint DEFAULT NULL COMMENT '败方用户ID',
    `player1_score` int NOT NULL DEFAULT 0 COMMENT '玩家1得分',
    `player2_score` int NOT NULL DEFAULT 0 COMMENT '玩家2得分',
    `end_reason` varchar(32) NOT NULL DEFAULT '' COMMENT '结束原因',
    `score_delta` int NOT NULL DEFAULT 3 COMMENT '积分变动',
    `winner_score_delta` int NOT NULL DEFAULT 0 COMMENT '胜方本局排位分变化',
    `loser_score_delta` int NOT NULL DEFAULT 0 COMMENT '败方本局排位分变化，负数',
    `replay_payload` mediumtext COMMENT '回放JSON',
    `started_at` datetime NOT NULL COMMENT '开始时间',
    `ended_at` datetime NOT NULL COMMENT '结束时间',
    `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除: 0否 1是',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tetris_pk_room` (`room_id`),
    KEY `idx_tetris_pk_player1` (`player1_user_id`, `delete_state`, `ended_at`),
    KEY `idx_tetris_pk_player2` (`player2_user_id`, `delete_state`, `ended_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='俄罗斯方块PK对局记录';


-- ----------------------------
-- 24. AI 每日用量表 (ai_usage_daily)
-- 按用户 + 自然日(建议 Asia/Shanghai)滚动; 推荐配图要点单独计数不计入 qwen_flash 写作配额.
-- qwen_flash_used: 普通用户档位累计至 10 上限; PRO/MAX 使用 Qwen 深度档配额.
-- advanced_llm_used / image_* / companion_* : 按 VIP 矩阵限额在业务层校验.
-- ----------------------------
DROP TABLE IF EXISTS `ai_usage_daily`;
CREATE TABLE `ai_usage_daily` (
                                  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
                                  `user_id` bigint NOT NULL COMMENT '用户ID',
                                  `usage_date` date NOT NULL COMMENT '用量归属日',
                                  `qwen_flash_used` int NOT NULL DEFAULT 0 COMMENT 'Qwen Flash写作已用次数(普通用户上限10)',
                                  `advanced_llm_used` int NOT NULL DEFAULT 0 COMMENT '高级大模型写作已用次数',
                                  `image_normal_used` int NOT NULL DEFAULT 0 COMMENT 'AI生图普通档已用次数',
                                  `image_premium_used` int NOT NULL DEFAULT 0 COMMENT 'AI生图高级档已用次数',
                                  `companion_normal_used` int NOT NULL DEFAULT 0 COMMENT 'AI伴读普通档已用(预留)',
                                  `companion_premium_used` int NOT NULL DEFAULT 0 COMMENT 'AI伴读高级档已用(预留)',
                                  `cover_hint_used` int NOT NULL DEFAULT 0 COMMENT '封面「推荐配图要点」调用次数(不计入写作配额,仅审计)',
                                  `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除: 0否 1是',
                                  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                  PRIMARY KEY (`id`),
                                  UNIQUE INDEX `uix_user_usage_date` (`user_id`, `usage_date`),
                                  INDEX `idx_usage_date` (`usage_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI能力每日用量';


-- ----------------------------
-- 24.1 VIP 会员中心配额展示配置 (forum_vip_quota_config)
-- quota_type: unlimited=不限次 daily_count=按日次数(对应 ai_usage_daily 字段) token_period=订阅周期内 Token
-- daily_bucket: qwen_flash | advanced_llm | image_normal | image_premium
-- ----------------------------
DROP TABLE IF EXISTS `forum_vip_quota_config`;
CREATE TABLE `forum_vip_quota_config` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `vip_tier` tinyint NOT NULL COMMENT '1=PRO 2=MAX',
    `quota_key` varchar(64) NOT NULL COMMENT '配置键',
    `group_label` varchar(64) NOT NULL COMMENT '分组标题',
    `display_name` varchar(100) NOT NULL COMMENT '展示名称',
    `quota_type` varchar(32) NOT NULL COMMENT 'unlimited|daily_count|token_period',
    `daily_bucket` varchar(32) DEFAULT NULL COMMENT '日配额桶',
    `model_code` varchar(64) DEFAULT NULL COMMENT 'token_period 时按模型汇总 forum_ai_usage_log',
    `icon_provider` varchar(32) DEFAULT NULL COMMENT 'qwen|openai|huanapi',
    `daily_limit` int DEFAULT NULL COMMENT '日次数上限',
    `token_limit` bigint DEFAULT NULL COMMENT '周期 Token 上限',
    `tier_tag` varchar(16) DEFAULT NULL COMMENT 'PRO|MAX|免费 角标',
    `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序',
    `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '1启用',
    PRIMARY KEY (`id`),
    KEY `idx_vip_quota_tier_sort` (`vip_tier`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='VIP配额展示配置';

INSERT INTO `forum_vip_quota_config`
(`vip_tier`, `quota_key`, `group_label`, `display_name`, `quota_type`, `daily_bucket`, `model_code`, `icon_provider`, `daily_limit`, `token_limit`, `tier_tag`, `sort_order`) VALUES
(1, 'image_normal', 'AI 生图 · 每日', 'Z-Image Turbo（普通）', 'daily_count', 'image_normal', 'z-image-turbo', 'qwen', 15, NULL, 'PRO', 30),
(1, 'image_premium', 'AI 生图 · 每日', 'GPT Image 2（进阶）', 'daily_count', 'image_premium', 'gpt-image-2', 'openai', 10, NULL, 'PRO', 40),
(1, 'token_qwen_deep', '本期 Token 配额 · 文本', '通义千问 · 深度', 'token_period', NULL, 'qwen3.7-max', 'qwen', NULL, 500000, 'PRO', 50),
(2, 'image_normal', 'AI 生图 · 每日', 'Z-Image Turbo（普通）', 'daily_count', 'image_normal', 'z-image-turbo', 'qwen', 50, NULL, 'MAX', 30),
(2, 'image_premium', 'AI 生图 · 每日', 'GPT Image 2（进阶）', 'daily_count', 'image_premium', 'gpt-image-2', 'openai', 50, NULL, 'MAX', 40),
(2, 'token_qwen_deep', '本期 Token 配额 · 文本', '通义千问 · 深度', 'token_period', NULL, 'qwen3.7-max', 'qwen', NULL, 2000000, 'MAX', 50);

-- ----------------------------
-- 25. 群聊模块
-- ----------------------------
DROP TABLE IF EXISTS `group_chat_join_request`;
DROP TABLE IF EXISTS `group_chat_report`;
DROP TABLE IF EXISTS `group_chat_message`;
DROP TABLE IF EXISTS `group_chat_member`;
DROP TABLE IF EXISTS `group_chat`;

ALTER TABLE `user`
    ADD COLUMN `creator_state` tinyint NOT NULL DEFAULT 0 COMMENT '创作者认证状态: 0未认证 1已认证'
    AFTER `vip_expire_at`;

CREATE TABLE `group_chat` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '群聊ID',
    `owner_user_id` bigint NOT NULL COMMENT '群主用户ID',
    `name` varchar(24) NOT NULL COMMENT '群名称',
    `avatar_url` varchar(512) DEFAULT NULL COMMENT '群头像URL',
    `intro` varchar(120) DEFAULT NULL COMMENT '群简介',
    `group_type` tinyint NOT NULL DEFAULT 0 COMMENT '群类型: 0公开 1私有',
    `member_limit` int NOT NULL DEFAULT 100 COMMENT '当前身份对应人数上限快照',
    `member_count` int NOT NULL DEFAULT 0 COMMENT '当前成员数',
    `status` tinyint NOT NULL DEFAULT 0 COMMENT '群状态: 0正常 1满员 2超额锁定 3已解散 4违规封禁',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除: 0否 1是',
    PRIMARY KEY (`id`),
    KEY `idx_group_owner_status` (`owner_user_id`, `status`, `delete_state`),
    KEY `idx_group_public` (`group_type`, `status`, `delete_state`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群聊主表';

CREATE TABLE `group_chat_member` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '成员记录ID',
    `group_id` bigint NOT NULL COMMENT '群聊ID',
    `user_id` bigint NOT NULL COMMENT '用户ID',
    `role` tinyint NOT NULL DEFAULT 1 COMMENT '角色: 0群主 1成员 2管理员',
    `remark_name` varchar(24) DEFAULT NULL COMMENT '群内备注昵称',
    `notify_mode` tinyint NOT NULL DEFAULT 0 COMMENT '提醒模式: 0正常 1仅@提醒 2完全不提醒',
    `mute_until` datetime DEFAULT NULL COMMENT '禁言截止时间',
    `last_read_message_id` bigint NOT NULL DEFAULT 0 COMMENT '最后已读群消息ID',
    `join_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态: 0正常 1已退出 2被移除',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除: 0否 1是',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_group_member` (`group_id`, `user_id`, `delete_state`),
    KEY `idx_member_user_status` (`user_id`, `status`, `delete_state`),
    KEY `idx_member_group_status` (`group_id`, `status`, `delete_state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群聊成员表';

CREATE TABLE `group_chat_message` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '群消息ID',
    `group_id` bigint NOT NULL COMMENT '群聊ID',
    `sender_user_id` bigint DEFAULT NULL COMMENT '发送者用户ID，系统消息为空',
    `message_type` tinyint NOT NULL DEFAULT 0 COMMENT '消息类型: 0文本 1表情 2图片 9系统',
    `content` varchar(500) NOT NULL COMMENT '消息内容',
    `reply_message_id` bigint DEFAULT NULL COMMENT '回复的群消息ID',
    `reply_sender_name` varchar(64) DEFAULT NULL COMMENT '被回复消息发送者昵称快照',
    `reply_content` varchar(200) DEFAULT NULL COMMENT '被回复消息内容快照',
    `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态: 0正常 1举报隐藏 2删除',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除: 0否 1是',
    PRIMARY KEY (`id`),
    KEY `idx_group_message` (`group_id`, `delete_state`, `id`),
    KEY `idx_sender_time` (`sender_user_id`, `delete_state`, `create_time`),
    KEY `idx_group_reply_message` (`reply_message_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群聊消息表';

CREATE TABLE `group_chat_report` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '举报记录ID',
    `group_id` bigint NOT NULL COMMENT '群聊ID',
    `message_id` bigint NOT NULL COMMENT '群消息ID',
    `reporter_user_id` bigint NOT NULL COMMENT '举报人用户ID',
    `target_user_id` bigint NOT NULL COMMENT '被举报用户ID',
    `reason` varchar(200) NOT NULL COMMENT '举报原因',
    `status` tinyint NOT NULL DEFAULT 0 COMMENT '处理状态: 0待处理 1已处理 2驳回',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除: 0否 1是',
    PRIMARY KEY (`id`),
    KEY `idx_group_report` (`group_id`, `status`, `delete_state`, `create_time`),
    KEY `idx_message_report` (`message_id`, `delete_state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群聊举报表';

CREATE TABLE `group_chat_join_request` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '申请ID',
    `group_id` bigint NOT NULL COMMENT '群聊ID',
    `target_user_id` bigint NOT NULL COMMENT '目标用户ID',
    `initiator_user_id` bigint NOT NULL COMMENT '发起人用户ID',
    `owner_user_id` bigint NOT NULL COMMENT '群主用户ID',
    `request_type` tinyint NOT NULL COMMENT '请求类型: 0申请加群 1邀请入群',
    `status` tinyint NOT NULL DEFAULT 0 COMMENT '处理状态: 0待处理 1已同意 2已拒绝',
    `owner_read_state` tinyint NOT NULL DEFAULT 0 COMMENT '群主查看状态: 0未读 1已读',
    `handled_by_user_id` bigint DEFAULT NULL COMMENT '处理人用户ID',
    `handle_time` datetime DEFAULT NULL COMMENT '处理时间',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除: 0否 1是',
    PRIMARY KEY (`id`),
    KEY `idx_group_status` (`group_id`, `status`, `delete_state`, `create_time`),
    KEY `idx_target_status` (`target_user_id`, `status`, `delete_state`, `create_time`),
    KEY `idx_owner_type_status` (`owner_user_id`, `request_type`, `status`, `delete_state`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群加入申请与邀请表';

-- ----------------------------
-- 26. 漂流瓶模块
-- ----------------------------
DROP TABLE IF EXISTS `drift_bottle_report`;
DROP TABLE IF EXISTS `drift_bottle_pick_log`;
DROP TABLE IF EXISTS `drift_bottle_comment`;
DROP TABLE IF EXISTS `drift_bottle`;

CREATE TABLE `drift_bottle` (
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

CREATE TABLE `drift_bottle_comment` (
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

CREATE TABLE `drift_bottle_pick_log` (
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

CREATE TABLE `drift_bottle_report` (
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

-- ----------------------------
-- 27. 考试题库模块
-- ----------------------------
DROP TABLE IF EXISTS `exam_question_user_progress`;
DROP TABLE IF EXISTS `exam_question`;
DROP TABLE IF EXISTS `exam_question_bank`;

CREATE TABLE `exam_question_bank` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '题库ID',
    `user_id` bigint NOT NULL COMMENT '创建用户ID',
    `subject` varchar(100) NOT NULL COMMENT '考试科目',
    `source_name` varchar(255) NOT NULL COMMENT '来源文件名',
    `total_count` int NOT NULL DEFAULT 0 COMMENT '总题数',
    `choice_count` int NOT NULL DEFAULT 0 COMMENT '选择题数量',
    `judgement_count` int NOT NULL DEFAULT 0 COMMENT '判断题数量',
    `subjective_count` int NOT NULL DEFAULT 0 COMMENT '主观题数量',
    `warnings_json` json DEFAULT NULL COMMENT '解析警告JSON',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0否 1是',
    PRIMARY KEY (`id`),
    KEY `idx_user_subject_time` (`user_id`, `subject`, `delete_state`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考试题库主表';

CREATE TABLE `exam_question` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '题目ID',
    `bank_id` bigint NOT NULL COMMENT '题库ID',
    `question_order` int NOT NULL COMMENT '题目顺序',
    `source_no` varchar(30) DEFAULT NULL COMMENT '原文题号',
    `section_name` varchar(100) DEFAULT NULL COMMENT '原文章节或分组',
    `question_type` varchar(20) NOT NULL COMMENT '题目类型',
    `stem` text NOT NULL COMMENT '题干',
    `options_json` json DEFAULT NULL COMMENT '选项JSON',
    `standard_answer` text COMMENT '标准答案',
    `explanation` text COMMENT '解析',
    `answer_inferred_from_user` tinyint NOT NULL DEFAULT 0 COMMENT '答案是否从用户答案推断',
    `needs_option_review` tinyint NOT NULL DEFAULT 0 COMMENT '是否需要人工复核选项',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0否 1是',
    PRIMARY KEY (`id`),
    KEY `idx_bank_order` (`bank_id`, `delete_state`, `question_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考试题库题目表';

CREATE TABLE `exam_question_user_progress` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '进度ID',
    `user_id` bigint NOT NULL COMMENT '用户ID',
    `bank_id` bigint NOT NULL COMMENT '题库ID',
    `question_id` bigint NOT NULL COMMENT '题目ID',
    `answer_text` varchar(1000) DEFAULT NULL COMMENT '用户答案',
    `answered` tinyint NOT NULL DEFAULT 0 COMMENT '是否已作答: 0否 1是',
    `correct` tinyint DEFAULT NULL COMMENT '是否答对: 0否 1是',
    `wrong` tinyint NOT NULL DEFAULT 0 COMMENT '是否错题: 0否 1是',
    `focus` tinyint NOT NULL DEFAULT 0 COMMENT '是否重点记忆: 0否 1是',
    `judge_score` int DEFAULT NULL COMMENT '主观题评分',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0否 1是',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_exam_progress_user_question` (`user_id`, `question_id`),
    KEY `idx_exam_progress_user_bank` (`user_id`, `bank_id`, `delete_state`),
    KEY `idx_exam_progress_user_focus` (`user_id`, `focus`, `delete_state`),
    KEY `idx_exam_progress_user_wrong` (`user_id`, `wrong`, `delete_state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考试题库用户答题进度表';

-- ----------------------------
-- 28. 为你推荐 P0
-- ----------------------------
DROP TABLE IF EXISTS `user_recommend_feedback`;
DROP TABLE IF EXISTS `user_interest_preference`;

CREATE TABLE `user_interest_preference` (
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

CREATE TABLE `user_recommend_feedback` (
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

-- ----------------------------
-- 29. 成长中心与体验会员
-- ----------------------------
DROP TABLE IF EXISTS `vip_trial_entitlement`;
DROP TABLE IF EXISTS `growth_reward_record`;
DROP TABLE IF EXISTS `growth_experience_log`;
DROP TABLE IF EXISTS `growth_challenge_attempt`;
DROP TABLE IF EXISTS `growth_challenge`;
DROP TABLE IF EXISTS `user_growth_profile`;

CREATE TABLE `user_growth_profile` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '成长档案ID',
    `user_id` bigint NOT NULL COMMENT '用户ID',
    `formal_state` tinyint NOT NULL DEFAULT 0 COMMENT '正式用户状态: 0非正式 1正式',
    `experience` int NOT NULL DEFAULT 0 COMMENT '成长经验',
    `growth_level` int NOT NULL DEFAULT 1 COMMENT '成长等级',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0否 1是',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_growth_profile_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户成长档案';

CREATE TABLE `growth_challenge` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '挑战ID',
    `challenge_code` varchar(40) NOT NULL COMMENT '挑战编码',
    `challenge_type` varchar(30) NOT NULL COMMENT '挑战类型',
    `title` varchar(80) NOT NULL COMMENT '挑战标题',
    `description` varchar(500) DEFAULT NULL COMMENT '挑战说明',
    `bank_id` bigint NOT NULL COMMENT '关联题库ID',
    `question_count` int NOT NULL DEFAULT 10 COMMENT '抽题数',
    `passing_score` int NOT NULL DEFAULT 80 COMMENT '及格分',
    `max_attempts_per_day` int NOT NULL DEFAULT 3 COMMENT '每日最大尝试数',
    `experience_reward` int NOT NULL DEFAULT 0 COMMENT '通过经验奖励',
    `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '是否启用: 0否 1是',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0否 1是',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_growth_challenge_code` (`challenge_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成长挑战定义';

CREATE TABLE `growth_challenge_attempt` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '挑战尝试ID',
    `user_id` bigint NOT NULL COMMENT '用户ID',
    `challenge_id` bigint NOT NULL COMMENT '挑战ID',
    `attempt_no` int NOT NULL COMMENT '尝试序号',
    `status` varchar(20) NOT NULL COMMENT '尝试状态',
    `question_ids_json` json NOT NULL COMMENT '本次题目ID',
    `answers_json` json DEFAULT NULL COMMENT '用户答案',
    `score` int DEFAULT NULL COMMENT '得分',
    `started_at` datetime NOT NULL COMMENT '开始时间',
    `submitted_at` datetime DEFAULT NULL COMMENT '提交时间',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0否 1是',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_growth_attempt_user_challenge_no` (`user_id`, `challenge_id`, `attempt_no`),
    KEY `idx_growth_attempt_user_challenge` (`user_id`, `challenge_id`, `delete_state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成长挑战尝试记录';

CREATE TABLE `growth_experience_log` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '经验流水ID',
    `user_id` bigint NOT NULL COMMENT '用户ID',
    `source_type` varchar(30) NOT NULL COMMENT '经验来源类型',
    `source_business_id` bigint NOT NULL COMMENT '来源业务ID',
    `experience_delta` int NOT NULL COMMENT '经验变动',
    `remark` varchar(200) DEFAULT NULL COMMENT '备注',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0否 1是',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_growth_experience_source` (`user_id`, `source_type`, `source_business_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成长经验流水';

CREATE TABLE `growth_reward_record` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '奖励流水ID',
    `user_id` bigint NOT NULL COMMENT '用户ID',
    `challenge_id` bigint NOT NULL COMMENT '挑战ID',
    `reward_type` varchar(30) NOT NULL COMMENT '奖励类型',
    `reward_value` varchar(100) DEFAULT NULL COMMENT '奖励值',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0否 1是',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_growth_reward_user_challenge_type` (`user_id`, `challenge_id`, `reward_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成长奖励流水';

CREATE TABLE `vip_trial_entitlement` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '体验会员ID',
    `user_id` bigint NOT NULL COMMENT '用户ID',
    `trial_code` varchar(30) NOT NULL COMMENT '体验编码',
    `status` varchar(20) NOT NULL COMMENT '状态: ACTIVE EXPIRED SUPERSEDED',
    `expire_at` datetime NOT NULL COMMENT '体验到期时间',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0否 1是',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_vip_trial_user_code` (`user_id`, `trial_code`),
    KEY `idx_vip_trial_active` (`user_id`, `status`, `expire_at`, `delete_state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='体验会员权益';

INSERT INTO `exam_question_bank`
(`user_id`, `subject`, `source_name`, `total_count`, `choice_count`, `judgement_count`, `subjective_count`, `warnings_json`, `delete_state`) VALUES
(0, '成长中心·新人试炼', 'growth-center-formal-user', 10, 10, 0, 0, JSON_ARRAY(), 0),
(0, '成长中心·会员体验', 'growth-center-vip-trial-900', 10, 10, 0, 0, JSON_ARRAY(), 0);

SET @formal_bank := (SELECT `id` FROM `exam_question_bank` WHERE `user_id` = 0 AND `subject` = '成长中心·新人试炼' AND `delete_state` = 0 ORDER BY `id` DESC LIMIT 1);
SET @trial_bank := (SELECT `id` FROM `exam_question_bank` WHERE `user_id` = 0 AND `subject` = '成长中心·会员体验' AND `delete_state` = 0 ORDER BY `id` DESC LIMIT 1);

INSERT INTO `exam_question`
(`bank_id`, `question_order`, `source_no`, `section_name`, `question_type`, `stem`, `options_json`, `standard_answer`, `explanation`, `answer_inferred_from_user`, `needs_option_review`, `delete_state`) VALUES
(@formal_bank, 1, '1', '社区规则', 'SINGLE', '发现违规内容时，正确的做法是？', JSON_ARRAY(JSON_OBJECT('label', 'A', 'text', '使用举报入口并提供真实说明'), JSON_OBJECT('label', 'B', 'text', '在评论区攻击对方')), 'A', '维护社区安全', 0, 0, 0),
(@formal_bank, 2, '2', '社区规则', 'SINGLE', '发布他人隐私信息是否允许？', JSON_ARRAY(JSON_OBJECT('label', 'A', 'text', '不允许，应尊重他人隐私'), JSON_OBJECT('label', 'B', 'text', '允许，只要内容有热度')), 'A', '保护隐私是基本规则', 0, 0, 0),
(@formal_bank, 3, '3', '社区规则', 'SINGLE', '遇到陌生链接时应当？', JSON_ARRAY(JSON_OBJECT('label', 'A', 'text', '谨慎核实来源，不随意输入账号信息'), JSON_OBJECT('label', 'B', 'text', '立刻点击并转发')), 'A', '防范账号风险', 0, 0, 0),
(@formal_bank, 4, '4', '社区规则', 'SINGLE', '评论交流应遵循什么原则？', JSON_ARRAY(JSON_OBJECT('label', 'A', 'text', '友善、就事论事'), JSON_OBJECT('label', 'B', 'text', '人身攻击更有说服力')), 'A', '文明交流', 0, 0, 0),
(@formal_bank, 5, '5', '社区规则', 'SINGLE', '转载社区内容前应当？', JSON_ARRAY(JSON_OBJECT('label', 'A', 'text', '确认授权并注明来源'), JSON_OBJECT('label', 'B', 'text', '直接复制即可')), 'A', '尊重原创', 0, 0, 0),
(@formal_bank, 6, '6', '社区规则', 'SINGLE', '账号密码应当？', JSON_ARRAY(JSON_OBJECT('label', 'A', 'text', '妥善保管且不与他人共用'), JSON_OBJECT('label', 'B', 'text', '告诉陌生网友')), 'A', '账号安全', 0, 0, 0),
(@formal_bank, 7, '7', '社区规则', 'SINGLE', '发现账号异常登录时应当？', JSON_ARRAY(JSON_OBJECT('label', 'A', 'text', '及时修改密码并查看登录记录'), JSON_OBJECT('label', 'B', 'text', '继续忽略')), 'A', '及时处置风险', 0, 0, 0),
(@formal_bank, 8, '8', '社区规则', 'SINGLE', '发布内容时应当？', JSON_ARRAY(JSON_OBJECT('label', 'A', 'text', '遵守法律和社区规则'), JSON_OBJECT('label', 'B', 'text', '为了流量可以造谣')), 'A', '内容责任', 0, 0, 0),
(@formal_bank, 9, '9', '社区规则', 'SINGLE', '与他人意见不同可以？', JSON_ARRAY(JSON_OBJECT('label', 'A', 'text', '理性表达观点'), JSON_OBJECT('label', 'B', 'text', '恶意骚扰对方')), 'A', '尊重不同意见', 0, 0, 0),
(@formal_bank, 10, '10', '社区规则', 'SINGLE', '社区功能遇到问题时可以？', JSON_ARRAY(JSON_OBJECT('label', 'A', 'text', '查看帮助或反馈问题'), JSON_OBJECT('label', 'B', 'text', '发布无关攻击内容')), 'A', '合理反馈', 0, 0, 0),
(@trial_bank, 1, '1', '会员体验', 'SINGLE', '会员体验有效期是？', JSON_ARRAY(JSON_OBJECT('label', 'A', 'text', '7 天'), JSON_OBJECT('label', 'B', 'text', '永久有效')), 'A', '体验权益有明确期限', 0, 0, 0),
(@trial_bank, 2, '2', '会员体验', 'SINGLE', '体验会员可以领取几次？', JSON_ARRAY(JSON_OBJECT('label', 'A', 'text', '每个账号一次'), JSON_OBJECT('label', 'B', 'text', '每天一次')), 'A', '避免重复领取', 0, 0, 0),
(@trial_bank, 3, '3', '会员体验', 'SINGLE', '体验会员的模型额度？', JSON_ARRAY(JSON_OBJECT('label', 'A', 'text', '使用独立体验额度'), JSON_OBJECT('label', 'B', 'text', '等同完整付费额度')), 'A', '体验额度独立管理', 0, 0, 0),
(@trial_bank, 4, '4', '会员体验', 'SINGLE', '已有有效付费会员能否领取体验？', JSON_ARRAY(JSON_OBJECT('label', 'A', 'text', '不能'), JSON_OBJECT('label', 'B', 'text', '可以叠加')), 'A', '避免覆盖付费权益', 0, 0, 0),
(@trial_bank, 5, '5', '会员体验', 'SINGLE', '会员功能应当如何使用？', JSON_ARRAY(JSON_OBJECT('label', 'A', 'text', '遵守站内规则合理使用'), JSON_OBJECT('label', 'B', 'text', '用于违规内容')), 'A', '权益也需遵守规则', 0, 0, 0),
(@trial_bank, 6, '6', '会员体验', 'SINGLE', '体验到期后会？', JSON_ARRAY(JSON_OBJECT('label', 'A', 'text', '自动失效'), JSON_OBJECT('label', 'B', 'text', '自动续期')), 'A', '体验不续期', 0, 0, 0),
(@trial_bank, 7, '7', '会员体验', 'SINGLE', '模型用量达到体验额度后？', JSON_ARRAY(JSON_OBJECT('label', 'A', 'text', '等待重置或按规则使用其他能力'), JSON_OBJECT('label', 'B', 'text', '绕过限制')), 'A', '额度受系统控制', 0, 0, 0),
(@trial_bank, 8, '8', '会员体验', 'SINGLE', '会员中心主要用于？', JSON_ARRAY(JSON_OBJECT('label', 'A', 'text', '查看权益和额度'), JSON_OBJECT('label', 'B', 'text', '修改他人账号')), 'A', '权益信息透明展示', 0, 0, 0),
(@trial_bank, 9, '9', '会员体验', 'SINGLE', '体验挑战通过后获得？', JSON_ARRAY(JSON_OBJECT('label', 'A', 'text', '7 天 TRIAL_900 体验'), JSON_OBJECT('label', 'B', 'text', '永久 MAX')), 'A', '奖励与挑战匹配', 0, 0, 0),
(@trial_bank, 10, '10', '会员体验', 'SINGLE', '会员体验挑战的目的？', JSON_ARRAY(JSON_OBJECT('label', 'A', 'text', '了解站内会员能力'), JSON_OBJECT('label', 'B', 'text', '绕过付费规则')), 'A', '合理体验权益', 0, 0, 0);

INSERT INTO `growth_challenge`
(`challenge_code`, `challenge_type`, `title`, `description`, `bank_id`, `question_count`, `passing_score`, `max_attempts_per_day`, `experience_reward`, `enabled`, `delete_state`) VALUES
('FORMAL_USER', 'FORMAL_USER', '新人试炼', '完成社区规则与安全基础题，获得正式用户资格。', @formal_bank, 10, 80, 3, 100, 1, 0),
('VIP_TRIAL_900', 'VIP_TRIAL_900', '会员体验挑战', '通过后获得一次 7 天 TRIAL_900 会员体验。', @trial_bank, 10, 80, 3, 80, 1, 0);

CREATE TABLE `forum_mascot_related_recommendation` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `user_id` bigint NOT NULL,
    `companion_session_id` bigint NOT NULL,
    `query` varchar(500) NOT NULL,
    `result_state` varchar(16) NOT NULL,
    `result_count` int NOT NULL DEFAULT 0,
    `delete_state` tinyint NOT NULL DEFAULT 0,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_mascot_related_user_session_time` (`user_id`, `companion_session_id`, `delete_state`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='看板娘已确认相关帖子检索';

CREATE TABLE `forum_mascot_related_recommendation_item` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `recommendation_id` bigint NOT NULL,
    `article_id` bigint NOT NULL,
    `display_order` int NOT NULL,
    `selection_reason` varchar(16) NOT NULL,
    `delete_state` tinyint NOT NULL DEFAULT 0,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_mascot_related_recommendation_article` (`recommendation_id`, `article_id`),
    KEY `idx_mascot_related_item_recommendation` (`recommendation_id`, `delete_state`, `display_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='看板娘相关帖子检索结果项';

CREATE TABLE `forum_article_ai_feature` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `article_id` bigint NOT NULL,
    `feature_json` mediumtext NOT NULL,
    `feature_version` varchar(32) NOT NULL,
    `content_hash` varchar(64) NOT NULL,
    `generated_by` varchar(32) NOT NULL,
    `delete_state` tinyint NOT NULL DEFAULT 0,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_article_ai_feature_article` (`article_id`),
    KEY `idx_article_ai_feature_state_time` (`delete_state`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子推荐AI特征';

CREATE TABLE `forum_user_ai_profile_snapshot` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `user_id` bigint NOT NULL,
    `profile_version` bigint NOT NULL DEFAULT 0,
    `profile_json` mediumtext NOT NULL,
    `feature_version` varchar(32) NOT NULL,
    `source_window_start` datetime DEFAULT NULL,
    `source_window_end` datetime DEFAULT NULL,
    `refresh_after` datetime NOT NULL,
    `generated_by` varchar(32) NOT NULL,
    `delete_state` tinyint NOT NULL DEFAULT 0,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_ai_profile_snapshot_user` (`user_id`),
    KEY `idx_user_ai_profile_snapshot_refresh` (`delete_state`, `refresh_after`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户推荐AI画像快照';

SET FOREIGN_KEY_CHECKS = 1;
