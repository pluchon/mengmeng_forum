package org.pluchon.forum.service.impl.game;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.websocket.game.GameConnectionRegistry;
import org.pluchon.forum.common.websocket.game.GameWsResponse;
import org.pluchon.forum.entity.bo.game.GameMatchBucket;
import org.pluchon.forum.entity.bo.game.GameMatchPair;
import org.pluchon.forum.entity.db.GameUserProfile;
import org.pluchon.forum.entity.vo.game.GameMatchSuccessVO;
import org.pluchon.forum.service.interfaces.game.GameMatchQueueService;
import org.pluchon.forum.service.interfaces.game.GameRoomEventBusService;
import org.pluchon.forum.service.interfaces.game.GameUserProfileService;
import org.pluchon.forum.service.interfaces.game.TetrisMatchService;
import org.pluchon.forum.service.interfaces.game.TetrisRoomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.stream.Stream;

// 俄罗斯方块 PK 匹配服务，仅真人匹配
@Slf4j
@ConditionalOnProperty(name = "forum.features.game-runtime", havingValue = "true")
@Service
public class TetrisMatchServiceImpl implements TetrisMatchService {

    private static final List<String> MATCH_BUCKETS = List.of(
            GameConstants.MATCH_BUCKET_BRONZE,
            GameConstants.MATCH_BUCKET_SILVER,
            GameConstants.MATCH_BUCKET_GOLD,
            GameConstants.MATCH_BUCKET_MASTER
    );

    // 参与配对的全部桶，含兜底桶
    private static final List<String> ALL_BUCKETS = Stream
            .concat(MATCH_BUCKETS.stream(), Stream.of(GameConstants.MATCH_BUCKET_ANY))
            .toList();

    @Autowired
    private GameUserProfileService gameUserProfileService;

    @Autowired
    private TetrisRoomService tetrisRoomService;

    @Autowired
    private GameConnectionRegistry gameConnectionRegistry;

    @Autowired
    private GameMatchQueueService gameMatchQueueService;

    @Autowired
    private GameRoomEventBusService gameRoomEventBusService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void startMatch(Long userId, String requestId, WebSocketSession session) {
        GameUserProfile profile = gameUserProfileService.getOrCreateProfile(userId, GameConstants.TETRIS_PK);
        if (GameConstants.PROFILE_PLAYING.equals(profile.getCurrentStatus())) {
            sendToSession(session, GameWsResponse.fail("match_failed", requestId, "你已在进行中的对局里"));
            return;
        }
        if (gameMatchQueueService.contains(GameConstants.TETRIS_PK, userId)) {
            sendToSession(session, GameWsResponse.ok("match_started", requestId, null));
            return;
        }
        boolean enqueued = gameMatchQueueService.enqueue(
                GameConstants.TETRIS_PK,
                userId,
                bucketOf(ladderScoreOf(profile))
        );
        if (!enqueued) {
            String matchingGameCode = gameMatchQueueService.matchingGameCode(userId);
            if (matchingGameCode != null && !GameConstants.TETRIS_PK.equals(matchingGameCode)) {
                sendToSession(session, GameWsResponse.fail(
                        "match_failed",
                        requestId,
                        "一次只能匹配一个游戏"
                ));
                return;
            }
            if (gameMatchQueueService.contains(GameConstants.TETRIS_PK, userId)) {
                sendToSession(session, GameWsResponse.ok("match_started", requestId, null));
                return;
            }
            sendToSession(session, GameWsResponse.fail("match_failed", requestId, "匹配服务暂时不可用，请稍后再试"));
            return;
        }
        gameUserProfileService.updateStatus(userId, GameConstants.TETRIS_PK, GameConstants.PROFILE_MATCHING, null);
        sendToSession(session, GameWsResponse.ok("match_started", requestId, null));
    }

    @Override
    public void stopMatch(Long userId, String requestId, WebSocketSession session) {
        removeFromQueue(userId);
        gameUserProfileService.updateStatus(userId, GameConstants.TETRIS_PK, GameConstants.PROFILE_IDLE, null);
        sendToSession(session, GameWsResponse.ok("match_stopped", requestId, null));
    }

    @Override
    public boolean removeFromQueue(Long userId) {
        if (userId == null) {
            return false;
        }
        return gameMatchQueueService.dequeue(GameConstants.TETRIS_PK, userId);
    }

    @Override
    public void reconcileMatchingState(Long userId) {
        if (userId == null) {
            return;
        }
        GameUserProfile profile = gameUserProfileService.getOrCreateProfile(userId, GameConstants.TETRIS_PK);
        if (GameConstants.PROFILE_PLAYING.equals(profile.getCurrentStatus())) {
            return;
        }
        boolean queued = gameMatchQueueService.contains(GameConstants.TETRIS_PK, userId);
        if (GameConstants.PROFILE_MATCHING.equals(profile.getCurrentStatus())) {
            if (!queued) {
                gameUserProfileService.updateStatus(userId, GameConstants.TETRIS_PK, GameConstants.PROFILE_IDLE, null);
            }
            return;
        }
        if (queued) {
            removeFromQueue(userId);
        }
    }

    @Scheduled(fixedDelay = 1_000)
    public void matchQueuedUsers() {
        for (String bucket : ALL_BUCKETS) {
            matchQueue(bucket);
        }
        relaxLongWaiters();
    }

    private void matchQueue(String bucketCode) {
        GameMatchPair pair = gameMatchQueueService.pollPair(GameConstants.TETRIS_PK, bucketCode);
        if (pair == null) {
            return;
        }
        Long userA = pair.getUserIdA();
        Long userB = pair.getUserIdB();
        if (userA == null || userB == null || userA.equals(userB)) {
            return;
        }
        try {
            String roomId = tetrisRoomService.createMatchedRoom(userA, userB);
            GameMatchSuccessVO payloadA = new GameMatchSuccessVO(roomId, userA, userB);
            GameMatchSuccessVO payloadB = new GameMatchSuccessVO(roomId, userB, userA);
            sendToGame(userA, GameWsResponse.ok("match_success", null, payloadA));
            sendToGame(userB, GameWsResponse.ok("match_success", null, payloadB));
        } catch (Exception e) {
            log.error("俄罗斯方块 PK 匹配建房失败 userA={}, userB={}", userA, userB, e);
            requeuePlayer(userA);
            requeuePlayer(userB);
        }
    }

    private void requeuePlayer(Long userId) {
        GameUserProfile profile = gameUserProfileService.getOrCreateProfile(userId, GameConstants.TETRIS_PK);
        if (GameConstants.PROFILE_PLAYING.equals(profile.getCurrentStatus())) {
            return;
        }
        boolean enqueued = gameMatchQueueService.enqueue(
                GameConstants.TETRIS_PK,
                userId,
                bucketOf(ladderScoreOf(profile))
        );
        if (enqueued) {
            gameUserProfileService.updateStatus(userId, GameConstants.TETRIS_PK, GameConstants.PROFILE_MATCHING, null);
            return;
        }
        gameUserProfileService.updateStatus(userId, GameConstants.TETRIS_PK, GameConstants.PROFILE_IDLE, null);
    }

    private int ladderScoreOf(GameUserProfile profile) {
        if (profile == null || profile.getScore() == null) {
            return GameConstants.INITIAL_SCORE;
        }
        return profile.getScore();
    }

    /**
     * 在段位桶里等够久还没配上的人，挪进兜底桶。
     *
     * <p>段位桶是硬隔离的，在线人少时同桶里常常只有自己一个，等多久都配不上。
     * 挪进兜底桶后，不同段位的等待者就能互相碰上——排位分本来就按段位差调整了
     * 升降幅度，跨段对局不至于失衡。
     */
    private void relaxLongWaiters() {
        for (String bucket : MATCH_BUCKETS) {
            Long userId = gameMatchQueueService.pollAiCandidate(
                    GameConstants.TETRIS_PK,
                    bucket,
                    GameConstants.BUCKET_RELAX_MS
            );
            if (userId == null) {
                continue;
            }
            if (!gameMatchQueueService.enqueue(
                    GameConstants.TETRIS_PK,
                    userId,
                    new GameMatchBucket(GameConstants.MATCH_BUCKET_ANY))) {
                // 放不回队列就恢复空闲，别把人吞在「匹配中」
                gameUserProfileService.updateStatus(userId, GameConstants.TETRIS_PK, GameConstants.PROFILE_IDLE, null);
            }
        }
    }

    private GameMatchBucket bucketOf(int score) {
        if (score < 1200) {
            return new GameMatchBucket(GameConstants.MATCH_BUCKET_BRONZE);
        }
        if (score < 1600) {
            return new GameMatchBucket(GameConstants.MATCH_BUCKET_SILVER);
        }
        if (score < 2000) {
            return new GameMatchBucket(GameConstants.MATCH_BUCKET_GOLD);
        }
        return new GameMatchBucket(GameConstants.MATCH_BUCKET_MASTER);
    }

    private void sendToGame(Long userId, GameWsResponse<?> response) {
        try {
            String payload = objectMapper.writeValueAsString(response);
            boolean sent = gameConnectionRegistry.sendToGame(GameConstants.TETRIS_PK, userId, payload);
            if (!sent) {
                gameRoomEventBusService.publishGameEvent(GameConstants.TETRIS_PK, userId, payload);
            }
        } catch (Exception e) {
            log.warn("发送俄罗斯方块匹配消息失败 userId={}", userId, e);
        }
    }

    private void sendToSession(WebSocketSession session, GameWsResponse<?> response) {
        try {
            gameConnectionRegistry.send(session, objectMapper.writeValueAsString(response));
        } catch (Exception e) {
            log.debug("发送俄罗斯方块匹配响应失败 sessionId={}", session.getId());
        }
    }
}
