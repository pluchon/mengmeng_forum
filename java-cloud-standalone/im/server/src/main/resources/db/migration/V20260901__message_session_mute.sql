-- 私信免打扰。
--
-- 继续复用 message_session_visibility：归档、置顶、免打扰都是「某用户对某会话的
-- 个人设置」，同属一行，不必为每个开关新建一张表。
--
-- 群聊那边已有 group_chat_member.notify_mode（NORMAL/MENTION_ONLY/NONE），
-- 私信没有「只看@我」的语义，所以这里只要一个开关，对齐群聊的 NONE。
ALTER TABLE `message_session_visibility`
  ADD COLUMN `muted_state` tinyint NOT NULL DEFAULT '0' COMMENT '免打扰: 0否 1是' AFTER `pinned_at`;
