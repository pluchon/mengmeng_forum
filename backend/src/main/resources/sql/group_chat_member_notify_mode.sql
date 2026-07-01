-- 群聊成员提醒模式：0正常提醒 1仅@提醒 2完全不提醒
ALTER TABLE `group_chat_member`
    ADD COLUMN `notify_mode` tinyint NOT NULL DEFAULT 0 COMMENT '提醒模式: 0正常 1仅@提醒 2完全不提醒'
    AFTER `remark_name`;
