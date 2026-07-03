package org.example.forumdemo.service.impl.game;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.common.websocket.game.GameConnectionRegistry;
import org.example.forumdemo.common.websocket.game.GameWsResponse;
import org.example.forumdemo.entity.bo.game.GameMatchBucket;
import org.example.forumdemo.entity.bo.game.GameMatchPair;
import org.example.forumdemo.entity.db.GameUserProfile;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.vo.game.GameMatchSuccessVO;
import org.example.forumdemo.mapper.UserMapper;
import org.example.forumdemo.service.interfaces.game.GameMatchQueueService;
import org.example.forumdemo.service.interfaces.game.GameRoomEventBusService;
import org.example.forumdemo.service.interfaces.game.GameUserProfileService;
import org.example.forumdemo.service.interfaces.game.JinziMatchService;
import org.example.forumdemo.service.interfaces.game.JinziRoomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;

// 井字棋匹配服务，复用游戏中心匹配队列但使用独立 gameCode
@Slf4j
@Service
public class JinziMatchServiceImpl implements JinziMatchService {

    private static final List<String> MATCH_BUCKETS = List.of(
            GameConstants.MATCH_BUCKET_BRONZE,
            GameConstants.MATCH_BUCKET_SILVER,
            GameConstants.MATCH_BUCKET_GOLD,
            GameConstants.MATCH_BUCKET_MASTER
    );

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
    private UserMapper userMapper;

    @Override
    public void startMatch(Long userId, String requestId, WebSocketSession session) {
        GameUserProfile profile = gameUserProfileService.getOrCreateProfile(userId, GameConstants.JINZI);
        User user = userMapper.selectById(userId);
        int points = profile.getScore() == null ? 0 : profile.getScore();
        if (user == null) {
            sendToSession(session, GameWsResponse.fail("match_failed", requestId, "用户不存在，无法开始匹配"));
            return;
        }
        if (points < GameConstants.JINZI_SCORE_DELTA) {
            sendToSession(session, GameWsResponse.fail(
                    "match_failed",
                    requestId,
                    "论坛积分不足，至少需要 " + GameConstants.JINZI_SCORE_DELTA + " 积分才能开始匹配"
            ));
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

    // 每秒扫描一次井字棋匹配队列，井字棋一局很短，超时后直接分配本地 AI
    @Scheduled(fixedDelay = 1_000)
    public void matchQueuedUsers() {
        for (String bucket : MATCH_BUCKETS) {
            matchQueue(bucket);
        }
        for (String bucket : MATCH_BUCKETS) {
            matchAiFromQueue(bucket);
        }
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

    private void matchAiFromQueue(String bucketCode) {
        Long userId = gameMatchQueueService.pollAiCandidate(
                GameConstants.JINZI,
                bucketCode,
                GameConstants.AI_MATCH_TIMEOUT_MS
        );
        if (userId == null) {
            return;
        }
        String roomId = jinziRoomService.createAiRoom(userId);
        sendToGame(userId, GameWsResponse.ok(
                "match_success",
                null,
                new GameMatchSuccessVO(roomId, userId, GameConstants.AI_USER_ID)
        ));
    }

    private GameMatchBucket bucketOf(int score) {
        if (score < 1200) {
            return new GameMatchBucket(GameConstants.MATCH_BUCKET_BRONZE, 0);
        }
        if (score < 1600) {
            return new GameMatchBucket(GameConstants.MATCH_BUCKET_SILVER, 1200);
        }
        if (score < 2000) {
            return new GameMatchBucket(GameConstants.MATCH_BUCKET_GOLD, 1600);
        }
        return new GameMatchBucket(GameConstants.MATCH_BUCKET_MASTER, 2000);
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
