package org.example.forumdemo.service.impl.game;

import lombok.Getter;
import lombok.Setter;
import org.example.forumdemo.entity.vo.game.JinziBoardPointVO;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// 井字棋内存房间状态，服务端持有权威 3x3 棋盘和实时计时
@Getter
public class JinziRoom {

    // 房间 ID
    private final String roomId = UUID.randomUUID().toString();

    // 黑方用户 ID
    private final Long blackUserId;

    // 白方用户 ID
    private final Long whiteUserId;

    // 棋盘：0空 1黑 2白
    private final int[][] board = new int[GameConstants.JINZI_BOARD_SIZE][GameConstants.JINZI_BOARD_SIZE];

    // 房间创建时间
    private final Date startedAt = new Date();

    // 当前回合用户 ID
    @Setter
    private Long currentTurnUserId;

    // 房间状态
    @Setter
    private String roomStatus = GameConstants.ROOM_WAITING;

    // 胜方用户 ID，平局为空
    @Setter
    private Long winnerUserId;

    // 结束原因
    @Setter
    private String endReason;

    // 三连坐标
    @Setter
    private List<JinziBoardPointVO> winningLine = new ArrayList<>();

    // 黑方剩余局时毫秒
    @Setter
    private long blackRemainingMs = GameConstants.JINZI_GAME_TIME_MS;

    // 白方剩余局时毫秒
    @Setter
    private long whiteRemainingMs = GameConstants.JINZI_GAME_TIME_MS;

    // 当前步开始时间戳
    @Setter
    private long turnStartedAtMs = System.currentTimeMillis();

    // 断线重连截止时间：userId -> 截止时间戳
    private final ConcurrentHashMap<Long, Long> disconnectDeadlines = new ConcurrentHashMap<>();

    public JinziRoom(Long blackUserId, Long whiteUserId) {
        this.blackUserId = blackUserId;
        this.whiteUserId = whiteUserId;
        this.currentTurnUserId = blackUserId;
    }

    public boolean contains(Long userId) {
        return blackUserId.equals(userId) || whiteUserId.equals(userId);
    }

    public Long opponentOf(Long userId) {
        if (blackUserId.equals(userId)) {
            return whiteUserId;
        }
        if (whiteUserId.equals(userId)) {
            return blackUserId;
        }
        return null;
    }

    public int chessOf(Long userId) {
        return blackUserId.equals(userId) ? 1 : 2;
    }

    public long currentTurnRemainingMs(long now) {
        long used = Math.max(0, now - turnStartedAtMs);
        long totalLeft = remainingGameMs(currentTurnUserId, now);
        return Math.max(0, Math.min(totalLeft, GameConstants.JINZI_MOVE_TIME_MS - used));
    }

    public long remainingGameMs(Long userId, long now) {
        if (userId == null) {
            return 0;
        }
        long remaining = userId.equals(blackUserId) ? blackRemainingMs : whiteRemainingMs;
        if (userId.equals(currentTurnUserId)) {
            remaining -= Math.max(0, now - turnStartedAtMs);
        }
        return Math.max(0, remaining);
    }

    public void consumeTurnTime(long now) {
        if (currentTurnUserId == null) {
            return;
        }
        long used = Math.max(0, now - turnStartedAtMs);
        if (currentTurnUserId.equals(blackUserId)) {
            blackRemainingMs = Math.max(0, blackRemainingMs - used);
        } else if (currentTurnUserId.equals(whiteUserId)) {
            whiteRemainingMs = Math.max(0, whiteRemainingMs - used);
        }
        turnStartedAtMs = now;
    }

    public int[][] copyBoard() {
        int[][] copy = new int[GameConstants.JINZI_BOARD_SIZE][GameConstants.JINZI_BOARD_SIZE];
        for (int i = 0; i < GameConstants.JINZI_BOARD_SIZE; i++) {
            copy[i] = Arrays.copyOf(board[i], GameConstants.JINZI_BOARD_SIZE);
        }
        return copy;
    }
}
