-- 群加入申请查看状态：点开进群申请整合栏后清除红点，不影响批准/拒绝状态
ALTER TABLE `group_chat_join_request`
    ADD COLUMN `owner_read_state` tinyint NOT NULL DEFAULT 0 COMMENT '群主查看状态: 0未读 1已读'
    AFTER `status`;
