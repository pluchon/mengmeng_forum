package org.example.forumdemo.entity.vo.game;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// 游戏房间 Redis 快照，用于多机重连和实时事件丢失后的兜底恢复
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameRoomSnapshotVO {

    // 游戏编码
    private String gameCode;

    // 房间 ID
    private String roomId;

    // 房间状态
    private String roomStatus;

    // 黑方用户 ID
    private Long blackUserId;

    // 白方用户 ID
    private Long whiteUserId;

    // 当前回合用户 ID
    private Long currentTurnUserId;

    // 当前棋盘
    private int[][] board;

    // 棋盘版本，当前使用落子数
    private Integer boardVersion;

    // 胜方用户 ID
    private Long winnerUserId;

    // 结束原因
    private String endReason;

    // 胜利五连坐标
    private List<GobangBoardPointVO> winningLine;

    // 黑方剩余局时
    private Long blackRemainingMs;

    // 白方剩余局时
    private Long whiteRemainingMs;

    // 当前步剩余时间
    private Long moveRemainingMs;

    // 快照生成时间
    private Long snapshotAtMs;
}
