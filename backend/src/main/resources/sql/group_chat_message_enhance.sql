-- 群聊消息增强：群图片、回复引用、成员群内备注
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
