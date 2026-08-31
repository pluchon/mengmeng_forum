-- 俄罗斯方块 PK 改为竞速：胜负先看消行数，分数只做决胜。
-- 对局记录以前只存分数，回头查一局为什么这么判就没有依据了。
ALTER TABLE `game_tetris_pk_match_record`
  ADD COLUMN `player1_lines` int NOT NULL DEFAULT '0' COMMENT '玩家1消行数' AFTER `player2_score`,
  ADD COLUMN `player2_lines` int NOT NULL DEFAULT '0' COMMENT '玩家2消行数' AFTER `player1_lines`;
