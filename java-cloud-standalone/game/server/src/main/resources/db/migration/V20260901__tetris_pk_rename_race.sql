-- PK 改竞速后，游戏目录里的显示名还是旧的。
-- game_code 保持 tetris_pk 不变：历史对局记录、排行榜、统计都挂在这个码上。
UPDATE `game_definition`
   SET `game_name` = '俄罗斯方块竞速'
 WHERE `game_code` = 'tetris_pk'
   AND `game_name` = '俄罗斯方块 PK';
