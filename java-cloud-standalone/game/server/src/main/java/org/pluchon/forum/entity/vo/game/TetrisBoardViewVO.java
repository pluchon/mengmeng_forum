package org.pluchon.forum.entity.vo.game;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 俄罗斯方块棋盘视图，可按角色隐藏 hold/next
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TetrisBoardViewVO {

    // 棋盘矩阵
    private String[][] matrix;

    // 当前方块
    private TetrisCurPieceVO cur;

    // 幽灵落点
    private TetrisCurPieceVO ghost;

    // 下一个方块
    private String nextType;

    // 暂存方块
    private String holdType;

    // 分数
    private Integer points;

    // 消行数
    private Integer linesCleared;

    // 当前连击数（连续消行放置次数，零消后归零）
    private Integer combo;

    // 是否已结束
    private Boolean gameOver;

    // 是否展示 hold/next
    private Boolean revealHoldNext;
}
