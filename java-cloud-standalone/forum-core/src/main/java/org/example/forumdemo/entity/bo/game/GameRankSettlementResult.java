package org.example.forumdemo.entity.bo.game;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 游戏排位结算结果
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameRankSettlementResult {

    // 是否计入排位分
    private Boolean ranked;

    // 是否平局
    private Boolean draw;

    // 胜方变化
    private GameRankPlayerChange winnerChange;

    // 败方变化
    private GameRankPlayerChange loserChange;
}
