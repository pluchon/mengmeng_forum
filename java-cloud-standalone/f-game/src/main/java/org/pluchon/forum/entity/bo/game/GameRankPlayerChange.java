package org.pluchon.forum.entity.bo.game;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.pluchon.forum.entity.vo.game.GameRankInfoVO;

// 单个玩家本局排位变化
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameRankPlayerChange {

    // 用户 ID
    private Long userId;

    // 赛前分数
    private Integer beforeScore;

    // 赛后分数
    private Integer afterScore;

    // 本局分数变化，胜方为正，败方为负
    private Integer delta;

    // 赛前段位
    private GameRankInfoVO beforeRank;

    // 赛后段位
    private GameRankInfoVO afterRank;
}
