package org.pluchon.forum.entity.bo.game;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 单个玩家本局排位变化
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameRankPlayerChange {

    // 用户 ID
    private Long userId;

    // 本局分数变化，胜方为正，败方为负
    private Integer delta;
}
