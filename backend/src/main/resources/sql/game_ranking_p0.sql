-- 游戏排位 P0：记录胜负双方真实排位分变化
ALTER TABLE `game_gobang_match_record`
    ADD COLUMN `winner_score_delta` int NOT NULL DEFAULT 0 COMMENT '胜方本局排位分变化' AFTER `score_delta`,
    ADD COLUMN `loser_score_delta` int NOT NULL DEFAULT 0 COMMENT '败方本局排位分变化，负数' AFTER `winner_score_delta`;

ALTER TABLE `game_jinzi_match_record`
    ADD COLUMN `winner_score_delta` int NOT NULL DEFAULT 0 COMMENT '胜方本局排位分变化' AFTER `score_delta`,
    ADD COLUMN `loser_score_delta` int NOT NULL DEFAULT 0 COMMENT '败方本局排位分变化，负数' AFTER `winner_score_delta`;

ALTER TABLE `game_tetris_pk_match_record`
    ADD COLUMN `winner_score_delta` int NOT NULL DEFAULT 0 COMMENT '胜方本局排位分变化' AFTER `score_delta`,
    ADD COLUMN `loser_score_delta` int NOT NULL DEFAULT 0 COMMENT '败方本局排位分变化，负数' AFTER `winner_score_delta`;
