package org.example.forumdemo.service.impl.game;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import org.example.forumdemo.entity.vo.game.GobangBoardPointVO;

// 五子棋内存房间状态，服务端持有权威棋盘和实时计时
@Getter
public class GobangRoom {

    // 房间 ID
    private final String roomId = UUID.randomUUID().toString();

    // 黑方用户 ID
    private final Long blackUserId;

    // 白方用户 ID
    private final Long whiteUserId;

    // 棋盘：0空 1黑 2白
    private final int[][] board = new int[GameConstants.BOARD_SIZE][GameConstants.BOARD_SIZE];

    // 房间创建时间
    private final Date startedAt = new Date();

    // 当前回合用户 ID
    @Setter
    private Long currentTurnUserId;

    // 房间状态
    @Setter
    private String roomStatus = GameConstants.ROOM_WAITING;

    // 胜方用户 ID
    @Setter
    private Long winnerUserId;

    // 结束原因
    @Setter
    private String endReason;

    // 胜利五连坐标
    @Setter
    private List<GobangBoardPointVO> winningLine = new ArrayList<>();

    // 黑方剩余局时毫秒
    @Setter
    private long blackRemainingMs = GameConstants.GAME_TIME_MS;

    // 白方剩余局时毫秒
    @Setter
    private long whiteRemainingMs = GameConstants.GAME_TIME_MS;

    // 当前步开始时间戳
    @Setter
    private long turnStartedAtMs = System.currentTimeMillis();

    // 是否 AI 房
    @Setter
    private boolean aiRoom;

    // AI 展示名：Python 模型成功返回时更新，否则显示本地兜底策略
    @Setter
    private String aiModelName = "DeepSeek V4 Flash · 本地策略兜底";

    // 房间内聊天消息
    private final List<String> chatMessages = new ArrayList<>();

    // 观众进入时间：userId -> 进入时间戳，用于前端按加入顺序展示观战席
    private final ConcurrentHashMap<Long, Long> spectatorJoinedAt = new ConcurrentHashMap<>();

    // 断线重连截止时间：userId -> 截止时间戳
    private final ConcurrentHashMap<Long, Long> disconnectDeadlines = new ConcurrentHashMap<>();

    public GobangRoom(Long blackUserId, Long whiteUserId) {
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
        return Math.max(0, Math.min(totalLeft, GameConstants.MOVE_TIME_MS - used));
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
        int[][] copy = new int[GameConstants.BOARD_SIZE][GameConstants.BOARD_SIZE];
        for (int i = 0; i < GameConstants.BOARD_SIZE; i++) {
            copy[i] = Arrays.copyOf(board[i], GameConstants.BOARD_SIZE);
        }
        return copy;
    }
}
