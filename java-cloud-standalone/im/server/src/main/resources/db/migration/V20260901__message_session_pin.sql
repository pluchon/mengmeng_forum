-- 私信会话置顶。
--
-- 复用 message_session_visibility 而不是新建表：这张表的语义本来就是
-- 「某用户对某会话的个人设置」，唯一键已经是 (user_id, peer_user_id)，
-- 归档与置顶天然属于同一行，不必再维护两处之间的一致性。
--
-- 只用一个 pinned_at 表达置顶：是否置顶就是它非空，同时它本身就是排序依据，
-- 不会出现「布尔说已置顶、时间却是空」这种自相矛盾的状态。
ALTER TABLE `message_session_visibility`
  ADD COLUMN `pinned_at` datetime DEFAULT NULL COMMENT '置顶时刻; NULL 表示未置顶' AFTER `hidden_state`,
  ADD KEY `idx_message_session_visibility_user_pinned` (`user_id`,`pinned_at`);
