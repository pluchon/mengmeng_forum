-- 全库初始化脚本（执行即删库重建 forum_db；勿与 PostgreSQL 的 postgres_ai_session.sql 混跑）
-- 含 forum_notice、forum_ai_model_usage_daily 等与 Java 实体一致。
-- 已有库增量（菜单 hidden、lottery_prize.stock_quantity 等）见同目录 migrate-existing-2026.sql，勿与本文件混跑。
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
                           `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除: 0否 1是',
                           `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                           `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                           PRIMARY KEY (`id`),
                           INDEX `idx_audit_pending` (`status`, `audit_submitted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='帖子表';

-- ----------------------------
-- 3.0 帖子搜索与 RAG / Redis（forum-demo + ai-server；非本脚本创建的 KV/向量结构）
--   已发布帖子的标题、正文片段、作者昵称/用户名等由异步任务或 ai-server 写入语义索引（常见落地为 Redis 或向量库），供搜索增强与 rerank。
--   用户端「AI 搜索」模式请求 forum-demo /search/article?ai=1 时将跳过标题 LIKE，直接走 RAG 召回链路。
-- ----------------------------


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
    `icon` varchar(100) DEFAULT NULL COMMENT '分类图标',
    `sort` int NOT NULL DEFAULT 0 COMMENT '排序优先级',
    `state` tinyint NOT NULL DEFAULT 0 COMMENT '状态: 0正常, 1禁用',
    `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除: 0否 1是',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='分类表';

INSERT INTO `category` (`name`, `description`, `icon`, `sort`) VALUES
('代码世界', '编程技术、开发经验分享', '💻', 1),
('美好生活', '生活方式、日常分享', '🌸', 2),
('舌尖美食', '美食探店、食谱分享', '🍜', 3),
('影音娱乐', '电影、音乐、游戏', '🎬', 4),
('运动健康', '健身、运动、健康生活', '💪', 5);

-- 插入版块种子数据
INSERT INTO `board` (`name`, `category_id`, `article_count`, `sort`, `state`, `delete_state`) VALUES
('Java',         1, 0, 1, 0, 0),
('Python',       1, 0, 2, 0, 0),
('前端',         1, 0, 3, 0, 0),
('数据库',        1, 0, 4, 0, 0),
('算法',         1, 0, 5, 0, 0),
('生活日记',       2, 0, 1, 0, 0),
('旅行分享',       2, 0, 2, 0, 0),
('宠物日常',       2, 0, 3, 0, 0),
('家居装修',       2, 0, 4, 0, 0),
('美食探店',       3, 0, 1, 0, 0),
('家常菜谱',       3, 0, 2, 0, 0),
('烘焙甜品',       3, 0, 3, 0, 0),
('奶茶咖啡',       3, 0, 4, 0, 0),
('电影推荐',       4, 0, 1, 0, 0),
('音乐分享',       4, 0, 2, 0, 0),
('游戏交流',       4, 0, 3, 0, 0),
('综艺娱乐',       4, 0, 4, 0, 0),
('健身打卡',       5, 0, 1, 0, 0),
('跑步骑行',       5, 0, 2, 0, 0),
('瑜伽冥想',       5, 0, 3, 0, 0);

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

SET FOREIGN_KEY_CHECKS = 1;

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

-- 默认规则插入 (month=0)，覆盖全年366天
-- 积分规则：按月份+日期确定性伪随机积分 (10~99)
-- 1月 (31天)
INSERT INTO checkin_rule (month, day_number, points) VALUES
(1,1,54),(1,2,61),(1,3,68),(1,4,75),(1,5,82),(1,6,89),(1,7,96),
(1,8,13),(1,9,20),(1,10,27),(1,11,34),(1,12,41),(1,13,48),(1,14,55),
(1,15,62),(1,16,69),(1,17,76),(1,18,83),(1,19,90),(1,20,97),(1,21,14),
(1,22,21),(1,23,28),(1,24,35),(1,25,42),(1,26,49),(1,27,56),(1,28,63),
(1,29,70),(1,30,77),(1,31,84);

-- 2月 (29天)
INSERT INTO checkin_rule (month, day_number, points) VALUES
(2,1,91),(2,2,98),(2,3,15),(2,4,22),(2,5,29),(2,6,36),(2,7,43),
(2,8,50),(2,9,57),(2,10,64),(2,11,71),(2,12,78),(2,13,85),(2,14,92),
(2,15,99),(2,16,16),(2,17,23),(2,18,30),(2,19,37),(2,20,44),(2,21,51),
(2,22,58),(2,23,65),(2,24,72),(2,25,79),(2,26,86),(2,27,93),(2,28,10),
(2,29,17);

-- 3月 (31天)
INSERT INTO checkin_rule (month, day_number, points) VALUES
(3,1,38),(3,2,45),(3,3,52),(3,4,59),(3,5,66),(3,6,73),(3,7,80),
(3,8,87),(3,9,94),(3,10,11),(3,11,18),(3,12,25),(3,13,32),(3,14,39),
(3,15,46),(3,16,53),(3,17,60),(3,18,67),(3,19,74),(3,20,81),(3,21,88),
(3,22,95),(3,23,12),(3,24,19),(3,25,26),(3,26,33),(3,27,40),(3,28,47),
(3,29,54),(3,30,61),(3,31,68);

-- 4月 (30天)
INSERT INTO checkin_rule (month, day_number, points) VALUES
(4,1,75),(4,2,82),(4,3,89),(4,4,96),(4,5,13),(4,6,20),(4,7,27),
(4,8,34),(4,9,41),(4,10,48),(4,11,55),(4,12,62),(4,13,69),(4,14,76),
(4,15,83),(4,16,90),(4,17,97),(4,18,14),(4,19,21),(4,20,28),(4,21,35),
(4,22,42),(4,23,49),(4,24,56),(4,25,63),(4,26,70),(4,27,77),(4,28,84),
(4,29,91),(4,30,98);

-- 5月 (31天)
INSERT INTO checkin_rule (month, day_number, points) VALUES
(5,1,22),(5,2,29),(5,3,36),(5,4,43),(5,5,50),(5,6,57),(5,7,64),
(5,8,71),(5,9,78),(5,10,85),(5,11,92),(5,12,99),(5,13,16),(5,14,23),
(5,15,30),(5,16,37),(5,17,44),(5,18,51),(5,19,58),(5,20,65),(5,21,72),
(5,22,79),(5,23,86),(5,24,93),(5,25,10),(5,26,17),(5,27,24),(5,28,31),
(5,29,38),(5,30,45),(5,31,52);

-- 6月 (30天)
INSERT INTO checkin_rule (month, day_number, points) VALUES
(6,1,59),(6,2,66),(6,3,73),(6,4,80),(6,5,87),(6,6,94),(6,7,11),
(6,8,18),(6,9,25),(6,10,32),(6,11,39),(6,12,46),(6,13,53),(6,14,60),
(6,15,67),(6,16,74),(6,17,81),(6,18,88),(6,19,95),(6,20,12),(6,21,19),
(6,22,26),(6,23,33),(6,24,40),(6,25,47),(6,26,54),(6,27,61),(6,28,68),
(6,29,75),(6,30,82);

-- 7月 (31天)
INSERT INTO checkin_rule (month, day_number, points) VALUES
(7,1,96),(7,2,13),(7,3,20),(7,4,27),(7,5,34),(7,6,41),(7,7,48),
(7,8,55),(7,9,62),(7,10,69),(7,11,76),(7,12,83),(7,13,90),(7,14,97),
(7,15,14),(7,16,21),(7,17,28),(7,18,35),(7,19,42),(7,20,49),(7,21,56),
(7,22,63),(7,23,70),(7,24,77),(7,25,84),(7,26,91),(7,27,98),(7,28,15),
(7,29,22),(7,30,29),(7,31,36);

-- 8月 (31天)
INSERT INTO checkin_rule (month, day_number, points) VALUES
(8,1,43),(8,2,50),(8,3,57),(8,4,64),(8,5,71),(8,6,78),(8,7,85),
(8,8,92),(8,9,99),(8,10,16),(8,11,23),(8,12,30),(8,13,37),(8,14,44),
(8,15,51),(8,16,58),(8,17,65),(8,18,72),(8,19,79),(8,20,86),(8,21,93),
(8,22,10),(8,23,17),(8,24,24),(8,25,31),(8,26,38),(8,27,45),(8,28,52),
(8,29,59),(8,30,66),(8,31,73);

-- 9月 (30天)
INSERT INTO checkin_rule (month, day_number, points) VALUES
(9,1,80),(9,2,87),(9,3,94),(9,4,11),(9,5,18),(9,6,25),(9,7,32),
(9,8,39),(9,9,46),(9,10,53),(9,11,60),(9,12,67),(9,13,74),(9,14,81),
(9,15,88),(9,16,95),(9,17,12),(9,18,19),(9,19,26),(9,20,33),(9,21,40),
(9,22,47),(9,23,54),(9,24,61),(9,25,68),(9,26,75),(9,27,82),(9,28,89),
(9,29,96),(9,30,13);

-- 10月 (31天)
INSERT INTO checkin_rule (month, day_number, points) VALUES
(10,1,27),(10,2,34),(10,3,41),(10,4,48),(10,5,55),(10,6,62),(10,7,69),
(10,8,76),(10,9,83),(10,10,90),(10,11,97),(10,12,14),(10,13,21),(10,14,28),
(10,15,35),(10,16,42),(10,17,49),(10,18,56),(10,19,63),(10,20,70),(10,21,77),
(10,22,84),(10,23,91),(10,24,98),(10,25,15),(10,26,22),(10,27,29),(10,28,36),
(10,29,43),(10,30,50),(10,31,57);

-- 11月 (30天)
INSERT INTO checkin_rule (month, day_number, points) VALUES
(11,1,64),(11,2,71),(11,3,78),(11,4,85),(11,5,92),(11,6,99),(11,7,16),
(11,8,23),(11,9,30),(11,10,37),(11,11,44),(11,12,51),(11,13,58),(11,14,65),
(11,15,72),(11,16,79),(11,17,86),(11,18,93),(11,19,10),(11,20,17),(11,21,24),
(11,22,31),(11,23,38),(11,24,45),(11,25,52),(11,26,59),(11,27,66),(11,28,73),
(11,29,80),(11,30,87);

-- 12月 (31天)
INSERT INTO checkin_rule (month, day_number, points) VALUES
(12,1,11),(12,2,18),(12,3,25),(12,4,32),(12,5,39),(12,6,46),(12,7,53),
(12,8,60),(12,9,67),(12,10,74),(12,11,81),(12,12,88),(12,13,95),(12,14,12),
(12,15,19),(12,16,26),(12,17,33),(12,18,40),(12,19,47),(12,20,54),(12,21,61),
(12,22,68),(12,23,75),(12,24,82),(12,25,89),(12,26,96),(12,27,13),(12,28,20),
(12,29,27),(12,30,34),(12,31,41);

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

-- 种子: 入站必看(新用户)、全站版规、分类「代码世界」补充版规(示例)
INSERT INTO `forum_notice` (`notice_kind`, `category_scope`, `template_id`, `sidebar_key`, `title`, `subtitle`, `content_markdown`, `body_json`, `sort`, `pin_top`, `publish_state`, `delete_state`) VALUES
(0, 0, 'welcome_hero_right', 'onboarding_welcome', '欢迎来到萌萌论坛', '在这里，发现更美好的生活方式',
 '# 欢迎加入萌萌论坛\n\n请阅读下方说明，遵守社区规范。',
 JSON_OBJECT(
   'highlights', JSON_ARRAY(
     JSON_OBJECT('label', '友好互动', 'labelColor', '#f53f3f', 'text', '拒绝冷漠，在这里分享你的快乐。'),
     JSON_OBJECT('label', '优质内容', 'labelColor', '#00b42a', 'text', '鼓励深度创作，碰撞灵感。')
   ),
   'coverImageUrl', ''
 ),
 0, 1, 1, 0),
(4, 0, 'plain_sections', 'rules_general', '全站发帖与评论规范', '适用于所有分类与版块;各分类可有补充说明。',
 '## 全站规范\n\n请文明发言，禁止人身攻击与违法内容。',
 JSON_OBJECT('sections', JSON_ARRAY()),
 0, 0, 1, 0),
(4, 1, 'plain_sections', 'rules_category_1', '「代码世界」分类版规', '本分类下 Java/Python/前端 等版块适用以下补充规则。',
 '## 本分类补充\n\n技术讨论请标注环境版本，避免无信息提问。',
 JSON_OBJECT('sections', JSON_ARRAY()),
 0, 0, 1, 0);


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
    (4, '安慰券', 3, 0, -1, 1, 0, NULL),
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
(1, 'advanced_llm', '写作配额 · 每日', '高级大模型写作（通义/Gemini/Claude）', 'daily_count', 'advanced_llm', NULL, 'qwen', 50, NULL, 'PRO', 20),
(1, 'image_normal', 'AI 生图 · 每日', 'Z-Image Turbo（普通）', 'daily_count', 'image_normal', 'z-image-turbo', 'qwen', 15, NULL, 'PRO', 30),
(1, 'image_premium', 'AI 生图 · 每日', 'GPT Image 2（进阶）', 'daily_count', 'image_premium', 'gpt-image-2', 'openai', 10, NULL, 'PRO', 40),
(1, 'token_qwen_deep', '本期 Token 配额 · 文本', '通义千问 · 深度', 'token_period', NULL, 'qwen3.7-max', 'qwen', NULL, 500000, 'PRO', 50),
(1, 'token_deepseek_deep', '本期 Token 配额 · 文本', 'DeepSeek · 深度', 'token_period', NULL, 'deepseek-v4-pro', 'deepseek', NULL, 450000, 'PRO', 60),
(1, 'token_gemini_deep', '本期 Token 配额 · 文本', 'Gemini · 深度', 'token_period', NULL, 'gemini-3.1-pro', 'gemini', NULL, 300000, 'PRO', 70),
(1, 'token_claude_haiku', '本期 Token 配额 · 文本', 'Claude Haiku', 'token_period', NULL, 'claude-haiku-4-5', 'claude', NULL, 250000, 'PRO', 80),
(2, 'deepseek_flash', 'DeepSeek · 会员权益', 'DeepSeek V4 Flash', 'unlimited', NULL, 'deepseek-v4-flash', 'deepseek', NULL, NULL, '免费', 10),
(2, 'advanced_llm', '写作配额 · 每日', '高级大模型写作（通义/Gemini/Claude）', 'daily_count', 'advanced_llm', NULL, 'qwen', 300, NULL, 'MAX', 20),
(2, 'image_normal', 'AI 生图 · 每日', 'Z-Image Turbo（普通）', 'daily_count', 'image_normal', 'z-image-turbo', 'qwen', 50, NULL, 'MAX', 30),
(2, 'image_premium', 'AI 生图 · 每日', 'GPT Image 2（进阶）', 'daily_count', 'image_premium', 'gpt-image-2', 'openai', 50, NULL, 'MAX', 40),
(2, 'token_qwen_deep', '本期 Token 配额 · 文本', '通义千问 · 深度', 'token_period', NULL, 'qwen3.7-max', 'qwen', NULL, 2000000, 'MAX', 50),
(2, 'token_deepseek_deep', '本期 Token 配额 · 文本', 'DeepSeek · 深度', 'token_period', NULL, 'deepseek-v4-pro', 'deepseek', NULL, 1300000, 'MAX', 60),
(2, 'token_gemini_deep', '本期 Token 配额 · 文本', 'Gemini · 深度', 'token_period', NULL, 'gemini-3.1-pro', 'gemini', NULL, 800000, 'MAX', 70),
(2, 'token_claude_haiku', '本期 Token 配额 · 文本', 'Claude Haiku', 'token_period', NULL, 'claude-haiku-4-5', 'claude', NULL, 500000, 'MAX', 80),
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