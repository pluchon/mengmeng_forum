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

    // 红方消行数：竞速的胜负先看它，分数只做决胜
    private Integer redLines;

    // 蓝方消行数
    private Integer blueLines;

    // 本局剩余毫秒。
    //
    // 下发剩余量而不是终点时刻：终点时刻是服务端时钟，客户端时钟偏几分钟倒计时就废了。
    // 每帧都会带上，前端本地递减、收到新状态时校正即可。
    private Long remainingMs;

    // PK 进度条左侧占比 0 100
    private Integer pkBarLeftPercent;

    // 对手展示信息
    private GobangRoomParticipantVO opponentPlayer;

    // 玩家1展示信息
    private GobangRoomParticipantVO player1;

    // 玩家2展示信息
    private GobangRoomParticipantVO player2;
    // 观战人数。只报数量不报名单：以前把每个观战者的昵称头像战绩推给房里所有人
    private Integer spectatorCount;

    // 房间在线人数
    private Integer roomOnlineCount;

    // 最近聊天记录
    private List<TetrisChatVO> recentChats;
}
