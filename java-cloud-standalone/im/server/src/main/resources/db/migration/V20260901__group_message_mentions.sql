-- 群聊 @ 提醒改成按用户 ID 判定。
--
-- 原来只靠 content 里的「@昵称」做字符串匹配，有两个躲不掉的毛病：
-- 昵称互为前缀时会误伤（「@小明明」里含有「@小明」），昵称改过之后旧消息的 @ 也就失效了。
-- 发送时前端本来就知道被 @ 的是谁，把 ID 一并存下来即可。
--
-- 存成逗号分隔的字符串而不是关联表：一条消息 @ 的人很少，也从不需要按被 @ 者反查。
ALTER TABLE `group_chat_message`
  ADD COLUMN `mentioned_user_ids` varchar(512) DEFAULT NULL COMMENT '被@的用户ID, 逗号分隔; NULL 表示没有或为历史数据' AFTER `content`;
