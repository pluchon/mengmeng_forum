package org.example.forumdemo.service.impl.game;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.entity.dto.game.GobangChatRequest;
import org.example.forumdemo.entity.dto.game.GobangMoveRequest;
import org.example.forumdemo.entity.dto.game.TetrisChatRequest;
import org.example.forumdemo.entity.dto.game.TetrisInputRequest;
import org.example.forumdemo.service.interfaces.game.GobangRoomService;
import org.example.forumdemo.service.interfaces.game.JinziRoomService;
import org.example.forumdemo.service.interfaces.game.TetrisRoomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;

import java.util.Map;

// 游戏房间命令路由，把非房主实例收到的动作转发给真正持有本地房间的实例执行
@Slf4j
@Service
@ConditionalOnProperty(name = "forum.features.game-runtime", havingValue = "true")
public class RedisGameRoomCommandRouter {

    private static final String TARGET_COMMAND = "ROOM_COMMAND";

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RedisMessageListenerContainer redisMessageListenerContainer;

    @Autowired
    private GobangRoomService gobangRoomService;

    @Autowired
    private JinziRoomService jinziRoomService;

    @Autowired
    private TetrisRoomService tetrisRoomService;

    @PostConstruct
    public void subscribe() {
        MessageListener listener = (message, pattern) -> {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> envelope = objectMapper.readValue(new String(message.getBody()), Map.class);
                if (!TARGET_COMMAND.equals(String.valueOf(envelope.get("targetType")))) {
                    return;
                }
                route(envelope);
            } catch (Exception e) {
                log.debug("游戏房间命令处理失败: {}", e.getMessage());
            }
        };
        redisMessageListenerContainer.addMessageListener(listener, new ChannelTopic(Constant.GAME_ROOM_EVENT_CHANNEL));
    }

    private void route(Map<String, Object> envelope) throws Exception {
        String gameCode = stringValue(envelope.get("gameCode"));
        String roomId = stringValue(envelope.get("roomId"));
        Long userId = longValue(envelope.get("userId"));
        String commandType = stringValue(envelope.get("commandType"));
        String requestId = blankToNull(stringValue(envelope.get("requestId")));
        JsonNode data = dataNode(envelope.get("data"));
        if (gameCode == null || roomId == null || userId == null || commandType == null) {
            return;
        }
        if (GameConstants.GOBANG.equals(gameCode)) {
            routeGobang(roomId, userId, commandType, requestId, data);
            return;
        }
        if (GameConstants.JINZI.equals(gameCode)) {
            routeJinzi(roomId, userId, commandType, requestId, data);
            return;
        }
        if (GameConstants.TETRIS_PK.equals(gameCode)) {
            routeTetris(roomId, userId, commandType, requestId, data);
        }
    }

    private void routeGobang(String roomId, Long userId, String commandType, String requestId, JsonNode data) throws Exception {
        if (!gobangRoomService.hasLocalRoom(roomId)) {
            return;
        }
        if ("state".equals(commandType)) {
            gobangRoomService.pushRoomState(roomId, requestId);
            return;
        }
        if ("move".equals(commandType)) {
            GobangMoveRequest request = objectMapper.treeToValue(data, GobangMoveRequest.class);
            gobangRoomService.handleMove(roomId, userId, request.getRow(), request.getCol(), requestId);
            return;
        }
        if ("chat".equals(commandType)) {
            GobangChatRequest request = objectMapper.treeToValue(data, GobangChatRequest.class);
            gobangRoomService.chat(roomId, userId, request, requestId);
            return;
        }
        if ("surrender".equals(commandType)) {
            gobangRoomService.surrender(roomId, userId, requestId);
            return;
        }
        if ("disconnect".equals(commandType)) {
            gobangRoomService.handleDisconnect(roomId, userId, null);
        }
    }

    private void routeJinzi(String roomId, Long userId, String commandType, String requestId, JsonNode data) throws Exception {
        if (!jinziRoomService.hasLocalRoom(roomId)) {
            return;
        }
        if ("state".equals(commandType)) {
            jinziRoomService.pushRoomState(roomId, requestId);
            return;
        }
        if ("move".equals(commandType)) {
            GobangMoveRequest request = objectMapper.treeToValue(data, GobangMoveRequest.class);
            jinziRoomService.handleMove(roomId, userId, request.getRow(), request.getCol(), requestId);
            return;
        }
        if ("chat".equals(commandType)) {
            GobangChatRequest request = objectMapper.treeToValue(data, GobangChatRequest.class);
            jinziRoomService.chat(roomId, userId, request, requestId);
            return;
        }
        if ("surrender".equals(commandType)) {
            jinziRoomService.surrender(roomId, userId, requestId);
            return;
        }
        if ("disconnect".equals(commandType)) {
            jinziRoomService.handleDisconnect(roomId, userId, null);
        }
    }

    private void routeTetris(String roomId, Long userId, String commandType, String requestId, JsonNode data) throws Exception {
        if (!tetrisRoomService.hasLocalRoom(roomId)) {
            return;
        }
        if ("state".equals(commandType)) {
            tetrisRoomService.pushRoomState(roomId, requestId);
            return;
        }
        if ("input".equals(commandType)) {
            TetrisInputRequest request = objectMapper.treeToValue(data, TetrisInputRequest.class);
            String action = request.getAction() == null ? "" : request.getAction().trim().toLowerCase();
            tetrisRoomService.handleInput(roomId, userId, action, requestId);
            return;
        }
        if ("chat".equals(commandType)) {
            TetrisChatRequest request = objectMapper.treeToValue(data, TetrisChatRequest.class);
            tetrisRoomService.chat(roomId, userId, request, requestId);
            return;
        }
        if ("surrender".equals(commandType)) {
            tetrisRoomService.surrender(roomId, userId, requestId);
            return;
        }
        if ("disconnect".equals(commandType)) {
            tetrisRoomService.handleDisconnect(roomId, userId, null);
        }
    }

    private JsonNode dataNode(Object data) throws Exception {
        if (data == null || String.valueOf(data).isBlank()) {
            return objectMapper.createObjectNode();
        }
        return objectMapper.readTree(String.valueOf(data));
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null ? null : Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
