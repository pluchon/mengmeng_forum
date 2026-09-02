-- 看板娘牵线 · 第二期：匹配与双向握手
--
-- 隐私模型：一条匹配在双方都点头之前，任何一方都看不到对方是谁。
-- 邀约通知里只写 reason（双方看到的是同一句交集描述），不含任何身份信息。
-- 任何一方 DECLINED，另一方永远不会知道这次匹配发生过——被拒的人不会受伤，
-- 因为他根本不知道。
CREATE TABLE IF NOT EXISTS `forum_mascot_intent_match` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `intent_a_id` bigint NOT NULL COMMENT '意愿A',
  `intent_b_id` bigint NOT NULL COMMENT '意愿B',
  `user_a_id` bigint NOT NULL COMMENT '意愿A的主人',
  `user_b_id` bigint NOT NULL COMMENT '意愿B的主人',
  `reason` varchar(200) NOT NULL COMMENT '交集描述；双方看到的是同一句，不含身份信息',
  `a_state` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING|ACCEPTED|DECLINED',
  `b_state` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING|ACCEPTED|DECLINED',
  `state` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING|CONNECTED|CLOSED',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `delete_state` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0=正常 1=已删除',
  PRIMARY KEY (`id`),
  -- 同一对意愿只配一次，避免定时任务重复推
  UNIQUE KEY `uk_mascot_match_pair` (`intent_a_id`,`intent_b_id`),
  KEY `idx_mascot_match_user_a` (`user_a_id`,`state`,`delete_state`),
  KEY `idx_mascot_match_user_b` (`user_b_id`,`state`,`delete_state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='看板娘牵线匹配';
