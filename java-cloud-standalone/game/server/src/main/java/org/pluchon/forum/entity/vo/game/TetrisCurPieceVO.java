package org.pluchon.forum.entity.vo.game;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 俄罗斯方块当前下落方块视图
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TetrisCurPieceVO {

    // 方块类型
    private String type;

    // 棋盘坐标 row, col
    private int[] xy;

    // 形状矩阵
    private int[][] shape;
}
