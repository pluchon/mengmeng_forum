-- 全库初始化脚本（执行即删库重建 forum_db；勿与 PostgreSQL 的 postgres_ai_session.sql 混跑）
-- 结构：DROP/CREATE 全部表 + 少量示例/配置种子（看板娘、分类版块、签到兜底、公告、AI 单价、VIP 配额、管理端 RBAC、抽奖演示）。
-- 不含用户/帖子等业务数据；生产数据请走注册与运营后台维护。
-- 结构变更请整库重跑本脚本，勿做增量 patch。
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
                        `password` varchar(32) NOT NULL COMMENT '加密后的密码',
                        `nickname` varchar(50) NOT NULL COMMENT '昵称, 非空',
                        `phone_num` varchar(255) DEFAULT NULL COMMENT '手机号密文',
                        `phone_hash` varchar(64) DEFAULT NULL COMMENT '手机号HMAC，用于等值查询',
                        `email` varchar(255) DEFAULT NULL COMMENT '邮箱密文',
                        `email_hash` varchar(64) DEFAULT NULL COMMENT '邮箱HMAC，用于等值查询',
                        `gender` tinyint NOT NULL DEFAULT 2 COMMENT '0女 1男 2保密, 非空, 默认2',
                        `salt` varchar(32) NOT NULL COMMENT '为密码加盐, 非空',
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
                        `admin_tag` varchar(500) DEFAULT NULL COMMENT '管理员标签，仅管理端维护',
                        `dept_id` bigint DEFAULT NULL COMMENT '后台部门ID，对应 sys_dept.id',
                        `state` tinyint NOT NULL DEFAULT 0 COMMENT '状态: 0正常, 1禁言',
                        `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除: 0否 1是',
                        `lottery_pity_draws` int NOT NULL DEFAULT 0 COMMENT '抽奖硬保底计数：连续未中神秘大奖(is_jackpot)父档的次数，命中后归零',
                        `lottery_surprise_claimed` tinyint NOT NULL DEFAULT 0 COMMENT '抽奖页彩蛋积分是否已领取 0否 1是',
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
                           INDEX `idx_audit_pending` (`status`, `audit_submitted_at`)
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
                              `remark` varchar(200) DEFAULT NULL COMMENT '人类可读描述',
                              `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除: 0否 1是',
                              `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                              `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                              PRIMARY KEY (`id`),
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
    `provider` varchar(32) NOT NULL DEFAULT 'dashscope' COMMENT 'dashscope|deepseek|huanapi',
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
('deepseek-v4-flash', 'deepseek', 'per_1m_input', 1.000000, 0, 1, '缓存未命中'),
('deepseek-v4-flash', 'deepseek', 'per_1m_output', 2.000000, 0, 1, '缓存未命中'),
('deepseek-v4-pro', 'deepseek', 'per_1m_input', 3.000000, 1, 1, '缓存未命中'),
('deepseek-v4-pro', 'deepseek', 'per_1m_output', 6.000000, 1, 1, '缓存未命中'),
('z-image-turbo', 'dashscope', 'per_image', 0.100000, 0, 1, 'prompt_extend=false'),
('wanx2.1-t2i-plus', 'dashscope', 'per_image', 0.100000, 1, 1, '通义万相进阶生图(兜底)'),
('gpt-image-2', 'huanapi', 'per_image', 0.200000, 1, 1, 'GPT Image 进阶生图'),
('gemini-3.1-pro', 'huanapi', 'per_1m_input', 2.000000, 1, 1, 'Gemini Pro'),
('gemini-3.1-pro', 'huanapi', 'per_1m_output', 8.000000, 1, 1, 'Gemini Pro'),
('claude-haiku-4-5', 'huanapi', 'per_1m_input', 1.000000, 1, 1, 'Claude Haiku PRO+'),
('claude-haiku-4-5', 'huanapi', 'per_1m_output', 4.000000, 1, 1, 'Claude Haiku PRO+'),
('claude-sonnet-4-6', 'huanapi', 'per_1m_input', 3.000000, 1, 1, 'Claude Sonnet MAX+'),
('claude-sonnet-4-6', 'huanapi', 'per_1m_output', 12.000000, 1, 1, 'Claude Sonnet MAX+');

-- ----------------------------
-- 19.3 AI 调用明细 (forum_ai_usage_log)
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
    `related_id` varchar(64) DEFAULT NULL COMMENT '会话或业务关联ID',
    `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '0否 1是',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_ai_usage_log_user_time` (`user_id`, `create_time`),
    KEY `idx_ai_usage_log_feature` (`feature_code`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI调用积分明细';

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
    `image_url` varchar(1024) DEFAULT NULL COMMENT '生图URL(OSS)',
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
-- 23. 抽奖记录表 (lottery_draw_record)
-- mystery_item_*: 神秘大奖开奖后实际子项快照; 非神秘时为 NULL
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
-- 23.1 抽奖按小时汇总(管理端首页趋势; 每次抽奖写入 UPSERT)
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
    ('jinzi', '井字棋', NULL, 1, 20, 0);

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
-- 23.4 游戏对局记录表 (game_match_record)
-- ----------------------------
DROP TABLE IF EXISTS `game_match_record`;
CREATE TABLE `game_match_record` (
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
-- 23.7 游戏房间落子记录表 (game_room_move)
-- ----------------------------
DROP TABLE IF EXISTS `game_room_move`;
CREATE TABLE `game_room_move` (
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


-- ----------------------------
-- 24. AI 每日用量表 (ai_usage_daily)
-- 按用户 + 自然日(建议 Asia/Shanghai)滚动; 推荐配图要点单独计数不计入 deepseek_write 写作配额.
-- deepseek_write_used: 仅普通用户档位累计至 10 上限; PRO/MAX 可不递增或由业务忽略校验.
-- advanced_llm_used / image_* / companion_* : 按 VIP 矩阵限额在业务层校验.
-- ----------------------------
DROP TABLE IF EXISTS `ai_usage_daily`;
CREATE TABLE `ai_usage_daily` (
                                  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
                                  `user_id` bigint NOT NULL COMMENT '用户ID',
                                  `usage_date` date NOT NULL COMMENT '用量归属日',
                                  `deepseek_write_used` int NOT NULL DEFAULT 0 COMMENT 'DeepSeek写作已用次数(普通用户上限10)',
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
-- daily_bucket: deepseek_write | advanced_llm | image_normal | image_premium
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
    `icon_provider` varchar(32) DEFAULT NULL COMMENT 'deepseek|qwen|gemini|claude|openai|huanapi',
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
(1, 'deepseek_flash', 'DeepSeek · 会员权益', 'DeepSeek V4 Flash', 'unlimited', NULL, 'deepseek-v4-flash', 'deepseek', NULL, NULL, '免费', 10),
(1, 'image_normal', 'AI 生图 · 每日', 'Z-Image Turbo（普通）', 'daily_count', 'image_normal', 'z-image-turbo', 'qwen', 15, NULL, 'PRO', 30),
(1, 'image_premium', 'AI 生图 · 每日', 'GPT Image 2（进阶）', 'daily_count', 'image_premium', 'gpt-image-2', 'openai', 10, NULL, 'PRO', 40),
(1, 'token_qwen_deep', '本期 Token 配额 · 文本', '通义千问 · 深度', 'token_period', NULL, 'qwen3.7-max', 'qwen', NULL, 500000, 'PRO', 50),
(1, 'token_deepseek_deep', '本期 Token 配额 · 文本', 'DeepSeek · 深度', 'token_period', NULL, 'deepseek-v4-pro', 'deepseek', NULL, 450000, 'PRO', 60),
(1, 'token_gemini_deep', '本期 Token 配额 · 文本', 'Gemini · 深度', 'token_period', NULL, 'gemini-3.1-pro', 'gemini', NULL, 300000, 'PRO', 70),
(2, 'deepseek_flash', 'DeepSeek · 会员权益', 'DeepSeek V4 Flash', 'unlimited', NULL, 'deepseek-v4-flash', 'deepseek', NULL, NULL, '免费', 10),
(2, 'image_normal', 'AI 生图 · 每日', 'Z-Image Turbo（普通）', 'daily_count', 'image_normal', 'z-image-turbo', 'qwen', 50, NULL, 'MAX', 30),
(2, 'image_premium', 'AI 生图 · 每日', 'GPT Image 2（进阶）', 'daily_count', 'image_premium', 'gpt-image-2', 'openai', 50, NULL, 'MAX', 40),
(2, 'token_qwen_deep', '本期 Token 配额 · 文本', '通义千问 · 深度', 'token_period', NULL, 'qwen3.7-max', 'qwen', NULL, 2000000, 'MAX', 50),
(2, 'token_deepseek_deep', '本期 Token 配额 · 文本', 'DeepSeek · 深度', 'token_period', NULL, 'deepseek-v4-pro', 'deepseek', NULL, 1300000, 'MAX', 60),
(2, 'token_gemini_deep', '本期 Token 配额 · 文本', 'Gemini · 深度', 'token_period', NULL, 'gemini-3.1-pro', 'gemini', NULL, 800000, 'MAX', 70),
(2, 'token_claude_sonnet', '本期 Token 配额 · 文本', 'Claude Sonnet', 'token_period', NULL, 'claude-sonnet-4-6', 'claude', NULL, 400000, 'MAX', 90);

-- ----------------------------
-- 25. 管理后台 RBAC（菜单 / 角色 / 部门 / 字典），供 forum-vue-admin 对接
-- 权限仍以 forum.user.is_admin=1 为硬门槛；以下为前台 Gi 动态路由与表单脚手架。
-- ----------------------------
DROP TABLE IF EXISTS `sys_role_menu`;
DROP TABLE IF EXISTS `sys_user_role`;
DROP TABLE IF EXISTS `sys_menu`;
DROP TABLE IF EXISTS `sys_dict_data`;
DROP TABLE IF EXISTS `sys_dict_type`;
DROP TABLE IF EXISTS `sys_role`;
DROP TABLE IF EXISTS `sys_dept`;

CREATE TABLE `sys_dept` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '部门ID',
    `parent_id` bigint NOT NULL DEFAULT 0 COMMENT '父级ID，0为根',
    `name` varchar(100) NOT NULL COMMENT '部门名称',
    `sort` int NOT NULL DEFAULT 0 COMMENT '排序',
    `status` char(1) NOT NULL DEFAULT '1' COMMENT '状态 1启用 0停用',
    `description` varchar(500) DEFAULT NULL COMMENT '备注',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='后台部门树';

CREATE TABLE `sys_role` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '角色ID',
    `role_code` varchar(64) NOT NULL COMMENT '角色编码，如 role_admin',
    `role_name` varchar(100) NOT NULL COMMENT '角色名称',
    `remark` varchar(500) DEFAULT NULL COMMENT '备注',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='后台角色';

CREATE TABLE `sys_menu` (
    `id` varchar(32) NOT NULL COMMENT '菜单ID（与前端路由组件路径约定一致）',
    `parent_id` varchar(32) NOT NULL DEFAULT '' COMMENT '父菜单ID',
    `path` varchar(200) NOT NULL COMMENT '路由 path',
    `component` varchar(200) DEFAULT NULL COMMENT '组件标识 Layout / system/user/index',
    `redirect` varchar(200) DEFAULT NULL COMMENT '重定向',
    `type` tinyint NOT NULL COMMENT '1目录 2菜单 3按钮',
    `title` varchar(100) NOT NULL COMMENT '标题',
    `icon` varchar(100) DEFAULT NULL COMMENT '图标',
    `sort` int NOT NULL DEFAULT 0 COMMENT '排序',
    `hidden` tinyint NOT NULL DEFAULT 0 COMMENT '是否隐藏',
    `keep_alive` tinyint NOT NULL DEFAULT 0 COMMENT '是否缓存',
    `breadcrumb` tinyint NOT NULL DEFAULT 1 COMMENT '面包屑',
    `affix` tinyint NOT NULL DEFAULT 0 COMMENT '固定标签',
    `show_in_tabs` tinyint NOT NULL DEFAULT 1 COMMENT '是否出现在标签栏',
    `always_show` tinyint NOT NULL DEFAULT 0 COMMENT '是否总是显示父级',
    `active_menu` varchar(200) DEFAULT NULL COMMENT '高亮菜单 path',
    `permission` varchar(100) DEFAULT NULL COMMENT '权限标识',
    `status` char(1) NOT NULL DEFAULT '1' COMMENT '状态',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='后台菜单';

CREATE TABLE `sys_dict_type` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `dict_code` varchar(64) NOT NULL COMMENT '字典编码',
    `dict_name` varchar(100) NOT NULL COMMENT '字典名称',
    `remark` varchar(255) DEFAULT NULL,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dict_code` (`dict_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='字典类型';

CREATE TABLE `sys_dict_data` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `dict_code` varchar(64) NOT NULL COMMENT '字典编码',
    `label` varchar(100) NOT NULL COMMENT '显示文本',
    `value` varchar(100) NOT NULL COMMENT '值',
    `sort` int NOT NULL DEFAULT 0,
    `status` char(1) NOT NULL DEFAULT '1' COMMENT '1启用 0停用',
    PRIMARY KEY (`id`),
    INDEX `idx_dict_code` (`dict_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='字典数据';

CREATE TABLE `sys_user_role` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `user_id` bigint NOT NULL COMMENT 'forum.user.id',
    `role_id` bigint NOT NULL COMMENT 'sys_role.id',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
    INDEX `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户-角色';

CREATE TABLE `sys_role_menu` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `role_id` bigint NOT NULL,
    `menu_id` varchar(32) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_menu` (`role_id`, `menu_id`),
    INDEX `idx_menu_id` (`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色-菜单权限';

-- 初始角色（与前端 mock roles 一致）
INSERT INTO `sys_role` (`id`, `role_code`, `role_name`, `remark`) VALUES
    (1, 'role_admin', '超级管理员', '完全菜单'),
    (2, 'role_user', '普通用户', '演示');

-- 部门根节点
INSERT INTO `sys_dept` (`id`, `parent_id`, `name`, `sort`, `status`, `description`) VALUES
    (1, 0, '萌萌论坛总部', 0, '1', '默认部门');

-- 字典：Gi 模板 STATUS / GENDER
INSERT INTO `sys_dict_type` (`dict_code`, `dict_name`) VALUES
    ('STATUS', '用户状态'),
    ('GENDER', '性别');

INSERT INTO `sys_dict_data` (`dict_code`, `label`, `value`, `sort`, `status`) VALUES
    ('STATUS', '启用', '1', 1, '1'),
    ('STATUS', '停用', '0', 2, '1'),
    ('GENDER', '男', '1', 1, '1'),
    ('GENDER', '女', '2', 2, '1'),
    ('GENDER', '保密', '3', 3, '1');

-- 公告大类(用户端侧栏/管理端筛选用; value 与 forum_notice.notice_kind 一致)
INSERT INTO `sys_dict_type` (`dict_code`, `dict_name`, `remark`) VALUES
    ('FORUM_NOTICE_KIND', '论坛公告大类', '0入站 1活动 2纪律 3系统 4版规');

INSERT INTO `sys_dict_data` (`dict_code`, `label`, `value`, `sort`, `status`) VALUES
    ('FORUM_NOTICE_KIND', '新用户入站', '0', 1, '1'),
    ('FORUM_NOTICE_KIND', '活动公告', '1', 2, '1'),
    ('FORUM_NOTICE_KIND', '纪律公告', '2', 3, '1'),
    ('FORUM_NOTICE_KIND', '系统更新', '3', 4, '1'),
    ('FORUM_NOTICE_KIND', '版规公告', '4', 5, '1');

-- 注册成功后写入 system_message.type=4 时的默认标题与正文(业务可覆盖)
INSERT INTO `sys_dict_type` (`dict_code`, `dict_name`, `remark`) VALUES
    ('FORUM_REGISTER_SYSMSG', '注册入站系统消息', '配合 system_message.type=4 引导用户查看公告中心「入站必看」');

INSERT INTO `sys_dict_data` (`dict_code`, `label`, `value`, `sort`, `status`) VALUES
    ('FORUM_REGISTER_SYSMSG', 'TITLE', '入站必看', 1, '1'),
    ('FORUM_REGISTER_SYSMSG', 'CONTENT', '欢迎加入萌萌论坛！请打开公告中心阅读「入站必看」，了解社区规则后再发帖互动。', 2, '1');

-- 菜单树（与 forum-vue-admin mock 裁剪版一致）
INSERT INTO `sys_menu` (`id`,`parent_id`,`path`,`component`,`redirect`,`type`,`title`,`icon`,`sort`,`hidden`,`keep_alive`,`breadcrumb`,`affix`,`show_in_tabs`,`always_show`,`active_menu`,`permission`,`status`) VALUES
    ('08','','/system','Layout','/system/user',1,'系统管理','icon-park-outline:setting-two',2,0,0,1,0,1,0,'','','1');
INSERT INTO `sys_menu` (`id`,`parent_id`,`path`,`component`,`redirect`,`type`,`title`,`icon`,`sort`,`hidden`,`keep_alive`,`breadcrumb`,`affix`,`show_in_tabs`,`always_show`,`active_menu`,`permission`,`status`) VALUES
    ('0801','08','/system/user','system/user/index','',2,'用户管理','icon-park-outline:setting-config',1,0,0,1,0,1,0,'','','1'),
    ('0802','08','/system/role','system/role/index','',2,'角色管理','icon-park-outline:setting-config',2,1,0,1,0,1,0,'','','1'),
    ('0803','08','/system/dept','system/dept/index','',2,'部门管理','icon-park-outline:setting-config',3,1,0,1,0,1,0,'','','1'),
    ('0804','08','/system/menu','system/menu/index','',2,'菜单管理','icon-park-outline:setting-config',4,1,0,1,0,1,0,'','','1'),
    ('0806','08','/system/account','system/account/index','',2,'账户管理','icon-park-outline:setting-config',5,1,0,1,0,1,0,'','','1');

INSERT INTO `sys_menu` (`id`,`parent_id`,`path`,`component`,`redirect`,`type`,`title`,`icon`,`sort`,`hidden`,`keep_alive`,`breadcrumb`,`affix`,`show_in_tabs`,`always_show`,`active_menu`,`permission`,`status`) VALUES
    ('09','','/content','Layout','/content/article',1,'内容管理','icon-park-outline:notebook-one',1,0,0,1,0,1,0,'','','1');
INSERT INTO `sys_menu` (`id`,`parent_id`,`path`,`component`,`redirect`,`type`,`title`,`icon`,`sort`,`hidden`,`keep_alive`,`breadcrumb`,`affix`,`show_in_tabs`,`always_show`,`active_menu`,`permission`,`status`) VALUES
    ('0901','09','/content/article','content/article/index','',2,'帖子管理','icon-park-outline:notes',1,0,0,1,0,1,0,'','','1'),
    ('0902','09','/content/reply','content/reply/index','',2,'一级评论','icon-park-outline:message',2,1,0,1,0,1,0,'','','1'),
    ('0903','09','/content/notice','content/notice/index','',2,'论坛公告','icon-park-outline:volume-notice',3,0,0,1,0,1,0,'','','1'),
    ('0904','09','/content/lottery-activity','content/lottery-activity/index','',2,'活动管理','icon-park-outline:gift',4,0,0,1,0,1,0,'','','1'),
    ('0905','09','/content/lottery-prize','content/lottery-prize/index','',2,'奖品管理','icon-park-outline:box',5,0,0,1,0,1,0,'','','1'),
    ('0906','09','/content/mascot-model','content/mascot-model/index','',2,'看板娘模型','icon-park-outline:robot',6,0,0,1,0,1,0,'','','1');

-- 超级管理员可见全部菜单
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, `id` FROM `sys_menu`;

-- 绑定管理员账号：将首个 is_admin=1 的用户赋予 role_admin（若无则跳过）
INSERT INTO `sys_user_role` (`user_id`, `role_id`)
SELECT `id`, 1 FROM `user` WHERE `is_admin` = 1 AND `delete_state` = 0 LIMIT 1;

SET FOREIGN_KEY_CHECKS = 1;
