package org.pluchon.forum.common.websocket.game;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.concurrent.ConcurrentHashMap;

// 游戏连接注册表，与站内通知 WebSocket 完全隔离，允许同一用户同时拥有通知/大厅/游戏/房间连接
@Slf4j
@Component
public class GameConnectionRegistry {

    private static final long HEARTBEAT_TIMEOUT_MS = 90_000;

    // 游戏中心大厅连接：userId > session
    private final ConcurrentHashMap<Long, WebSocketSession> lobbySessions = new ConcurrentHashMap<>();

    // 游戏级连接：gameCode > userId > session
    private final ConcurrentHashMap<String, ConcurrentHashMap<Long, WebSocketSession>> gameSessions =
            new ConcurrentHashMap<>();

    // 房间连接：roomId > userId > session
    private final ConcurrentHashMap<String, ConcurrentHashMap<Long, WebSocketSession>> roomSessions =
            new ConcurrentHashMap<>();

    // sessionId > 最后心跳时间
    private final ConcurrentHashMap<String, Long> lastPingTime = new ConcurrentHashMap<>();

    public void enterLobby(Long userId, WebSocketSession session) {
        WebSocketSession old = lobbySessions.put(userId, session);
        closePrevious(old, session);
        touch(session);
    }

    public void exitLobby(Long userId, WebSocketSession session) {
        removeIfSame(lobbySessions, userId, session);
        cleanupSession(session);
    }

    public void enterGame(String gameCode, Long userId, WebSocketSession session) {
        ConcurrentHashMap<Long, WebSocketSession> sessions = gameSessions.computeIfAbsent(
                gameCode,
                key -> new ConcurrentHashMap<>()
        );
        WebSocketSession old = sessions.put(userId, session);
        closePrevious(old, session);
        touch(session);
    }

    public void exitGame(String gameCode, Long userId, WebSocketSession session) {
        ConcurrentHashMap<Long, WebSocketSession> sessions = gameSessions.get(gameCode);
        if (sessions != null) {
            removeIfSame(sessions, userId, session);
        }
        cleanupSession(session);
    }

    public void enterRoom(String roomId, Long userId, WebSocketSession session) {
        ConcurrentHashMap<Long, WebSocketSession> sessions = roomSessions.computeIfAbsent(
                roomId,
                key -> new ConcurrentHashMap<>()
        );
        WebSocketSession old = sessions.put(userId, session);
        closePrevious(old, session);
        touch(session);
    }

    public void exitRoom(String roomId, Long userId, WebSocketSession session) {
        ConcurrentHashMap<Long, WebSocketSession> sessions = roomSessions.get(roomId);
        if (sessions != null) {
            removeIfSame(sessions, userId, session);
            if (sessions.isEmpty()) {
                roomSessions.remove(roomId, sessions);
            }
        }
        cleanupSession(session);
    }

    public boolean sendToGame(String gameCode, Long userId, String payload) {
        Map<Long, WebSocketSession> sessions = gameSessions.get(gameCode);
        return sessions != null && send(sessions.get(userId), payload);
    }

    public boolean sendToRoom(String roomId, Long userId, String payload) {
        Map<Long, WebSocketSession> sessions = roomSessions.get(roomId);
        return sessions != null && send(sessions.get(userId), payload);
    }

    public void broadcastRoom(String roomId, String payload) {
        Map<Long, WebSocketSession> sessions = roomSessions.get(roomId);
        if (sessions == null) {
            return;
        }
        sessions.values().forEach(session -> send(session, payload));
    }

    public void forEachRoomSession(String roomId, BiConsumer<Long, WebSocketSession> consumer) {
        Map<Long, WebSocketSession> sessions = roomSessions.get(roomId);
        if (sessions == null || consumer == null) {
            return;
        }
        sessions.forEach(consumer);
    }

    public Set<Long> roomUserIds(String roomId) {
        Map<Long, WebSocketSession> sessions = roomSessions.get(roomId);
        if (sessions == null) {
            return Set.of();
        }
        Set<Long> ids = new HashSet<>();
        sessions.forEach((userId, session) -> {
            if (session != null && session.isOpen()) {
                ids.add(userId);
            }
        });
        return ids;
    }

    public int countRoomOnline(String roomId) {
        Map<Long, WebSocketSession> sessions = roomSessions.get(roomId);
        return sessions == null ? 0 : (int) sessions.values().stream().filter(WebSocketSession::isOpen).count();
    }

    public int countLobbyOnline() {
        return (int) lobbySessions.values().stream().filter(WebSocketSession::isOpen).count();
    }

    public int countGameOnline(String gameCode) {
        Map<Long, WebSocketSession> sessions = gameSessions.get(gameCode);
        return sessions == null ? 0 : (int) sessions.values().stream().filter(WebSocketSession::isOpen).count();
    }

    public void touch(WebSocketSession session) {
        if (session != null) {
            lastPingTime.put(session.getId(), System.currentTimeMillis());
        }
    }

    public boolean send(WebSocketSession session, String payload) {
        if (session == null || !session.isOpen()) {
            return false;
        }
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(payload));
            }
            return true;
        } catch (Exception e) {
            log.debug("游戏 WS 推送失败 sessionId={}, error={}", session.getId(), e.getMessage());
            return false;
        }
    }

    // 游戏连接也做心跳清理，避免浏览器异常退出后占住匹配或房间连接
    @Scheduled(fixedDelay = 30_000)
    public void evictStaleSessions() {
        long now = System.currentTimeMillis();
        lastPingTime.forEach((sessionId, lastTime) -> {
            if (now - lastTime <= HEARTBEAT_TIMEOUT_MS) {
                return;
            }
            closeBySessionId(sessionId);
        });
    }

    private void closeBySessionId(String sessionId) {
        findAndClose(lobbySessions, sessionId);
        gameSessions.values().forEach(sessions -> findAndClose(sessions, sessionId));
        roomSessions.values().forEach(sessions -> findAndClose(sessions, sessionId));
        lastPingTime.remove(sessionId);
    }

    private void findAndClose(Map<Long, WebSocketSession> sessions, String sessionId) {
        sessions.forEach((userId, session) -> {
            if (!session.getId().equals(sessionId)) {
                return;
            }
            try {
                if (session.isOpen()) {
                    session.close(CloseStatus.SESSION_NOT_RELIABLE);
                }
            } catch (Exception e) {
                log.debug("关闭游戏 WS 死连接失败 sessionId={}, error={}", sessionId, e.getMessage());
            }
            removeIfSame(sessions, userId, session);
        });
    }

    private void removeIfSame(Map<Long, WebSocketSession> sessions, Long userId, WebSocketSession session) {
        if (userId != null && session != null && sessions.get(userId) == session) {
            sessions.remove(userId);
        }
    }

    private void closePrevious(WebSocketSession old, WebSocketSession current) {
        if (old == null || old == current || !old.isOpen()) {
            return;
        }
        try {
            old.close(CloseStatus.NORMAL);
        } catch (Exception e) {
            log.debug("关闭旧游戏 WS 连接失败 sessionId={}, error={}", old.getId(), e.getMessage());
        }
    }

    private void cleanupSession(WebSocketSession session) {
        if (session != null) {
            lastPingTime.remove(session.getId());
        }
    }
}
