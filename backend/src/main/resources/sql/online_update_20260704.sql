-- 线上增量更新合并包：群聊、漂流瓶、游戏排位
-- 执行说明：
-- 1. 仅用于已经存在基础库表的线上环境。
-- 2. 只执行一次；其中部分 ALTER TABLE / CREATE TABLE 未做重复执行兼容。
-- 3. 执行前请先备份线上数据库。

SET NAMES utf8mb4;

-- =========================================================
-- 1. 群聊 P0：创作者认证字段与群聊基础表
-- 来源：group_chat_p0.sql
-- =========================================================
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
    `role` tinyint NOT NULL DEFAULT 1 COMMENT '角色: 0群主 1成员',
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
    `message_type` tinyint NOT NULL DEFAULT 0 COMMENT '消息类型: 0文本 1表情 9系统',
    `content` varchar(500) NOT NULL COMMENT '消息内容',
    `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态: 0正常 1举报隐藏 2删除',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `delete_state` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除: 0否 1是',
    PRIMARY KEY (`id`),
    KEY `idx_group_message` (`group_id`, `delete_state`, `id`),
    KEY `idx_sender_time` (`sender_user_id`, `delete_state`, `create_time`)
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

-- =========================================================
-- 2. 群聊消息增强：群图片、回复引用、成员群内备注
-- 来源：group_chat_message_enhance.sql
-- =========================================================
ALTER TABLE `group_chat_member`
    ADD COLUMN `remark_name` varchar(24) DEFAULT NULL COMMENT '群内备注昵称'
    AFTER `role`;

ALTER TABLE `group_chat_message`
    MODIFY COLUMN `message_type` tinyint NOT NULL DEFAULT 0 COMMENT '消息类型: 0文本 1表情 2图片 9系统',
    ADD COLUMN `reply_message_id` bigint DEFAULT NULL COMMENT '回复的群消息ID'
    AFTER `content`,
    ADD COLUMN `reply_sender_name` varchar(64) DEFAULT NULL COMMENT '被回复消息发送者昵称快照'
    AFTER `reply_message_id`,
    ADD COLUMN `reply_content` varchar(200) DEFAULT NULL COMMENT '被回复消息内容快照'
    AFTER `reply_sender_name`,
    ADD KEY `idx_group_reply_message` (`reply_message_id`);

-- =========================================================
-- 3. 群聊成员提醒模式
-- 来源：group_chat_member_notify_mode.sql
-- =========================================================
ALTER TABLE `group_chat_member`
    ADD COLUMN `notify_mode` tinyint NOT NULL DEFAULT 0 COMMENT '提醒模式: 0正常 1仅@提醒 2完全不提醒'
    AFTER `remark_name`;

-- =========================================================
-- 4. 群加入申请与邀请
-- 来源：group_chat_join_request.sql
-- =========================================================
ALTER TABLE `group_chat_member`
    MODIFY COLUMN `role` tinyint NOT NULL DEFAULT 1 COMMENT '角色: 0群主 1成员 2管理员';

CREATE TABLE `group_chat_join_request` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '申请ID',
    `group_id` bigint NOT NULL COMMENT '群聊ID',
    `target_user_id` bigint NOT NULL COMMENT '目标用户ID',
    `initiator_user_id` bigint NOT NULL COMMENT '发起人用户ID',
    `owner_user_id` bigint NOT NULL COMMENT '群主用户ID',
    `request_type` tinyint NOT NULL COMMENT '请求类型: 0申请加群 1邀请入群',
    `status` tinyint NOT NULL DEFAULT 0 COMMENT '处理状态: 0待处理 1已同意 2已拒绝',
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

-- =========================================================
-- 5. 群加入申请查看状态
-- 来源：group_chat_join_request_read_state.sql
-- =========================================================
ALTER TABLE `group_chat_join_request`
    ADD COLUMN `owner_read_state` tinyint NOT NULL DEFAULT 0 COMMENT '群主查看状态: 0未读 1已读'
    AFTER `status`;

-- =========================================================
-- 6. 漂流瓶 P0：匿名树洞、打捞、评论与举报
-- 来源：drift_bottle_p0.sql
-- =========================================================
CREATE TABLE IF NOT EXISTS `drift_bottle` (
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

CREATE TABLE IF NOT EXISTS `drift_bottle_comment` (
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

CREATE TABLE IF NOT EXISTS `drift_bottle_pick_log` (
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

CREATE TABLE IF NOT EXISTS `drift_bottle_report` (
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

-- =========================================================
-- 7. 游戏排位 P0：记录胜负双方真实排位分变化
-- 来源：game_ranking_p0.sql
-- =========================================================
ALTER TABLE `game_gobang_match_record`
    ADD COLUMN `winner_score_delta` int NOT NULL DEFAULT 0 COMMENT '胜方本局排位分变化' AFTER `score_delta`,
    ADD COLUMN `loser_score_delta` int NOT NULL DEFAULT 0 COMMENT '败方本局排位分变化，负数' AFTER `winner_score_delta`;

ALTER TABLE `game_jinzi_match_record`
    ADD COLUMN `winner_score_delta` int NOT NULL DEFAULT 0 COMMENT '胜方本局排位分变化' AFTER `score_delta`,
    ADD COLUMN `loser_score_delta` int NOT NULL DEFAULT 0 COMMENT '败方本局排位分变化，负数' AFTER `winner_score_delta`;

ALTER TABLE `game_tetris_pk_match_record`
    ADD COLUMN `winner_score_delta` int NOT NULL DEFAULT 0 COMMENT '胜方本局排位分变化' AFTER `score_delta`,
    ADD COLUMN `loser_score_delta` int NOT NULL DEFAULT 0 COMMENT '败方本局排位分变化，负数' AFTER `winner_score_delta`;
