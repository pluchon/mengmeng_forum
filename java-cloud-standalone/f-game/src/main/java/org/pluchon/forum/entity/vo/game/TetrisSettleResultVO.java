package org.pluchon.forum.entity.vo.game;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 俄罗斯方块结算结果
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TetrisSettleResultVO {

    // 记录 ID
    private Long recordId;

    // 采纳分数
    private Integer score;

    // 更新后最高分
    private Integer bestScore;

    // 本次论坛积分
    private Integer forumPointsAwarded;

    // 是否刷新个人最高
    private Boolean newBest;
}
