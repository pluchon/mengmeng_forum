package org.pluchon.forum.service.impl.game;

import lombok.Getter;
import lombok.Setter;
import org.pluchon.forum.service.impl.game.tetris.TetrisPlayerState;

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

    // 房间聊天记录
    private final List<TetrisChatVO> chatHistory = new ArrayList<>();

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

    public int scoreOf(Long userId) {
        TetrisPlayerState state = stateOf(userId);
        return state == null ? 0 : state.getPoints();
    }
}
