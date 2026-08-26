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

    // 当前小局是否结束
    private Boolean roundFinished;

    // 当前小局胜方用户 ID，小局平局为空
    private Long roundWinnerUserId;

    // 当前小局结束原因
    private String roundEndReason;

    // 当前小局三连坐标，非三连结束为空
    private List<JinziBoardPointVO> winningLine;

    // 黑方胜局
    private Integer blackWins;

    // 白方胜局
    private Integer whiteWins;

    // 平局小局数
    private Integer drawRounds;

    // 当前小局轮次
    private Integer currentRound;

    // 整场比赛是否结束
    private Boolean matchFinished;

    // 整场比赛获胜者 ID
    private Long matchWinnerUserId;

    // 整场比赛结束原因
    private String matchEndReason;
}
