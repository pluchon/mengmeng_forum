package org.example.forumdemo.service.impl.game;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.common.websocket.game.GameConnectionRegistry;
import org.example.forumdemo.common.websocket.game.GameWsResponse;
import org.example.forumdemo.entity.db.GameUserProfile;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.vo.game.GameMatchSuccessVO;
import org.example.forumdemo.mapper.UserMapper;
import org.example.forumdemo.service.interfaces.game.GameUserProfileService;
import org.example.forumdemo.service.interfaces.game.GobangMatchService;
import org.example.forumdemo.service.interfaces.game.GobangRoomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// 五子棋匹配服务，按积分分段匹配，避免同一用户重复入队
@Slf4j
@Service
public class GobangMatchServiceImpl implements GobangMatchService {

    private final Object queueLock = new Object();

    private final ArrayDeque<Long> bronzeQueue = new ArrayDeque<>();
    private final ArrayDeque<Long> silverQueue = new ArrayDeque<>();
    private final ArrayDeque<Long> goldQueue = new ArrayDeque<>();
    private final ArrayDeque<Long> masterQueue = new ArrayDeque<>();
    private final Set<Long> queuedUsers = new HashSet<>();
    private final ConcurrentHashMap<Long, Long> queuedAt = new ConcurrentHashMap<>();

    @Autowired
    private GameUserProfileService gameUserProfileService;

    @Autowired
    private GobangRoomService gobangRoomService;

    @Autowired
    private GameConnectionRegistry gameConnectionRegistry;

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
        synchronized (queueLock) {
            if (queuedUsers.contains(userId)) {
                sendToSession(session, GameWsResponse.ok("match_started", requestId, null));
                return;
            }
            if (GameConstants.PROFILE_PLAYING.equals(profile.getCurrentStatus())) {
                sendToSession(session, GameWsResponse.fail("match_failed", requestId, "你已经在对局中"));
                return;
            }
            offer(profile);
            queuedUsers.add(userId);
            queuedAt.put(userId, System.currentTimeMillis());
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
        synchronized (queueLock) {
            if (!queuedUsers.remove(userId)) {
                return false;
            }
            bronzeQueue.remove(userId);
            silverQueue.remove(userId);
            goldQueue.remove(userId);
            masterQueue.remove(userId);
            queuedAt.remove(userId);
            return true;
        }
    }

    // 每秒扫描一次，优先真人匹配，等待过久时分配 AI 对手
    @Scheduled(fixedDelay = 1_000)
    public void matchQueuedUsers() {
        matchQueue(bronzeQueue);
        matchQueue(silverQueue);
        matchQueue(goldQueue);
        matchQueue(masterQueue);
        matchAiFromQueue(bronzeQueue);
        matchAiFromQueue(silverQueue);
        matchAiFromQueue(goldQueue);
        matchAiFromQueue(masterQueue);
    }

    private void matchQueue(ArrayDeque<Long> queue) {
        Long userA;
        Long userB;
        synchronized (queueLock) {
            if (queue.size() < 2) {
                return;
            }
            userA = queue.poll();
            userB = queue.poll();
            queuedUsers.remove(userA);
            queuedUsers.remove(userB);
            queuedAt.remove(userA);
            queuedAt.remove(userB);
        }
        if (userA == null || userB == null || userA.equals(userB)) {
            return;
        }
        String roomId = gobangRoomService.createMatchedRoom(userA, userB);
        GameMatchSuccessVO payloadA = new GameMatchSuccessVO(roomId, userA, userB);
        GameMatchSuccessVO payloadB = new GameMatchSuccessVO(roomId, userB, userA);
        sendToGame(userA, GameWsResponse.ok("match_success", null, payloadA));
        sendToGame(userB, GameWsResponse.ok("match_success", null, payloadB));
    }

    private void matchAiFromQueue(ArrayDeque<Long> queue) {
        Long userId;
        synchronized (queueLock) {
            userId = queue.peek();
            if (userId == null) {
                return;
            }
            Long startedAt = queuedAt.get(userId);
            if (startedAt == null || System.currentTimeMillis() - startedAt < GameConstants.AI_MATCH_TIMEOUT_MS) {
                return;
            }
            queue.poll();
            queuedUsers.remove(userId);
            queuedAt.remove(userId);
        }
        String roomId = gobangRoomService.createAiRoom(userId);
        GameMatchSuccessVO payload = new GameMatchSuccessVO(roomId, userId, GameConstants.AI_USER_ID);
        sendToGame(userId, GameWsResponse.ok("match_success", null, payload));
    }

    private void offer(GameUserProfile profile) {
        User user = userMapper.selectById(profile.getUserId());
        int score = user == null || user.getPoints() == null ? 0 : user.getPoints();
        if (score < 1200) {
            bronzeQueue.offer(profile.getUserId());
        } else if (score < 1600) {
            silverQueue.offer(profile.getUserId());
        } else if (score < 2000) {
            goldQueue.offer(profile.getUserId());
        } else {
            masterQueue.offer(profile.getUserId());
        }
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
