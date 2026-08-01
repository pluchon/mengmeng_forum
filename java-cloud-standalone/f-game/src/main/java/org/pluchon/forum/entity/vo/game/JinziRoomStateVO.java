package org.pluchon.forum.entity.vo.game;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// 井字棋房间状态响应，井字棋不开放观战席
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JinziRoomStateVO {

    // 房间 ID
    private String roomId;

    // 当前用户 ID
    private Long thisUserId;

    // 对手用户 ID
    private Long opponentUserId;

    // 黑方用户 ID
    private Long blackUserId;

    // 白方用户 ID
    private Long whiteUserId;

    // 当前回合用户 ID
    private Long currentTurnUserId;

    // 房间状态：WAITING / PLAYING / FINISHED
    private String roomStatus;

    // 棋盘快照，0空 1黑 2白
    private int[][] board;

    // 胜方用户 ID，平局为空
    private Long winnerUserId;

    // 结束原因：LINE / DRAW / SURRENDER / DISCONNECT / TIMEOUT
    private String endReason;

    // 黑方剩余局时毫秒
    private Long blackRemainingMs;

    // 白方剩余局时毫秒
    private Long whiteRemainingMs;

    // 当前步剩余毫秒
    private Long moveRemainingMs;

    // 服务器当前时间戳
    private Long serverNowMs;

    // 三连坐标，非三连结束为空
    private List<JinziBoardPointVO> winningLine;

    // 黑方展示信息
    private GobangRoomParticipantVO blackPlayer;

    // 白方展示信息
    private GobangRoomParticipantVO whitePlayer;

    // 当前用户视角下的对手展示信息
    private GobangRoomParticipantVO opponentPlayer;

    // 当前房间 WebSocket 在线人数
    private Integer roomOnlineCount;
}
