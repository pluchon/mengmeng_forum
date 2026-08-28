package org.pluchon.forum.entity.bo.game;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 游戏排位结算结果
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameRankSettlementResult {

    // 胜方变化
    private GameRankPlayerChange winnerChange;

    // 败方变化
    private GameRankPlayerChange loserChange;
}
