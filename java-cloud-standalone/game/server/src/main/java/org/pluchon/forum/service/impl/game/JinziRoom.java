package org.pluchon.forum.service.impl.game;

import lombok.Getter;
import lombok.Setter;
import org.pluchon.forum.entity.vo.game.JinziBoardPointVO;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// 井字棋内存房间状态，服务端持有权威 3x3 棋盘和实时计时
@Getter
public class JinziRoom {

    public static final int TARGET_WINS = 3;
    public static final int MAX_DRAW_ROUNDS = 3;
    public static final int MAX_TOTAL_ROUNDS = 7;

    // 房间 ID 6 位纯数字
    private final String roomId;

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

    // 房间状态：WAITING / PLAYING / FINISHED
    @Setter
    private String roomStatus = GameConstants.ROOM_WAITING;

    // 大局胜方用户 ID，平局为空
    @Setter
    private Long winnerUserId;

    // 大局结束原因
    @Setter
    private String endReason;

    // 当前小局三连坐标
    @Setter
    private List<JinziBoardPointVO> winningLine = new ArrayList<>();

    // 全局落子步数计数器 跨小局累加，用于落子记录唯一编号
    @Setter
    private int totalMoveCount = 0;

    public synchronized int nextMoveNo() {
        return ++totalMoveCount;
    }

    // 黑方小局获胜数
    @Setter
    private int blackWins = 0;

    // 白方小局获胜数
    @Setter
    private int whiteWins = 0;

    // 平局小局数
    @Setter
    private int drawRounds = 0;

    // 当前第几小局
    @Setter
    private int currentRound = 1;

    // 当前小局的先手方棋子类型：1 黑 X ，2 白 O
    @Setter
    private int roundStartingChess = 1;

    // 当前小局是否已结束 展示结算过渡中
    @Setter
    private boolean roundFinished = false;

    // 当前小局胜者用户 ID
    @Setter
    private Long roundWinnerUserId;

    // 当前小局结束原因：LINE / DRAW / SURRENDER 等
    @Setter
    private String roundEndReason;

    // 黑方剩余局时毫秒
    @Setter
    private long blackRemainingMs = GameConstants.JINZI_GAME_TIME_MS;

    // 白方剩余局时毫秒
    @Setter
    private long whiteRemainingMs = GameConstants.JINZI_GAME_TIME_MS;

    // 当前步开始时间戳
    @Setter
    private long turnStartedAtMs = System.currentTimeMillis();

    // 断线重连截止时间：userId > 截止时间戳
    private final ConcurrentHashMap<Long, Long> disconnectDeadlines = new ConcurrentHashMap<>();

    // 每人最近一次发言时刻，用于限频
    private final ConcurrentHashMap<Long, Long> lastChatAtMs = new ConcurrentHashMap<>();

    public JinziRoom(String roomId, Long blackUserId, Long whiteUserId) {
        this.roomId = roomId;
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

    // 距离上次发言不足 interval 毫秒就拒绝，同时记下本次时刻
    public boolean tryChat(Long userId, long nowMs, long intervalMs) {
        Long last = lastChatAtMs.get(userId);
        if (last != null && nowMs - last < intervalMs) {
            return false;
        }
        lastChatAtMs.put(userId, nowMs);
        return true;
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

    public void recordRoundWin(int chess, Long winnerId, String reason, List<JinziBoardPointVO> line) {
        if (chess == 1) {
            blackWins++;
        } else if (chess == 2) {
            whiteWins++;
        }
        this.roundFinished = true;
        this.roundWinnerUserId = winnerId;
        this.roundEndReason = reason;
        this.winningLine = line == null ? new ArrayList<>() : line;
    }

    public void recordRoundDraw(String reason) {
        drawRounds++;
        this.roundFinished = true;
        this.roundWinnerUserId = null;
        this.roundEndReason = reason;
        this.winningLine = new ArrayList<>();
    }

    public boolean isMatchOver() {
        return blackWins >= TARGET_WINS
                || whiteWins >= TARGET_WINS
                || drawRounds >= MAX_DRAW_ROUNDS
                || currentRound >= MAX_TOTAL_ROUNDS;
    }

    public Long getMatchWinnerId() {
        if (blackWins >= TARGET_WINS) {
            return blackUserId;
        }
        if (whiteWins >= TARGET_WINS) {
            return whiteUserId;
        }
        if (blackWins > whiteWins) {
            return blackUserId;
        }
        if (whiteWins > blackWins) {
            return whiteUserId;
        }
        // 胜局数相同就是平局。原来这里直接返回黑方——三局全平、或者七局打成 2:2
        // 都会让黑方白捡一场胜利，排位分还照加照扣
        return null;
    }

    public void startNextRound() {
        for (int i = 0; i < GameConstants.JINZI_BOARD_SIZE; i++) {
            Arrays.fill(board[i], 0);
        }
        this.winningLine.clear();
        this.roundFinished = false;
        this.roundWinnerUserId = null;
        this.roundEndReason = null;
        this.currentRound++;
        this.roundStartingChess = (this.roundStartingChess == 1) ? 2 : 1;
        this.currentTurnUserId = (this.roundStartingChess == 1) ? blackUserId : whiteUserId;
        this.turnStartedAtMs = System.currentTimeMillis();
    }

    public int[][] copyBoard() {
        int[][] copy = new int[GameConstants.JINZI_BOARD_SIZE][GameConstants.JINZI_BOARD_SIZE];
        for (int i = 0; i < GameConstants.JINZI_BOARD_SIZE; i++) {
            copy[i] = Arrays.copyOf(board[i], GameConstants.JINZI_BOARD_SIZE);
        }
        return copy;
    }
}
