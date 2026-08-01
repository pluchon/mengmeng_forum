package org.pluchon.forum.entity.vo.game;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// 俄罗斯方块 PK 房间状态响应
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TetrisRoomStateVO {

    // 房间 ID
    private String roomId;

    // 当前用户 ID
    private Long thisUserId;

    // 对手用户 ID
    private Long opponentUserId;

    // 玩家1用户 ID
    private Long player1UserId;

    // 玩家2用户 ID
    private Long player2UserId;

    // 红方用户 ID
    private Long redUserId;

    // 蓝方用户 ID
    private Long blueUserId;

    // 房间状态
    private String roomStatus;

    // 胜方用户 ID
    private Long winnerUserId;

    // 结束原因
    private String endReason;

    // 当前用户是否观众
    private Boolean spectator;

    // 我方棋盘
    private TetrisBoardViewVO myBoard;

    // 对手棋盘
    private TetrisBoardViewVO opponentBoard;

    // 红方分数
    private Integer redScore;

    // 蓝方分数
    private Integer blueScore;

    // PK 进度条左侧占比（0-100）
    private Integer pkBarLeftPercent;

    // 对手展示信息
    private GobangRoomParticipantVO opponentPlayer;

    // 玩家1展示信息
    private GobangRoomParticipantVO player1;

    // 玩家2展示信息
    private GobangRoomParticipantVO player2;

    // 观战列表
    private List<GobangRoomParticipantVO> spectators;

    // 观战人数
    private Integer spectatorCount;

    // 房间在线人数
    private Integer roomOnlineCount;

    // 服务器时间戳
    private Long serverNowMs;

    // 最近聊天记录
    private List<TetrisChatVO> recentChats;
}
