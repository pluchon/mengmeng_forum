-- 群加入申请与邀请：0申请加群 1邀请入群；0待处理 1已同意 2已拒绝
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
