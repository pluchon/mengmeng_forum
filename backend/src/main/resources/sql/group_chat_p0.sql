-- 群聊 P0：创作者认证字段与群聊基础表
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
