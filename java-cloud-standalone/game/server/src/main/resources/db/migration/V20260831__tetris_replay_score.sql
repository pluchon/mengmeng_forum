-- 重放校验灰度：先记录重放算出的分数与自报分数的差异，不拒绝请求。
-- 观察确认零误判后，再把 validation_status=MISMATCH 变成拒绝条件。
ALTER TABLE `game_tetris_record`
  ADD COLUMN `replay_score` int DEFAULT NULL COMMENT '服务端重放算出的分数; NULL 表示未校验' AFTER `validation_status`;
