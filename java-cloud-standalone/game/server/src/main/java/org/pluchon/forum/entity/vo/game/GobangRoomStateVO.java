package org.pluchon.forum.entity.vo.game;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// 五子棋房间状态响应
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GobangRoomStateVO {

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

    // 胜方用户 ID
    private Long winnerUserId;

    // 结束原因
    private String endReason;

    // 黑方剩余局时毫秒
    private Long blackRemainingMs;

    // 白方剩余局时毫秒
    private Long whiteRemainingMs;

    // 当前步剩余毫秒
    private Long moveRemainingMs;

    // 当前用户是否观众
    private Boolean spectator;

    // 是否 AI 对局
    private Boolean aiRoom;

    // AI 是否正在思考
    private Boolean aiThinking;

    // 胜利五连坐标，未结束为空
    private List<GobangBoardPointVO> winningLine;

    // 黑方展示信息
    private GobangRoomParticipantVO blackPlayer;

    // 白方展示信息
    private GobangRoomParticipantVO whitePlayer;

    // 当前用户视角下的对手展示信息
    private GobangRoomParticipantVO opponentPlayer;
    // 当前观战人数
    // 观战人数。只报数量不报名单：名单会把观战者的昵称头像战绩推给房里所有人
    private Integer spectatorCount;

    // 当前房间 WebSocket 在线人数
    private Integer roomOnlineCount;
}
