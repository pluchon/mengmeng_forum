-- 音乐氛围标签沉淀表。
-- 在此之前候选集是 Nacos 里的一份静态配置，AI 审核补出来的新标签只写进歌曲自己的
-- mood_tags JSON，筛选栏永远只有那几个内置词，AI 造的标签谁也看不见、筛不到。
-- 这张表把三个来源（内置 / AI 补充 / 创作者创建）收进同一个池子。

CREATE TABLE IF NOT EXISTS `music_mood_tag` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(16) NOT NULL COMMENT '标签名',
  `source` varchar(8) NOT NULL DEFAULT 'AI' COMMENT '来源 BUILTIN内置 AI补充 USER创作者创建',
  `create_user_id` bigint DEFAULT NULL COMMENT '创建者，仅 source=USER 有值',
  `use_count` int NOT NULL DEFAULT '0' COMMENT '被歌曲使用次数，筛选栏按此降序',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否可用 0否 1是',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除: 0否 1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_music_mood_tag_name` (`name`),
  KEY `idx_music_mood_tag_rank` (`enabled`,`delete_state`,`use_count`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='音乐氛围标签池';

-- 原 forum.music.mood-tags 的内置词作为种子。「热门」是默认态不是真实氛围，不入池。
INSERT INTO `music_mood_tag` (`name`, `source`, `use_count`) VALUES
  ('治愈', 'BUILTIN', 0),
  ('清新', 'BUILTIN', 0),
  ('浪漫', 'BUILTIN', 0),
  ('轻松', 'BUILTIN', 0),
  ('深夜', 'BUILTIN', 0),
  ('轻音乐', 'BUILTIN', 0),
  ('适合配图', 'BUILTIN', 0)
ON DUPLICATE KEY UPDATE `source` = VALUES(`source`);

-- 回填存量：把已发布歌曲 mood_tags 里的标签收进池子并统计使用次数。
-- 「热门」是筛选栏的默认态不是氛围，排除掉。
INSERT INTO `music_mood_tag` (`name`, `source`, `use_count`)
SELECT t.tag, 'AI', COUNT(*)
  FROM `user_music` m
  JOIN JSON_TABLE(m.mood_tags, '$[*]' COLUMNS (tag VARCHAR(16) PATH '$')) t
 WHERE m.status = 2
   AND m.delete_state = 0
   AND JSON_VALID(m.mood_tags)
   AND t.tag IS NOT NULL
   AND t.tag <> ''
   AND t.tag <> '热门'
 GROUP BY t.tag
ON DUPLICATE KEY UPDATE `use_count` = `use_count` + VALUES(`use_count`);
