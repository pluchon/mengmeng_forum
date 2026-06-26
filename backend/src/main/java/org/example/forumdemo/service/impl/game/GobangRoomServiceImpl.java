package org.example.forumdemo.service.impl.game;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.common.config.AiHubUrls;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.mq.ForumProducer;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.common.websocket.game.GameConnectionRegistry;
import org.example.forumdemo.common.websocket.game.GameWsResponse;
import org.example.forumdemo.entity.db.GameGobangMatchRecord;
import org.example.forumdemo.entity.db.GameGobangRoomMove;
import org.example.forumdemo.entity.db.GameRoomPlayer;
import org.example.forumdemo.entity.db.GameSettlementEvent;
import org.example.forumdemo.entity.db.GameUserProfile;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.dto.game.GobangChatRequest;
import org.example.forumdemo.entity.vo.game.GobangActiveRoomVO;
import org.example.forumdemo.entity.vo.game.GobangChatVO;
import org.example.forumdemo.entity.vo.game.GobangMoveVO;
import org.example.forumdemo.entity.vo.game.GobangRoomParticipantVO;
import org.example.forumdemo.entity.vo.game.GobangRoomStateVO;
import org.example.forumdemo.entity.vo.game.GameRoomSnapshotVO;
import org.example.forumdemo.entity.vo.mq.GameFinishedMqVO;
import org.example.forumdemo.mapper.GameGobangMatchRecordMapper;
import org.example.forumdemo.mapper.GameGobangRoomMoveMapper;
import org.example.forumdemo.mapper.GameRoomPlayerMapper;
import org.example.forumdemo.mapper.GameSettlementEventMapper;
import org.example.forumdemo.mapper.GameUserProfileMapper;
import org.example.forumdemo.mapper.UserMapper;
import org.example.forumdemo.service.interfaces.game.GameUserProfileService;
import org.example.forumdemo.service.interfaces.game.GameRoomSnapshotService;
import org.example.forumdemo.service.interfaces.game.GameRoomEventBusService;
import org.example.forumdemo.service.interfaces.game.GobangRoomService;
import org.example.forumdemo.service.interfaces.points.PointsService;
import org.example.forumdemo.service.impl.game.ai.GameAiPlanner;
import org.example.forumdemo.service.impl.game.ai.GobangAiEngine;
import org.example.forumdemo.service.impl.game.guard.GobangActionContext;
import org.example.forumdemo.service.impl.game.guard.GobangGuardChain;
import org.example.forumdemo.service.impl.game.guard.GobangGuardResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

// 五子棋房间服务，服务端持有权威棋盘并负责唯一结算
@Slf4j
@Service
public class GobangRoomServiceImpl implements GobangRoomService {

    private static final long RECONNECT_WINDOW_MS = GameConstants.GOBANG_RECONNECT_WINDOW_MS;

    private static final int AI_PRO_SCORE_THRESHOLD = 1600;

    private static final String AI_MODEL_FLASH = "deepseek-v4-flash";

    private static final String AI_MODEL_PRO = "deepseek-v4-pro";

    private static final String DEFAULT_AI_MODEL_NAME = AI_MODEL_FLASH;

    // roomId -> 房间状态
    private final ConcurrentHashMap<String, GobangRoom> rooms = new ConcurrentHashMap<>();

    // userId -> roomId，用于防止同一用户进入多个房间
    private final ConcurrentHashMap<Long, String> userRoomIds = new ConcurrentHashMap<>();

    @Autowired
    private GameConnectionRegistry gameConnectionRegistry;

    @Autowired
    private GameUserProfileService gameUserProfileService;

    @Autowired
    private GameRoomSnapshotService gameRoomSnapshotService;

    @Autowired
    private GameRoomEventBusService gameRoomEventBusService;

    @Autowired
    private GameUserProfileMapper gameUserProfileMapper;

    @Autowired
    private GameGobangMatchRecordMapper gameGobangMatchRecordMapper;

    @Autowired
    private GameRoomPlayerMapper gameRoomPlayerMapper;

    @Autowired
    private GameGobangRoomMoveMapper gameGobangRoomMoveMapper;

    @Autowired
    private GameSettlementEventMapper gameSettlementEventMapper;

    @Autowired
    private PointsService pointsService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private GobangRuleEngine gobangRuleEngine;

    @Autowired
    private GobangAiEngine gobangAiEngine;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private ForumProducer forumProducer;

    private GobangGuardChain gobangGuardChain = GobangGuardChain.defaultChain();

    @Value("${forum.ai.internal-key:}")
    private String aiInternalKey;

    @Autowired
    @Qualifier("gameAiRestTemplate")
    private RestTemplate gameAiRestTemplate;

    @Autowired(required = false)
    public void setGobangGuardChain(GobangGuardChain gobangGuardChain) {
        if (gobangGuardChain != null) {
            this.gobangGuardChain = gobangGuardChain;
        }
    }

    @Override
    public String createMatchedRoom(Long userIdA, Long userIdB) {
        if (userIdA == null || userIdB == null || userIdA.equals(userIdB)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        GobangRoom room = new GobangRoom(userIdA, userIdB);
        room.setRoomStatus(GameConstants.ROOM_PLAYING);
        rooms.put(room.getRoomId(), room);
        userRoomIds.put(userIdA, room.getRoomId());
        userRoomIds.put(userIdB, room.getRoomId());
        gameUserProfileService.updateStatus(userIdA, GameConstants.GOBANG, GameConstants.PROFILE_PLAYING, room.getRoomId());
        gameUserProfileService.updateStatus(userIdB, GameConstants.GOBANG, GameConstants.PROFILE_PLAYING, room.getRoomId());
        saveRoomPlayer(room.getRoomId(), userIdA, "BLACK");
        saveRoomPlayer(room.getRoomId(), userIdB, "WHITE");
        saveRoomSnapshot(room);
        return room.getRoomId();
    }

    @Override
    public String createAiRoom(Long userId) {
        if (userId == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        GobangRoom room = new GobangRoom(userId, GameConstants.AI_USER_ID);
        room.setAiRoom(true);
        String modelCode = chooseAiModelCode(userId);
        room.setAiModelCode(modelCode);
        room.setAiModelName(GameAiPlanner.formatModelLabel(modelCode, false, false));
        room.setRoomStatus(GameConstants.ROOM_PLAYING);
        rooms.put(room.getRoomId(), room);
        userRoomIds.put(userId, room.getRoomId());
        gameUserProfileService.updateStatus(userId, GameConstants.GOBANG, GameConstants.PROFILE_PLAYING, room.getRoomId());
        saveRoomPlayer(room.getRoomId(), userId, "BLACK");
        saveRoomPlayer(room.getRoomId(), GameConstants.AI_USER_ID, "AI");
        saveRoomSnapshot(room);
        return room.getRoomId();
    }

    @Override
    public GobangRoomStateVO joinRoom(String roomId, Long userId, WebSocketSession session) {
        GobangRoom room = requireExistingRoom(roomId);
        boolean spectator = !room.contains(userId);
        gameConnectionRegistry.enterRoom(roomId, userId, session);
        if (!spectator) {
            room.getDisconnectDeadlines().remove(userId);
            broadcast(roomId, GameWsResponse.ok("peer_reconnected", null, getRoomState(roomId, userId)));
        } else {
            room.getSpectatorJoinedAt().putIfAbsent(userId, System.currentTimeMillis());
            saveRoomPlayer(roomId, userId, "SPECTATOR");
            saveRoomSnapshot(room);
            sendStateToRoom(room, "room_state_updated");
        }
        return getRoomState(roomId, userId);
    }

    @Override
    public GobangRoomStateVO getRoomState(String roomId, Long userId) {
        GobangRoom room = requireExistingRoom(roomId);
        return toStateVO(room, userId);
    }

    @Override
    public void handleMove(String roomId, Long userId, Integer row, Integer col, String requestId) {
        requireRoomActionParams(roomId, userId);
        GobangRoom room = rooms.get(roomId);
        if (room == null) {
            rejectIfGuardFailed(GobangActionContext.move(roomId, userId, requestId, null, row, col));
            return;
        }
        synchronized (room) {
            if (rejectIfGuardFailed(GobangActionContext.move(roomId, userId, requestId, room, row, col))) {
                return;
            }
            int chess = room.chessOf(userId);
            long now = System.currentTimeMillis();
            long spentMs = Math.max(0, now - room.getTurnStartedAtMs());
            saveMove(room, userId, row, col, chess, spentMs);
            room.consumeTurnTime(now);
            room.getBoard()[row][col] = chess;
            boolean win = gobangRuleEngine.hasFive(room.getBoard(), chess, row, col);
            Long winnerId = win ? userId : null;
            Long nextTurnUserId = win ? null : room.opponentOf(userId);
            if (win) {
                room.setWinningLine(gobangRuleEngine.winningLine(room.getBoard(), chess, row, col));
            }
            room.setCurrentTurnUserId(nextTurnUserId);
            room.setTurnStartedAtMs(System.currentTimeMillis());
            saveRoomSnapshot(room);
            GobangMoveVO moveVO = new GobangMoveVO(
                    userId,
                    row,
                    col,
                    chess,
                    nextTurnUserId,
                    winnerId,
                    win ? GameConstants.END_FIVE : null,
                    win ? room.getWinningLine() : null
            );
            broadcast(roomId, GameWsResponse.ok("move_accepted", requestId, moveVO));
            if (win) {
                finishRoom(room, winnerId, GameConstants.END_FIVE);
            } else if (room.isAiRoom() && GameConstants.AI_USER_ID.equals(room.getCurrentTurnUserId())) {
                scheduleAiMove(room);
            }
        }
    }

    @Override
    public void surrender(String roomId, Long userId, String requestId) {
        requireRoomActionParams(roomId, userId);
        GobangRoom room = rooms.get(roomId);
        if (room == null) {
            rejectIfGuardFailed(GobangActionContext.surrender(roomId, userId, requestId, null));
            return;
        }
        synchronized (room) {
            if (rejectIfGuardFailed(GobangActionContext.surrender(roomId, userId, requestId, room))) {
                return;
            }
            finishRoom(room, room.opponentOf(userId), GameConstants.END_SURRENDER);
        }
    }

    @Override
    public void chat(String roomId, Long userId, GobangChatRequest request, String requestId) {
        requireRoomActionParams(roomId, userId);
        GobangRoom room = rooms.get(roomId);
        GobangActionContext context = GobangActionContext.chat(roomId, userId, requestId, room, request);
        if (rejectIfGuardFailed(context)) {
            return;
        }
        String type = context.chatMessageType();
        String content = context.chatContent();
        GobangChatVO vo = new GobangChatVO(
                userId,
                type,
                content,
                request == null ? null : request.getEmojiId(),
                request == null ? null : request.getEmojiUrl(),
                System.currentTimeMillis()
        );
        broadcast(roomId, GameWsResponse.ok("room_chat", requestId, vo));
    }

    private void requireRoomActionParams(String roomId, Long userId) {
        if (roomId == null || roomId.isBlank() || userId == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
    }

    private boolean rejectIfGuardFailed(GobangActionContext context) {
        GobangGuardResult result = gobangGuardChain.check(context);
        if (result.isPassed()) {
            return false;
        }
        sendRoomError(context.getRoomId(), context.getUserId(), context.getRequestId(), result.getMessage());
        return true;
    }

    @Override
    public void handleDisconnect(String roomId, Long userId, WebSocketSession session) {
        GobangRoom room = rooms.get(roomId);
        if (room == null || userId == null) {
            gameConnectionRegistry.exitRoom(roomId, userId, session);
            return;
        }
        if (!room.contains(userId)) {
            gameConnectionRegistry.exitRoom(roomId, userId, session);
            sendStateToRoom(room, "room_state_updated");
            return;
        }
        gameConnectionRegistry.exitRoom(roomId, userId, session);
        synchronized (room) {
            if (!GameConstants.ROOM_PLAYING.equals(room.getRoomStatus())) {
                return;
            }
            room.getDisconnectDeadlines().put(userId, System.currentTimeMillis() + RECONNECT_WINDOW_MS);
            broadcast(roomId, GameWsResponse.ok("peer_disconnected", null, toStateVO(room, room.opponentOf(userId))));
        }
    }

    @Override
    public List<GobangActiveRoomVO> listActiveRooms() {
        List<GobangActiveRoomVO> rows = new ArrayList<>();
        for (GobangRoom room : rooms.values()) {
            if (!GameConstants.ROOM_PLAYING.equals(room.getRoomStatus())) {
                continue;
            }
            rows.add(new GobangActiveRoomVO(
                    room.getRoomId(),
                    room.getBlackUserId(),
                    room.getWhiteUserId(),
                    room.getCurrentTurnUserId(),
                    room.isAiRoom(),
                    room.getStartedAt()
            ));
        }
        rows.sort(Comparator.comparing(GobangActiveRoomVO::getStartedAt).reversed());
        return rows;
    }

    // 定时处理超过重连窗口的玩家，结算前再次确认房间仍在进行中
    @Scheduled(fixedDelay = 5_000)
    public void settleExpiredDisconnects() {
        long now = System.currentTimeMillis();
        for (GobangRoom room : rooms.values()) {
            synchronized (room) {
                if (!GameConstants.ROOM_PLAYING.equals(room.getRoomStatus())) {
                    continue;
                }
                for (Map.Entry<Long, Long> entry : room.getDisconnectDeadlines().entrySet()) {
                    if (entry.getValue() <= now) {
                        finishRoom(room, room.opponentOf(entry.getKey()), GameConstants.END_DISCONNECT);
                        break;
                    }
                }
            }
        }
    }

    @Scheduled(fixedDelay = 1_000)
    public void settleTimeoutRooms() {
        long now = System.currentTimeMillis();
        for (GobangRoom room : rooms.values()) {
            synchronized (room) {
                if (!GameConstants.ROOM_PLAYING.equals(room.getRoomStatus())) {
                    continue;
                }
                long moveLeft = room.currentTurnRemainingMs(now);
                long gameLeft = room.remainingGameMs(room.getCurrentTurnUserId(), now);
                if (moveLeft <= 0 || gameLeft <= 0) {
                    finishRoom(room, room.opponentOf(room.getCurrentTurnUserId()), GameConstants.END_TIMEOUT);
                }
            }
        }
    }

    private void finishRoom(GobangRoom room, Long winnerId, String endReason) {
        if (!GameConstants.ROOM_PLAYING.equals(room.getRoomStatus())) {
            return;
        }
        room.setRoomStatus(GameConstants.ROOM_FINISHED);
        room.setWinnerUserId(winnerId);
        room.setEndReason(endReason);
        Long loserId = winnerId == null ? null : room.opponentOf(winnerId);
        GameFinishedMqVO finishedEvent = transactionTemplate.execute(status -> {
            GameGobangMatchRecord record = new GameGobangMatchRecord();
            record.setRoomId(room.getRoomId());
            record.setBlackUserId(room.getBlackUserId());
            record.setWhiteUserId(room.getWhiteUserId());
            record.setWinnerUserId(winnerId);
            record.setLoserUserId(loserId);
            record.setEndReason(endReason);
            record.setScoreDelta(GameConstants.SCORE_DELTA);
            record.setStartedAt(room.getStartedAt());
            record.setEndedAt(new Date());
            record.setDeleteState((byte) 0);
            gameGobangMatchRecordMapper.insert(record);
            if (winnerId != null && loserId != null) {
                gameUserProfileMapper.applyWin(winnerId, GameConstants.GOBANG, GameConstants.SCORE_DELTA);
                gameUserProfileMapper.applyLose(loserId, GameConstants.GOBANG, GameConstants.SCORE_DELTA);
                if (!GameConstants.AI_USER_ID.equals(winnerId)) {
                    pointsService.addPoints(winnerId, GameConstants.SCORE_DELTA,
                            Constant.POINTS_SOURCE_GAME_WIN, record.getId(), "五子棋胜利奖励");
                }
                if (!GameConstants.AI_USER_ID.equals(loserId)) {
                    User loser = userMapper.selectByIdForUpdate(loserId);
                    int loserPoints = loser == null || loser.getPoints() == null ? 0 : loser.getPoints();
                    if (loserPoints >= GameConstants.SCORE_DELTA) {
                        pointsService.deductPoints(loserId, GameConstants.SCORE_DELTA,
                                Constant.POINTS_SOURCE_GAME_LOSE, record.getId(), "五子棋对局扣除");
                    }
                }
            }
            gameUserProfileMapper.updatePlayStatus(room.getBlackUserId(), GameConstants.GOBANG, GameConstants.PROFILE_IDLE, null);
            if (!GameConstants.AI_USER_ID.equals(room.getWhiteUserId())) {
                gameUserProfileMapper.updatePlayStatus(room.getWhiteUserId(), GameConstants.GOBANG, GameConstants.PROFILE_IDLE, null);
            }
            return createGameFinishedEvent(room, record, winnerId, loserId, endReason);
        });
        saveRoomSnapshot(room);
        publishGameFinishedEvent(finishedEvent);
        sendFinalStateToRoom(room);
        cleanupRoom(room);
    }

    private void saveRoomSnapshot(GobangRoom room) {
        if (room == null) {
            return;
        }
        long now = System.currentTimeMillis();
        GameRoomSnapshotVO snapshot = new GameRoomSnapshotVO(
                GameConstants.GOBANG,
                room.getRoomId(),
                room.getRoomStatus(),
                room.getBlackUserId(),
                room.getWhiteUserId(),
                room.getCurrentTurnUserId(),
                room.copyBoard(),
                countMoves(room),
                room.getWinnerUserId(),
                room.getEndReason(),
                room.getWinningLine(),
                room.remainingGameMs(room.getBlackUserId(), now),
                room.remainingGameMs(room.getWhiteUserId(), now),
                room.currentTurnRemainingMs(now),
                now
        );
        gameRoomSnapshotService.saveSnapshot(snapshot);
    }

    private GameFinishedMqVO createGameFinishedEvent(
            GobangRoom room,
            GameGobangMatchRecord record,
            Long winnerId,
            Long loserId,
            String endReason
    ) {
        String eventId = UUID.randomUUID().toString();
        GameSettlementEvent event = new GameSettlementEvent();
        event.setEventId(eventId);
        event.setGameCode(GameConstants.GOBANG);
        event.setRoomId(room.getRoomId());
        event.setEventType(GameConstants.SETTLEMENT_EVENT_GAME_FINISHED);
        event.setRecordId(record.getId());
        event.setStatus(GameConstants.SETTLEMENT_EVENT_CREATED);
        event.setRetryCount(0);
        event.setDeleteState((byte) 0);
        gameSettlementEventMapper.insert(event);
        return new GameFinishedMqVO(
                eventId,
                GameConstants.GOBANG,
                room.getRoomId(),
                record.getId(),
                winnerId,
                loserId,
                endReason,
                room.getWinningLine(),
                System.currentTimeMillis()
        );
    }

    private void publishGameFinishedEvent(GameFinishedMqVO event) {
        if (event == null) {
            return;
        }
        try {
            forumProducer.sendGameFinished(event);
            updateSettlementEventStatusFromCreated(
                    event.getEventId(),
                    GameConstants.SETTLEMENT_EVENT_MQ_SENT,
                    null
            );
        } catch (Exception e) {
            log.error("投递游戏结束 MQ 失败 roomId={}, eventId={}, error={}",
                    event.getRoomId(),
                    event.getEventId(),
                    e.getMessage());
            updateSettlementEventStatusFromCreated(
                    event.getEventId(),
                    GameConstants.SETTLEMENT_EVENT_MQ_PENDING,
                    truncateError(e.getMessage())
            );
        }
    }

    private String chooseAiModelCode(Long userId) {
        GameUserProfile profile = gameUserProfileService.getOrCreateProfile(userId, GameConstants.GOBANG);
        int score = profile == null || profile.getScore() == null ? 0 : profile.getScore();
        return score < AI_PRO_SCORE_THRESHOLD ? AI_MODEL_FLASH : AI_MODEL_PRO;
    }

    private void updateSettlementEventStatusFromCreated(String eventId, String status, String lastError) {
        LambdaUpdateWrapper<GameSettlementEvent> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(GameSettlementEvent::getEventId, eventId)
                .eq(GameSettlementEvent::getStatus, GameConstants.SETTLEMENT_EVENT_CREATED)
                .set(GameSettlementEvent::getStatus, status)
                .set(GameSettlementEvent::getLastError, lastError);
        gameSettlementEventMapper.update(null, wrapper);
    }

    private String truncateError(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 512 ? message : message.substring(0, 512);
    }

    private void cleanupRoom(GobangRoom room) {
        rooms.remove(room.getRoomId());
        userRoomIds.remove(room.getBlackUserId());
        if (!GameConstants.AI_USER_ID.equals(room.getWhiteUserId())) {
            userRoomIds.remove(room.getWhiteUserId());
        }
    }

    private GobangRoom requireRoom(String roomId, Long userId) {
        if (roomId == null || roomId.isBlank() || userId == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        GobangRoom room = rooms.get(roomId);
        if (room == null || !room.contains(userId)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_FORBIDDEN));
        }
        return room;
    }

    private GobangRoom requireExistingRoom(String roomId) {
        if (roomId == null || roomId.isBlank()) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        GobangRoom room = rooms.get(roomId);
        if (room == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_FORBIDDEN));
        }
        return room;
    }

    private GobangRoomStateVO toStateVO(GobangRoom room, Long userId) {
        long now = System.currentTimeMillis();
        boolean spectator = userId == null || !room.contains(userId);
        Map<Long, User> userMap = loadRoomUsers(room);
        Map<Long, GameUserProfile> profileMap = loadRoomProfiles(room);
        GobangRoomParticipantVO blackPlayer = toParticipant(
                room.getBlackUserId(),
                "BLACK",
                room.getStartedAt().getTime(),
                null,
                userMap,
                profileMap
        );
        GobangRoomParticipantVO whitePlayer = toParticipant(
                room.getWhiteUserId(),
                room.isAiRoom() ? "AI" : "WHITE",
                room.getStartedAt().getTime(),
                room.getAiModelName(),
                userMap,
                profileMap
        );
        GobangRoomParticipantVO opponentPlayer = null;
        Long opponentId = spectator ? null : room.opponentOf(userId);
        if (opponentId != null) {
            opponentPlayer = opponentId.equals(room.getBlackUserId()) ? blackPlayer : whitePlayer;
        }
        List<GobangRoomParticipantVO> spectators = buildSpectators(room, userMap, profileMap);
        return new GobangRoomStateVO(
                room.getRoomId(),
                userId,
                spectator ? null : room.opponentOf(userId),
                room.getBlackUserId(),
                room.getWhiteUserId(),
                room.getCurrentTurnUserId(),
                room.getRoomStatus(),
                room.copyBoard(),
                room.getWinnerUserId(),
                room.getEndReason(),
                room.remainingGameMs(room.getBlackUserId(), now),
                room.remainingGameMs(room.getWhiteUserId(), now),
                room.currentTurnRemainingMs(now),
                now,
                spectator,
                room.isAiRoom(),
                room.isAiThinking(),
                room.getWinningLine(),
                blackPlayer,
                whitePlayer,
                opponentPlayer,
                spectators,
                spectators.size(),
                gameConnectionRegistry.countRoomOnline(room.getRoomId())
        );
    }

    private Map<Long, GameUserProfile> loadRoomProfiles(GobangRoom room) {
        List<Long> userIds = new ArrayList<>();
        addRealUserId(userIds, room.getBlackUserId());
        addRealUserId(userIds, room.getWhiteUserId());
        gameConnectionRegistry.roomUserIds(room.getRoomId()).forEach(id -> addRealUserId(userIds, id));
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, GameUserProfile> profileMap = new HashMap<>();
        gameUserProfileMapper.selectList(new LambdaQueryWrapper<GameUserProfile>()
                .eq(GameUserProfile::getGameCode, GameConstants.GOBANG)
                .eq(GameUserProfile::getDeleteState, (byte) 0)
                .in(GameUserProfile::getUserId, userIds))
                .forEach(profile -> profileMap.put(profile.getUserId(), profile));
        return profileMap;
    }

    private Map<Long, User> loadRoomUsers(GobangRoom room) {
        List<Long> userIds = new ArrayList<>();
        addRealUserId(userIds, room.getBlackUserId());
        addRealUserId(userIds, room.getWhiteUserId());
        gameConnectionRegistry.roomUserIds(room.getRoomId()).forEach(id -> addRealUserId(userIds, id));
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, User> userMap = new HashMap<>();
        userMapper.selectByIds(userIds).forEach(user -> userMap.put(user.getId(), user));
        return userMap;
    }

    private void addRealUserId(List<Long> userIds, Long userId) {
        if (userId != null && userId > 0 && !userIds.contains(userId)) {
            userIds.add(userId);
        }
    }

    private List<GobangRoomParticipantVO> buildSpectators(
            GobangRoom room,
            Map<Long, User> userMap,
            Map<Long, GameUserProfile> profileMap
    ) {
        Set<Long> onlineIds = gameConnectionRegistry.roomUserIds(room.getRoomId());
        List<GobangRoomParticipantVO> spectators = new ArrayList<>();
        onlineIds.forEach(userId -> {
            if (userId != null && !room.contains(userId)) {
                spectators.add(toParticipant(
                        userId,
                        "SPECTATOR",
                        room.getSpectatorJoinedAt().getOrDefault(userId, System.currentTimeMillis()),
                        null,
                        userMap,
                        profileMap
                ));
            }
        });
        spectators.sort(Comparator.comparing(GobangRoomParticipantVO::getJoinedAtMs));
        return spectators;
    }

    private GobangRoomParticipantVO toParticipant(
            Long userId,
            String role,
            Long joinedAtMs,
            String aiModelName,
            Map<Long, User> userMap,
            Map<Long, GameUserProfile> profileMap
    ) {
        if (GameConstants.AI_USER_ID.equals(userId)) {
            return new GobangRoomParticipantVO(
                    GameConstants.AI_USER_ID,
                    "ai",
                    "同水平AI",
                    "",
                    (byte) 0,
                    false,
                    "AI",
                    joinedAtMs,
                    true,
                    aiModelName == null || aiModelName.isBlank() ? DEFAULT_AI_MODEL_NAME : aiModelName,
                    0,
                    0
            );
        }
        User user = userMap.get(userId);
        String username = user == null ? null : user.getUsername();
        String nickname = user == null ? "用户 " + userId : (
                user.getNickname() == null || user.getNickname().isBlank() ? user.getUsername() : user.getNickname()
        );
        Byte vipTier = user == null || user.getVipTier() == null ? (byte) 0 : user.getVipTier();
        boolean vip = user != null && vipTier > 0
                && (user.getVipExpireAt() == null || user.getVipExpireAt().after(new Date()));
        GameUserProfile profile = profileMap.get(userId);
        int total = profile == null || profile.getTotalCount() == null ? 0 : profile.getTotalCount();
        int wins = profile == null || profile.getWinCount() == null ? 0 : profile.getWinCount();
        int winRate = total == 0 ? 0 : (int) Math.round(wins * 100.0 / total);
        return new GobangRoomParticipantVO(
                userId,
                username,
                nickname,
                user == null ? "" : user.getAvatarUrl(),
                vipTier,
                vip,
                role,
                joinedAtMs,
                false,
                null,
                total,
                winRate
        );
    }

    private void saveRoomPlayer(String roomId, Long userId, String role) {
        GameRoomPlayer row = new GameRoomPlayer();
        row.setGameCode(GameConstants.GOBANG);
        row.setRoomId(roomId);
        row.setUserId(userId);
        row.setRoomRole(role);
        row.setDeleteState((byte) 0);
        try {
            gameRoomPlayerMapper.insert(row);
        } catch (Exception e) {
            log.debug("保存游戏房间玩家映射失败 roomId={}, userId={}, role={}", roomId, userId, role);
        }
    }

    private void saveMove(GobangRoom room, Long userId, Integer row, Integer col, Integer chess, Long spentMs) {
        GameGobangRoomMove move = new GameGobangRoomMove();
        move.setRoomId(room.getRoomId());
        move.setMoveNo(countMoves(room) + 1);
        move.setUserId(userId);
        move.setRowIndex(row);
        move.setColIndex(col);
        move.setChess(chess);
        move.setSpentMs(spentMs);
        move.setDeleteState((byte) 0);
        gameGobangRoomMoveMapper.insert(move);
    }

    private int countMoves(GobangRoom room) {
        int count = 0;
        int[][] board = room.getBoard();
        for (int i = 0; i < GameConstants.BOARD_SIZE; i++) {
            for (int j = 0; j < GameConstants.BOARD_SIZE; j++) {
                if (board[i][j] != 0) {
                    count++;
                }
            }
        }
        return count;
    }

    private void scheduleAiMove(GobangRoom room) {
        boolean consultLlm = GameAiPlanner.shouldConsultLlm(room)
                && !gobangAiEngine.hasTacticalMove(room.getBoard());
        long minThinkMs = GameAiPlanner.minThinkMs(consultLlm);
        room.setAiThinking(true);
        sendStateToRoom(room, "room_state_updated");
        final boolean llm = consultLlm;
        CompletableFuture.runAsync(() -> executeAiTurn(room.getRoomId(), llm, minThinkMs));
    }

    private void executeAiTurn(String roomId, boolean consultLlm, long minThinkMs) {
        long started = System.currentTimeMillis();
        try {
            GobangRoom room = rooms.get(roomId);
            if (room == null) {
                return;
            }
            int[] move;
            synchronized (room) {
                if (!GameConstants.ROOM_PLAYING.equals(room.getRoomStatus())
                        || !GameConstants.AI_USER_ID.equals(room.getCurrentTurnUserId())) {
                    return;
                }
                move = chooseAiMove(room, consultLlm);
            }
            if (move == null) {
                return;
            }
            long elapsed = System.currentTimeMillis() - started;
            long wait = minThinkMs - elapsed;
            if (wait > 0) {
                Thread.sleep(wait);
            }
            synchronized (room) {
                if (!GameConstants.ROOM_PLAYING.equals(room.getRoomStatus())
                        || !GameConstants.AI_USER_ID.equals(room.getCurrentTurnUserId())) {
                    return;
                }
                if (!inBoard(move[0], move[1]) || room.getBoard()[move[0]][move[1]] != 0) {
                    return;
                }
                room.setAiMoveCount(room.getAiMoveCount() + 1);
                handleMove(roomId, GameConstants.AI_USER_ID, move[0], move[1], null);
                if (room.isAiRoom()) {
                    sendStateToRoom(room, "room_state_updated");
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            GobangRoom room = rooms.get(roomId);
            if (room != null) {
                room.setAiThinking(false);
                sendStateToRoom(room, "room_state_updated");
            }
        }
    }

    private int[] chooseAiMove(GobangRoom room, boolean consultLlm) {
        if (gobangAiEngine.hasTacticalMove(room.getBoard())) {
            room.setAiModelName(GameAiPlanner.formatModelLabel(room.getAiModelCode(), false, false));
            return gobangAiEngine.chooseMove(room.getBoard());
        }
        if (consultLlm) {
            int[] remoteMove = chooseRemoteAiMove(room);
            if (remoteMove != null) {
                return remoteMove;
            }
        }
        room.setAiModelName(GameAiPlanner.formatModelLabel(room.getAiModelCode(), false, false));
        return gobangAiEngine.chooseMove(room.getBoard());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private int[] chooseRemoteAiMove(GobangRoom room) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (aiInternalKey != null && !aiInternalKey.isBlank()) {
                headers.set("X-Internal-Key", aiInternalKey);
            }
            Map<String, Object> body = new HashMap<>();
            body.put("game_code", GameConstants.GOBANG);
            body.put("board", room.copyBoard());
            body.put("ai_chess", 2);
            body.put("player_chess", 1);
            body.put("room_id", room.getRoomId());
            body.put("model_code", room.getAiModelCode());
            body.put("use_llm", true);
            ResponseEntity<Map> response = gameAiRestTemplate.postForEntity(
                    AiHubUrls.gobangMoveUrl(),
                    new HttpEntity<>(body, headers),
                    Map.class
            );
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return null;
            }
            Map resp = response.getBody();
            if (parseInt(resp.get("code"), -1) != 200 || !(resp.get("data") instanceof Map data)) {
                return null;
            }
            int row = parseInt(data.get("row"), -1);
            int col = parseInt(data.get("col"), -1);
            if (!inBoard(row, col) || room.getBoard()[row][col] != 0) {
                return null;
            }
            String modelCode = parseModelCode(data, room.getAiModelCode());
            boolean fallback = Boolean.TRUE.equals(data.get("fallback"));
            room.setAiModelCode(modelCode);
            room.setAiModelName(GameAiPlanner.formatModelLabel(modelCode, !fallback, fallback));
            return new int[] { row, col };
        } catch (Exception e) {
            log.warn("五子棋 Python AI 不可用，使用本地智能引擎 roomId={}, error={}", room.getRoomId(), e.getMessage());
            return null;
        }
    }

    private String parseModelCode(Map data, String defaultModelCode) {
        Object raw = data.get("modelCode");
        if (raw == null) {
            raw = data.get("model");
        }
        String modelCode = raw == null ? "" : String.valueOf(raw).trim();
        if (AI_MODEL_PRO.equals(modelCode)) {
            return AI_MODEL_PRO;
        }
        if (AI_MODEL_FLASH.equals(modelCode)) {
            return AI_MODEL_FLASH;
        }
        return AI_MODEL_PRO.equals(defaultModelCode) ? AI_MODEL_PRO : AI_MODEL_FLASH;
    }

    private int parseInt(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value).trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private boolean inBoard(int row, int col) {
        return row >= 0 && row < GameConstants.BOARD_SIZE && col >= 0 && col < GameConstants.BOARD_SIZE;
    }

    private void sendRoomError(String roomId, Long userId, String requestId, String message) {
        try {
            gameConnectionRegistry.sendToRoom(
                    roomId,
                    userId,
                    objectMapper.writeValueAsString(GameWsResponse.fail("move_rejected", requestId, message))
            );
        } catch (Exception e) {
            log.debug("发送五子棋错误响应失败 roomId={}, userId={}, error={}", roomId, userId, e.getMessage());
        }
    }

    private void sendFinalStateToRoom(GobangRoom room) {
        sendStateToRoom(room, "game_finished");
    }

    private void sendStateToRoom(GobangRoom room, String type) {
        gameConnectionRegistry.forEachRoomSession(room.getRoomId(), (userId, session) -> {
            try {
                gameConnectionRegistry.send(
                        session,
                        objectMapper.writeValueAsString(GameWsResponse.ok(
                                type,
                                null,
                                toStateVO(room, userId)
                        ))
                );
            } catch (Exception e) {
                log.debug("发送五子棋最终状态失败 roomId={}, userId={}, error={}",
                        room.getRoomId(),
                        userId,
                        e.getMessage());
            }
        });
        publishGenericRoomState(room, type);
    }

    private void publishGenericRoomState(GobangRoom room, String type) {
        try {
            String payload = objectMapper.writeValueAsString(GameWsResponse.ok(
                    type,
                    null,
                    toStateVO(room, null)
            ));
            gameRoomEventBusService.publishRoomEvent(room.getRoomId(), payload);
        } catch (Exception e) {
            log.debug("发布五子棋房间状态事件失败 roomId={}, error={}", room.getRoomId(), e.getMessage());
        }
    }

    private void broadcast(String roomId, GameWsResponse<?> response) {
        try {
            String payload = objectMapper.writeValueAsString(response);
            gameConnectionRegistry.broadcastRoom(roomId, payload);
            gameRoomEventBusService.publishRoomEvent(roomId, payload);
        } catch (Exception e) {
            log.debug("广播五子棋房间消息失败 roomId={}, error={}", roomId, e.getMessage());
        }
    }
}
