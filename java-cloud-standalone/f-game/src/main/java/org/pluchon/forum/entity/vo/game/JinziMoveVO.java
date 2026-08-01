package org.pluchon.forum.entity.vo.game;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// 井字棋落子广播响应
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JinziMoveVO {

    // 落子用户 ID
    private Long userId;

    // 行号
    private Integer row;

    // 列号
    private Integer col;

    // 棋子颜色：1黑 2白
    private Integer chess;

    // 下一回合用户 ID
    private Long nextTurnUserId;

    // 胜方用户 ID，平局或未结束为空
    private Long winnerUserId;

    // 结束原因，未结束为空
    private String endReason;

    // 三连坐标，非三连结束为空
    private List<JinziBoardPointVO> winningLine;
}
