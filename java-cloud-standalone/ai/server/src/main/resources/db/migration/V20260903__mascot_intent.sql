-- 看板娘牵线：意愿池（第一期只做「识别 + 用户确认 + 入池」，不含匹配）
--
-- 为什么不塞进 forum_mascot_memory：
--   记忆是「你是谁」——一人一行、覆盖式、无期限、只喂给你自己的对话；
--   意愿是「你现在想要什么」——一人多行、会过期、将来可能被匹配给另一个人看。
-- 混在一起，第一个后果就是记忆被匹配逻辑读出去，等于把「你是谁」暴露给陌生人。
--
-- 隐私前提：只有用户在卡片上点过头的那句话才会写进这张表。闲聊内容一概不进。
CREATE TABLE IF NOT EXISTS `forum_mascot_intent` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `intent_kind` varchar(16) NOT NULL COMMENT 'seek=想找人 offer=能帮人',
  `intent_text` varchar(200) NOT NULL COMMENT '用户确认过的那句意愿描述',
  `source_session_id` bigint DEFAULT NULL COMMENT '来自哪个看板娘会话，用于「同一会话不重复问」',
  `state` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE|MATCHED|EXPIRED|CANCELLED',
  `expire_at` datetime NOT NULL COMMENT '过期时间；到期自动作废，避免拿着半年前的需求去牵线',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `delete_state` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0=正常 1=已删除',
  PRIMARY KEY (`id`),
  KEY `idx_mascot_intent_user` (`user_id`,`state`,`delete_state`),
  KEY `idx_mascot_intent_session` (`source_session_id`,`delete_state`),
  KEY `idx_mascot_intent_pool` (`state`,`expire_at`,`delete_state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='看板娘牵线意愿池';
