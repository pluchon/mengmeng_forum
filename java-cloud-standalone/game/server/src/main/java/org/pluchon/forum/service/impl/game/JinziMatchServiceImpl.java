package org.pluchon.forum.service.impl.game;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.websocket.game.GameConnectionRegistry;
import org.pluchon.forum.common.websocket.game.GameWsResponse;
import org.pluchon.forum.entity.bo.game.GameMatchBucket;
import org.pluchon.forum.entity.bo.game.GameMatchPair;
import org.pluchon.forum.entity.db.GameUserProfile;
import org.pluchon.forum.api.UserInternalVO;
import org.pluchon.forum.entity.vo.game.GameMatchSuccessVO;
import org.pluchon.forum.service.security.GameUserLookupService;
import org.pluchon.forum.service.interfaces.game.GameMatchQueueService;
import org.pluchon.forum.service.interfaces.game.GameRoomEventBusService;
import org.pluchon.forum.service.interfaces.game.GameUserProfileService;
import org.pluchon.forum.service.interfaces.game.JinziMatchService;
import org.pluchon.forum.service.interfaces.game.JinziRoomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.stream.Stream;

// 井字棋匹配服务，复用游戏中心匹配队列但使用独立 gameCode
@Slf4j
@ConditionalOnProperty(name = "forum.features.game-runtime", havingValue = "true")
@Service
public class JinziMatchServiceImpl implements JinziMatchService {

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
    private JinziRoomService jinziRoomService;

    @Autowired
    private GameConnectionRegistry gameConnectionRegistry;

    @Autowired
    private GameMatchQueueService gameMatchQueueService;

    @Autowired
    private GameRoomEventBusService gameRoomEventBusService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private GameUserLookupService gameUserLookupService;

    @Override
    public void startMatch(Long userId, String requestId, WebSocketSession session) {
        GameUserProfile profile = gameUserProfileService.getOrCreateProfile(userId, GameConstants.JINZI);
        UserInternalVO user = gameUserLookupService.getById(userId);
        int points = profile.getScore() == null ? 0 : profile.getScore();
        if (user == null) {
            sendToSession(session, GameWsResponse.fail("match_failed", requestId, "用户不存在，无法开始匹配"));
            return;
        }
        if (GameConstants.PROFILE_PLAYING.equals(profile.getCurrentStatus())) {
            sendToSession(session, GameWsResponse.fail("match_failed", requestId, "你已经在对局中"));
            return;
        }
        if (GameConstants.PROFILE_MATCHING.equals(profile.getCurrentStatus())
                && !gameMatchQueueService.contains(GameConstants.JINZI, userId)) {
            gameUserProfileService.updateStatus(userId, GameConstants.JINZI, GameConstants.PROFILE_IDLE, null);
            profile.setCurrentStatus(GameConstants.PROFILE_IDLE);
        }
        if (gameMatchQueueService.contains(GameConstants.JINZI, userId)) {
            sendToSession(session, GameWsResponse.ok("match_started", requestId, null));
            return;
        }
        boolean enqueued = gameMatchQueueService.enqueue(GameConstants.JINZI, userId, bucketOf(points));
        if (!enqueued) {
            String matchingGameCode = gameMatchQueueService.matchingGameCode(userId);
            if (matchingGameCode != null && !GameConstants.JINZI.equals(matchingGameCode)) {
                sendToSession(session, GameWsResponse.fail(
                        "match_failed",
                        requestId,
                        "一次只能匹配一个游戏"
                ));
                return;
            }
            if (gameMatchQueueService.contains(GameConstants.JINZI, userId)) {
                sendToSession(session, GameWsResponse.ok("match_started", requestId, null));
                return;
            }
            sendToSession(session, GameWsResponse.fail("match_failed", requestId, "匹配服务暂时不可用，请稍后再试"));
            return;
        }
        gameUserProfileService.updateStatus(userId, GameConstants.JINZI, GameConstants.PROFILE_MATCHING, null);
        sendToSession(session, GameWsResponse.ok("match_started", requestId, null));
    }

    @Override
    public void stopMatch(Long userId, String requestId, WebSocketSession session) {
        boolean removed = removeFromQueue(userId);
        if (removed) {
            gameUserProfileService.updateStatus(userId, GameConstants.JINZI, GameConstants.PROFILE_IDLE, null);
        }
        sendToSession(session, GameWsResponse.ok("match_stopped", requestId, null));
    }

    @Override
    public boolean removeFromQueue(Long userId) {
        if (userId == null) {
            return false;
        }
        return gameMatchQueueService.dequeue(GameConstants.JINZI, userId);
    }

    // 每秒扫描一次井字棋匹配队列，只匹配真实玩家
    @Scheduled(fixedDelay = 1_000)
    public void matchQueuedUsers() {
        for (String bucket : ALL_BUCKETS) {
            matchQueue(bucket);
        }
        relaxLongWaiters();
    }

    private void matchQueue(String bucketCode) {
        GameMatchPair pair = gameMatchQueueService.pollPair(GameConstants.JINZI, bucketCode);
        if (pair == null || pair.getUserIdA() == null || pair.getUserIdB() == null) {
            return;
        }
        if (pair.getUserIdA().equals(pair.getUserIdB())) {
            return;
        }
        String roomId = jinziRoomService.createMatchedRoom(pair.getUserIdA(), pair.getUserIdB());
        sendToGame(pair.getUserIdA(), GameWsResponse.ok(
                "match_success",
                null,
                new GameMatchSuccessVO(roomId, pair.getUserIdA(), pair.getUserIdB())
        ));
        sendToGame(pair.getUserIdB(), GameWsResponse.ok(
                "match_success",
                null,
                new GameMatchSuccessVO(roomId, pair.getUserIdB(), pair.getUserIdA())
        ));
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
                    GameConstants.JINZI,
                    bucket,
                    GameConstants.BUCKET_RELAX_MS
            );
            if (userId == null) {
                continue;
            }
            if (!gameMatchQueueService.enqueue(
                    GameConstants.JINZI,
                    userId,
                    new GameMatchBucket(GameConstants.MATCH_BUCKET_ANY))) {
                // 放不回队列就恢复空闲，别把人吞在「匹配中」
                gameUserProfileService.updateStatus(userId, GameConstants.JINZI, GameConstants.PROFILE_IDLE, null);
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
            boolean sent = gameConnectionRegistry.sendToGame(GameConstants.JINZI, userId, payload);
            if (!sent) {
                gameRoomEventBusService.publishGameEvent(GameConstants.JINZI, userId, payload);
            }
        } catch (Exception e) {
            log.debug("发送井字棋匹配消息失败 userId={}, error={}", userId, e.getMessage());
        }
    }

    private void sendToSession(WebSocketSession session, GameWsResponse<?> response) {
        try {
            gameConnectionRegistry.send(session, objectMapper.writeValueAsString(response));
        } catch (Exception e) {
            log.debug("发送井字棋匹配响应失败 sessionId={}, error={}", session.getId(), e.getMessage());
        }
    }
}
