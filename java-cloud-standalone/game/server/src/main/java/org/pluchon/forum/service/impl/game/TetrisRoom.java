package org.pluchon.forum.service.impl.game;

import lombok.Getter;
import lombok.Setter;
import org.pluchon.forum.service.impl.game.tetris.TetrisEngineConstants;
import org.pluchon.forum.service.impl.game.tetris.TetrisPlayerState;
import org.pluchon.forum.entity.db.GameUserProfile;
import org.pluchon.forum.api.UserInternalVO;

import org.pluchon.forum.entity.vo.game.TetrisChatVO;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// 俄罗斯方块 PK 内存房间
@Getter
public class TetrisRoom {

    // 房间 ID
    private final String roomId;

    // 玩家1用户 ID
    private final Long player1UserId;

    // 玩家2用户 ID
    private final Long player2UserId;

    // 红方用户 ID
    private final Long redUserId;

    // 蓝方用户 ID
    private final Long blueUserId;

    // 玩家1权威棋盘
    private final TetrisPlayerState player1State;

    // 玩家2权威棋盘
    private final TetrisPlayerState player2State;

    // 房间开始时间
    private final Date startedAt = new Date();

    // 竞速局的终点时刻，到点由房间按消行数与分数裁定
    private final long deadlineMs = startedAt.getTime() + TetrisEngineConstants.RACE_DURATION_MS;

    // 房内用户资料快照。
    //
    // 棋盘每推进一格就要广播一次状态，而昵称头像和战绩在一局里几乎不变——
    // 以前每帧都去查一次 auth 和 profile 表，手速快时一个房间每秒能打出几十次
    // 跨域调用。这里缓存住，只在有人进出房间时失效。
    private volatile Map<Long, UserInternalVO> userSnapshot;

    private volatile Map<Long, GameUserProfile> profileSnapshot;

    // 房间状态
    @Setter
    private String roomStatus = GameConstants.ROOM_PLAYING;

    // 胜方用户 ID
    @Setter
    private Long winnerUserId;

    // 结束原因
    @Setter
    private String endReason;

    // 断线重连截止时间
    private final Map<Long, Long> disconnectDeadlines = new ConcurrentHashMap<>();

    // 观战加入时间
    private final Map<Long, Long> spectatorJoinedAt = new ConcurrentHashMap<>();

    // 房间聊天记录，只留最近若干条：以前无上限，刷屏会一直堆在内存里
    private static final int MAX_CHAT_HISTORY = 50;

    private final List<TetrisChatVO> chatHistory = new ArrayList<>();

    // 每人最近一次发言时刻，用于限频
    private final Map<Long, Long> lastChatAtMs = new ConcurrentHashMap<>();

    public TetrisRoom(String roomId, Long player1UserId, Long player2UserId, Long redUserId, Long blueUserId) {
        this.roomId = roomId;
        this.player1UserId = player1UserId;
        this.player2UserId = player2UserId;
        this.redUserId = redUserId;
        this.blueUserId = blueUserId;
        long roomSeed = System.nanoTime() ^ player1UserId ^ Long.rotateLeft(player2UserId, 16);
        long player1Seed = roomSeed ^ Long.rotateLeft(player1UserId, 17);
        long player2Seed = roomSeed ^ Long.rotateLeft(player2UserId, 41) ^ 0x9E3779B97F4A7C15L;
        this.player1State = new TetrisPlayerState(player1Seed);
        this.player2State = new TetrisPlayerState(player2Seed);
    }

    public boolean contains(Long userId) {
        return player1UserId.equals(userId) || player2UserId.equals(userId);
    }

    public Long opponentOf(Long userId) {
        if (player1UserId.equals(userId)) {
            return player2UserId;
        }
        if (player2UserId.equals(userId)) {
            return player1UserId;
        }
        return null;
    }

    public TetrisPlayerState stateOf(Long userId) {
        if (player1UserId.equals(userId)) {
            return player1State;
        }
        if (player2UserId.equals(userId)) {
            return player2State;
        }
        return null;
    }

    public void appendChat(TetrisChatVO chat) {
        chatHistory.add(chat);
        while (chatHistory.size() > MAX_CHAT_HISTORY) {
            chatHistory.remove(0);
        }
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

    public int scoreOf(Long userId) {
        TetrisPlayerState state = stateOf(userId);
        return state == null ? 0 : state.getPoints();
    }

    public int linesOf(Long userId) {
        TetrisPlayerState state = stateOf(userId);
        return state == null ? 0 : state.getClearLines();
    }

    public Map<Long, UserInternalVO> getUserSnapshot() {
        return userSnapshot;
    }

    public Map<Long, GameUserProfile> getProfileSnapshot() {
        return profileSnapshot;
    }

    public void cacheSnapshots(Map<Long, UserInternalVO> users, Map<Long, GameUserProfile> profiles) {
        this.userSnapshot = users;
        this.profileSnapshot = profiles;
    }

    // 有人进出房间时调用：观战席变了才需要重新去查资料
    public void invalidateSnapshots() {
        this.userSnapshot = null;
        this.profileSnapshot = null;
    }
}
