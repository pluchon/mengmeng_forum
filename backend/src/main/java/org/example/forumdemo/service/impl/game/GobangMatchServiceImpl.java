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
import org.example.forumdemo.service.interfaces.game.GameUserProfileService;
import org.example.forumdemo.service.interfaces.game.GobangMatchService;
import org.example.forumdemo.service.interfaces.game.GobangRoomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;

// 五子棋匹配服务，按积分分段匹配，避免同一用户重复入队
@Slf4j
@Service
public class GobangMatchServiceImpl implements GobangMatchService {

    private static final List<String> MATCH_BUCKETS = List.of(
            GameConstants.MATCH_BUCKET_BRONZE,
            GameConstants.MATCH_BUCKET_SILVER,
            GameConstants.MATCH_BUCKET_GOLD,
            GameConstants.MATCH_BUCKET_MASTER
    );

    @Autowired
    private GameUserProfileService gameUserProfileService;

    @Autowired
    private GobangRoomService gobangRoomService;

    @Autowired
    private GameConnectionRegistry gameConnectionRegistry;

    @Autowired
    private GameMatchQueueService gameMatchQueueService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserMapper userMapper;

    @Override
    public void startMatch(Long userId, String requestId, WebSocketSession session) {
        GameUserProfile profile = gameUserProfileService.getOrCreateProfile(userId, GameConstants.GOBANG);
        User user = userMapper.selectById(userId);
        int points = user == null || user.getPoints() == null ? 0 : user.getPoints();
        if (points < GameConstants.SCORE_DELTA) {
            sendToSession(session, GameWsResponse.fail("match_failed", requestId,
                    "论坛积分不足，至少需要 " + GameConstants.SCORE_DELTA + " 积分才能开始匹配"));
            return;
        }
        if (GameConstants.PROFILE_PLAYING.equals(profile.getCurrentStatus())) {
            sendToSession(session, GameWsResponse.fail("match_failed", requestId, "你已经在对局中"));
            return;
        }
        if (gameMatchQueueService.contains(GameConstants.GOBANG, userId)) {
            sendToSession(session, GameWsResponse.ok("match_started", requestId, null));
            return;
        }
        boolean enqueued = gameMatchQueueService.enqueue(GameConstants.GOBANG, userId, bucketOf(points));
        if (!enqueued) {
            sendToSession(session, GameWsResponse.fail("match_failed", requestId, "匹配服务暂时不可用，请稍后再试"));
            return;
        }
        gameUserProfileService.updateStatus(userId, GameConstants.GOBANG, GameConstants.PROFILE_MATCHING, null);
        sendToSession(session, GameWsResponse.ok("match_started", requestId, null));
    }

    @Override
    public void stopMatch(Long userId, String requestId, WebSocketSession session) {
        boolean removed = removeFromQueue(userId);
        if (removed) {
            gameUserProfileService.updateStatus(userId, GameConstants.GOBANG, GameConstants.PROFILE_IDLE, null);
        }
        sendToSession(session, GameWsResponse.ok("match_stopped", requestId, null));
    }

    @Override
    public boolean removeFromQueue(Long userId) {
        if (userId == null) {
            return false;
        }
        return gameMatchQueueService.dequeue(GameConstants.GOBANG, userId);
    }

    // 每秒扫描一次，优先真人匹配，等待过久时分配 AI 对手
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
        GameMatchPair pair = gameMatchQueueService.pollPair(GameConstants.GOBANG, bucketCode);
        if (pair == null) {
            return;
        }
        Long userA = pair.getUserIdA();
        Long userB = pair.getUserIdB();
        if (userA == null || userB == null || userA.equals(userB)) {
            return;
        }
        String roomId = gobangRoomService.createMatchedRoom(userA, userB);
        GameMatchSuccessVO payloadA = new GameMatchSuccessVO(roomId, userA, userB);
        GameMatchSuccessVO payloadB = new GameMatchSuccessVO(roomId, userB, userA);
        sendToGame(userA, GameWsResponse.ok("match_success", null, payloadA));
        sendToGame(userB, GameWsResponse.ok("match_success", null, payloadB));
    }

    private void matchAiFromQueue(String bucketCode) {
        Long userId = gameMatchQueueService.pollAiCandidate(
                GameConstants.GOBANG,
                bucketCode,
                GameConstants.AI_MATCH_TIMEOUT_MS
        );
        if (userId == null) {
            return;
        }
        String roomId = gobangRoomService.createAiRoom(userId);
        GameMatchSuccessVO payload = new GameMatchSuccessVO(roomId, userId, GameConstants.AI_USER_ID);
        sendToGame(userId, GameWsResponse.ok("match_success", null, payload));
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
            gameConnectionRegistry.sendToGame(
                    GameConstants.GOBANG,
                    userId,
                    objectMapper.writeValueAsString(response)
            );
        } catch (Exception e) {
            log.debug("发送五子棋匹配消息失败 userId={}, error={}", userId, e.getMessage());
        }
    }

    private void sendToSession(WebSocketSession session, GameWsResponse<?> response) {
        try {
            gameConnectionRegistry.send(session, objectMapper.writeValueAsString(response));
        } catch (Exception e) {
            log.debug("发送五子棋匹配响应失败 sessionId={}, error={}", session.getId(), e.getMessage());
        }
    }
}
