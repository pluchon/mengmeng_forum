package org.pluchon.forum.service.impl.game;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.mq.ForumProducer;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.websocket.game.GameConnectionRegistry;
import org.pluchon.forum.common.websocket.game.GameWsResponse;
import org.pluchon.forum.entity.db.GameGobangMatchRecord;
import org.pluchon.forum.entity.db.GameGobangRoomMove;
import org.pluchon.forum.entity.db.GameRoomPlayer;
import org.pluchon.forum.entity.db.GameSettlementEvent;
import org.pluchon.forum.entity.db.GameUserProfile;
import org.pluchon.forum.api.UserInternalVO;
import org.pluchon.forum.api.ai.AiGobangMoveRequest;
import org.pluchon.forum.api.ai.AiGobangMoveVO;
import org.pluchon.forum.service.remote.GameAiGatewayService;
import org.pluchon.forum.entity.bo.game.GameRankSettlementCommand;
import org.pluchon.forum.entity.bo.game.GameRankSettlementResult;
import org.pluchon.forum.entity.dto.game.GobangChatRequest;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.entity.vo.game.GobangActiveRoomVO;
import org.pluchon.forum.entity.vo.game.GobangChatVO;
import org.pluchon.forum.entity.vo.game.GobangMoveVO;
import org.pluchon.forum.entity.vo.game.GobangRoomParticipantVO;
import org.pluchon.forum.entity.vo.game.GobangRoomStateVO;
import org.pluchon.forum.entity.vo.mq.GameFinishedMqVO;
import org.pluchon.forum.mapper.GameGobangMatchRecordMapper;
import org.pluchon.forum.mapper.GameGobangRoomMoveMapper;
import org.pluchon.forum.mapper.GameRoomPlayerMapper;
import org.pluchon.forum.mapper.GameSettlementEventMapper;
import org.pluchon.forum.mapper.GameUserProfileMapper;
import org.pluchon.forum.service.security.GameUserLookupService;
import org.pluchon.forum.service.interfaces.game.GameUserProfileService;
import org.pluchon.forum.service.interfaces.game.GameRankService;
import org.pluchon.forum.service.interfaces.game.GameRoomEventBusService;
import org.pluchon.forum.service.interfaces.game.GameRoomStateCacheService;
import org.pluchon.forum.service.interfaces.game.GobangRoomService;
import org.pluchon.forum.api.ai.AiGobangBoardInsight;
import org.pluchon.forum.service.impl.game.ai.GameAiPlanner;
import org.pluchon.forum.service.impl.game.ai.GobangAiDifficultyProfile;
import org.pluchon.forum.service.impl.game.ai.GobangAiEngine;
import org.pluchon.forum.service.impl.game.ai.GobangThreatDetector;
import org.pluchon.forum.service.impl.game.guard.GobangActionContext;
import org.pluchon.forum.service.impl.game.guard.GobangGuardChain;
import org.pluchon.forum.service.impl.game.guard.GobangGuardResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.Executor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

// 五子棋房间服务，服务端持有权威棋盘并负责唯一结算
@Slf4j
@ConditionalOnProperty(name = "forum.features.game-runtime", havingValue = "true")
@Service
public class GobangRoomServiceImpl implements GobangRoomService {

    private static final long RECONNECT_WINDOW_MS = GameConstants.GOBANG_RECONNECT_WINDOW_MS;

    private static final int AI_PRO_SCORE_THRESHOLD = 1600;

    private static final String AI_MODEL_FLASH = "qwen3.7-flash";

    private static final String AI_MODEL_PRO = "qwen3.7-max";

    private static final String DEFAULT_AI_MODEL_NAME = AI_MODEL_FLASH;

    // roomId > 房间状态
    private final ConcurrentHashMap<String, GobangRoom> rooms = new ConcurrentHashMap<>();

    // 房间号必须对活跃房间查重：撞号会让后建的房间把先建的从 rooms 里挤掉
    @Autowired
    private GameLobbyBroadcaster gameLobbyBroadcaster;

    private String nextRoomId() {
        return GameRoomIdGenerator.generateRoomId(rooms::containsKey);
    }

    // userId > roomId，用于防止同一用户进入多个房间
    private final ConcurrentHashMap<Long, String> userRoomIds = new ConcurrentHashMap<>();

    @Autowired
    private GameConnectionRegistry gameConnectionRegistry;

    @Autowired
    private GameUserProfileService gameUserProfileService;

    @Autowired
    private GameRankService gameRankService;

    @Autowired
    private GameRoomEventBusService gameRoomEventBusService;

    @Autowired
    private GameRoomStateCacheService gameRoomStateCacheService;

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
    private GameUserLookupService gameUserLookupService;

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

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    @Qualifier("gameAiExecutor")
    private Executor gameAiExecutor;

    @Autowired
    private GameMatchRoomHelper gameMatchRoomHelper;

    private final GobangGuardChain gobangGuardChain = GobangGuardChain.defaultChain();

    @Autowired
    private GameAiGatewayService gameAiGatewayService;

    @Override
    public String createMatchedRoom(Long userIdA, Long userIdB) {
        if (userIdA == null || userIdB == null || userIdA.equals(userIdB)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        return gameMatchRoomHelper.resolveMatchedRoomId(
                "gobang", userIdA, userIdB,
                () -> createMatchedRoomInternal(userIdA, userIdB),
                rooms::containsKey);
    }

    private String createMatchedRoomInternal(Long userIdA, Long userIdB) {
        GobangRoom room = new GobangRoom(nextRoomId(), userIdA, userIdB);
        room.setRoomStatus(GameConstants.ROOM_PLAYING);
        rooms.put(room.getRoomId(), room);
        gameLobbyBroadcaster.roomsChanged(GameConstants.GOBANG);
        userRoomIds.put(userIdA, room.getRoomId());
        userRoomIds.put(userIdB, room.getRoomId());
        gameUserProfileService.updateStatus(userIdA, GameConstants.GOBANG, GameConstants.PROFILE_PLAYING, room.getRoomId());
        gameUserProfileService.updateStatus(userIdB, GameConstants.GOBANG, GameConstants.PROFILE_PLAYING, room.getRoomId());
        saveRoomPlayer(room.getRoomId(), userIdA, "BLACK");
        saveRoomPlayer(room.getRoomId(), userIdB, "WHITE");
        cacheRoomState(room);
        return room.getRoomId();
    }

    @Override
    public String createAiRoom(Long userId) {
        if (userId == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        GobangRoom room = new GobangRoom(nextRoomId(), userId, GameConstants.AI_USER_ID);
        room.setAiRoom(true);
        String modelCode = chooseAiModelCode(userId);
        room.setAiModelCode(modelCode);
        room.setAiModelName(GameAiPlanner.formatModelLabel(modelCode, false, false));
        GobangAiDifficultyProfile difficulty = chooseAiDifficulty(userId);
        room.setAiSearchDepth(difficulty.depth());
        room.setAiMaxCandidates(difficulty.maxCandidates());
        room.setRoomStatus(GameConstants.ROOM_PLAYING);
        rooms.put(room.getRoomId(), room);
        userRoomIds.put(userId, room.getRoomId());
        gameUserProfileService.updateStatus(userId, GameConstants.GOBANG, GameConstants.PROFILE_PLAYING, room.getRoomId());
        saveRoomPlayer(room.getRoomId(), userId, "BLACK");
        saveRoomPlayer(room.getRoomId(), GameConstants.AI_USER_ID, "AI");
        cacheRoomState(room);
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
            sendStateToRoom(room, "room_state_updated");
        }
        return getRoomState(roomId, userId);
    }

    @Override
    public GobangRoomStateVO getRoomState(String roomId, Long userId) {
        GobangRoom room = rooms.get(roomId);
        if (room == null) {
            GobangRoomStateVO cached = gameRoomStateCacheService.getState(
                    GameConstants.GOBANG,
                    roomId,
                    userId,
                    GobangRoomStateVO.class
            );
            if (cached != null) {
                return cached;
            }
            throw new ApplicationException(Result.fail(ResultCode.FAILED_FORBIDDEN));
        }
        return toStateVO(room, userId);
    }

    @Override
    public boolean hasLocalRoom(String roomId) {
        return roomId != null && rooms.containsKey(roomId);
    }

    @Override
    public void pushRoomState(String roomId, String requestId) {
        GobangRoom room = rooms.get(roomId);
        if (room == null) {
            return;
        }
        sendStateToRoom(room, "room_state_updated", requestId);
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
            cacheRoomState(room);
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
                request == null ? null : request.getEmojiUrl()
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
    public PageResult<GobangActiveRoomVO> pageActiveRooms(String roomId, Integer pageNum, Integer pageSize) {
        String wanted = roomId == null ? "" : roomId.trim();
        if (!wanted.isEmpty() && !GameRoomIdGenerator.isValidRoomId(wanted)) {
            // 房间号固定 6 位数字，非法输入直接返回空而不是拿去查
            return GameActiveRoomPaging.emptyPage(pageNum, pageSize);
        }
        List<GobangRoom> matched = new ArrayList<>();
        Set<Long> userIds = new HashSet<>();
        for (GobangRoom room : rooms.values()) {
            if (!GameConstants.ROOM_PLAYING.equals(room.getRoomStatus())) {
                continue;
            }
            if (!wanted.isEmpty() && !wanted.equals(room.getRoomId())) {
                continue;
            }
            matched.add(room);
        }
        matched.sort(Comparator.comparing(GobangRoom::getStartedAt,
                Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        // 先分页再查用户信息：原来是把所有房间的对局者一次性全查出来
        List<GobangRoom> pageRooms = GameActiveRoomPaging.slice(matched, pageNum, pageSize);
        for (GobangRoom room : pageRooms) {
            collectRealUserId(userIds, room.getBlackUserId());
            collectRealUserId(userIds, room.getWhiteUserId());
        }
        Map<Long, UserInternalVO> userMap = loadActiveRoomUsers(userIds);
        List<GobangActiveRoomVO> rows = new ArrayList<>(pageRooms.size());
        for (GobangRoom room : pageRooms) {
            rows.add(new GobangActiveRoomVO(
                    room.getRoomId(),
                    room.getBlackUserId(),
                    activeRoomNickname(userMap.get(room.getBlackUserId()), room.getBlackUserId()),
                    room.getWhiteUserId(),
                    room.isAiRoom() ? "AI" : activeRoomNickname(userMap.get(room.getWhiteUserId()), room.getWhiteUserId()),
                    room.getCurrentTurnUserId(),
                    room.isAiRoom(),
                    room.getStartedAt()
            ));
        }
        return GameActiveRoomPaging.toPage(rows, matched.size(), pageNum, pageSize);
    }

    private void collectRealUserId(Set<Long> userIds, Long userId) {
        if (userId != null && !GameConstants.AI_USER_ID.equals(userId)) {
            userIds.add(userId);
        }
    }

    private Map<Long, UserInternalVO> loadActiveRoomUsers(Set<Long> userIds) {
        Map<Long, UserInternalVO> userMap = new HashMap<>();
        if (userIds.isEmpty()) {
            return userMap;
        }
        gameUserLookupService.loadActiveUsers(userIds).forEach(userMap::put);
        return userMap;
    }

    private String activeRoomNickname(UserInternalVO user, Long userId) {
        if (user == null) {
            return "用户 " + userId;
        }
        if (user.getNickname() != null && !user.getNickname().isBlank()) {
            return user.getNickname();
        }
        if (user.getUsername() != null && !user.getUsername().isBlank()) {
            return user.getUsername();
        }
        return "用户 " + userId;
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
        gameLobbyBroadcaster.roomsChanged(GameConstants.GOBANG);
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
            GameRankSettlementCommand rankCommand = createRankCommand(room, winnerId, loserId, endReason);
            GameRankSettlementResult rankResult = gameRankService.settleRank(rankCommand);
            int scoreDelta = winnerDelta(rankResult);
            record.setScoreDelta(scoreDelta);
            record.setWinnerScoreDelta(scoreDelta);
            record.setLoserScoreDelta(loserDelta(rankResult));
            record.setStartedAt(room.getStartedAt());
            record.setEndedAt(new Date());
            record.setDeleteState(GameConstants.NOT_DELETED);
            gameGobangMatchRecordMapper.insert(record);
            return createGameFinishedEvent(room, record, winnerId, loserId, endReason);
        });
        publishGameFinishedEvent(finishedEvent);
        sendFinalStateToRoom(room);
        cleanupRoom(room);
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
        event.setDeleteState(GameConstants.NOT_DELETED);
        gameSettlementEventMapper.insert(event);
        return new GameFinishedMqVO(
                eventId,
                GameConstants.GOBANG,
                room.getRoomId(),
                record.getId(),
                winnerId,
                loserId,
                endReason
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

    private GobangAiDifficultyProfile chooseAiDifficulty(Long userId) {
        GameUserProfile profile = gameUserProfileService.getOrCreateProfile(userId, GameConstants.GOBANG);
        int score = profile == null || profile.getScore() == null ? 0 : profile.getScore();
        return GobangAiDifficultyProfile.ofScore(score);
    }

    private GobangAiDifficultyProfile difficultyOf(GobangRoom room) {
        if (room == null) {
            return GobangAiDifficultyProfile.ofScore(0);
        }
        return GobangAiDifficultyProfile.of(room.getAiSearchDepth(), room.getAiMaxCandidates());
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

    private GameRankSettlementCommand createRankCommand(GobangRoom room, Long winnerId, Long loserId, String endReason) {
        GameRankSettlementCommand command = new GameRankSettlementCommand();
        command.setGameCode(GameConstants.GOBANG);
        command.setRoomId(room.getRoomId());
        command.setPlayerAUserId(room.getBlackUserId());
        command.setPlayerBUserId(room.getWhiteUserId());
        command.setWinnerUserId(winnerId);
        command.setLoserUserId(loserId);
        command.setEndReason(endReason);
        command.setEffectiveForRank(true);
        return command;
    }

    private int winnerDelta(GameRankSettlementResult result) {
        return result == null || result.getWinnerChange() == null || result.getWinnerChange().getDelta() == null
                ? 0
                : Math.max(0, result.getWinnerChange().getDelta());
    }

    private int loserDelta(GameRankSettlementResult result) {
        return result == null || result.getLoserChange() == null || result.getLoserChange().getDelta() == null
                ? 0
                : Math.min(0, result.getLoserChange().getDelta());
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
        Map<Long, UserInternalVO> userMap = loadRoomUsers(room);
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
                .eq(GameUserProfile::getDeleteState, GameConstants.NOT_DELETED)
                .in(GameUserProfile::getUserId, userIds))
                .forEach(profile -> profileMap.put(profile.getUserId(), profile));
        return profileMap;
    }

    private Map<Long, UserInternalVO> loadRoomUsers(GobangRoom room) {
        List<Long> userIds = new ArrayList<>();
        addRealUserId(userIds, room.getBlackUserId());
        addRealUserId(userIds, room.getWhiteUserId());
        gameConnectionRegistry.roomUserIds(room.getRoomId()).forEach(id -> addRealUserId(userIds, id));
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, UserInternalVO> userMap = new HashMap<>();
        gameUserLookupService.listByIds(userIds).forEach(user -> userMap.put(user.getId(), user));
        return userMap;
    }

    private void addRealUserId(List<Long> userIds, Long userId) {
        if (userId != null && userId > 0 && !userIds.contains(userId)) {
            userIds.add(userId);
        }
    }

    private List<GobangRoomParticipantVO> buildSpectators(
            GobangRoom room,
            Map<Long, UserInternalVO> userMap,
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
            Map<Long, UserInternalVO> userMap,
            Map<Long, GameUserProfile> profileMap
    ) {
        if (GameConstants.AI_USER_ID.equals(userId)) {
            return new GobangRoomParticipantVO(
                    GameConstants.AI_USER_ID,
                    "ai",
                    "智能对手",
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
        UserInternalVO user = userMap.get(userId);
        String username = user == null ? null : user.getUsername();
        String nickname = user == null ? "用户 " + userId : (
                user.getNickname() == null || user.getNickname().isBlank() ? user.getUsername() : user.getNickname()
        );
        Byte vipTier = 0;
        boolean vip = false;
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
        row.setDeleteState(GameConstants.NOT_DELETED);
        try {
            gameRoomPlayerMapper.insert(row);
        } catch (Exception e) {
            log.warn("保存游戏房间玩家映射失败 roomId={}, userId={}, role={}, error={}", roomId, userId, role, e.getMessage());
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
        move.setDeleteState(GameConstants.NOT_DELETED);
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
        GobangAiDifficultyProfile difficulty = difficultyOf(room);
        boolean forcedThreat = gobangAiEngine.hasForcedThreat(room.getBoard());
        int scoreSpread = gobangAiEngine.candidateScoreSpread(room.getBoard(), difficulty);
        boolean consultLlm = !forcedThreat && GameAiPlanner.shouldConsultLlm(room, scoreSpread);
        long minThinkMs = GameAiPlanner.minThinkMs(consultLlm);
        room.setAiThinking(true);
        sendStateToRoom(room, "room_state_updated");
        final boolean llm = consultLlm;
        CompletableFuture.runAsync(() -> executeAiTurn(room.getRoomId(), llm, minThinkMs), gameAiExecutor);
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
        GobangAiDifficultyProfile difficulty = difficultyOf(room);
        GobangThreatDetector.ThreatHit forced = gobangAiEngine.findForcedThreat(room.getBoard());
        if (forced != null) {
            room.setAiModelName(GameAiPlanner.formatModelLabel(room.getAiModelCode(), false, false));
            return new int[] { forced.row(), forced.col() };
        }
        if (consultLlm) {
            int[] remoteMove = chooseRemoteAiMove(room, difficulty);
            if (remoteMove != null) {
                return remoteMove;
            }
        }
        room.setAiModelName(GameAiPlanner.formatModelLabel(room.getAiModelCode(), false, false));
        return gobangAiEngine.chooseMove(room.getBoard(), difficulty);
    }

    private int[] chooseRemoteAiMove(GobangRoom room, GobangAiDifficultyProfile difficulty) {
        try {
            AiGobangBoardInsight insight = gobangAiEngine.buildBoardInsight(room.getBoard(), difficulty);
            AiGobangMoveRequest request = new AiGobangMoveRequest();
            request.setBoard(room.copyBoard());
            request.setAiChess(2);
            request.setModelCode(room.getAiModelCode());
            request.setInsight(insight);
            AiGobangMoveVO move = gameAiGatewayService.chooseGobangMove(request);
            if (move == null || move.getRow() == null || move.getCol() == null) {
                return null;
            }
            int row = move.getRow();
            int col = move.getCol();
            if (!inBoard(row, col) || room.getBoard()[row][col] != 0) {
                return null;
            }
            GobangThreatDetector.ThreatHit mustBlock = gobangAiEngine.findMustBlock(room.getBoard());
            if (mustBlock != null && (mustBlock.row() != row || mustBlock.col() != col)) {
                room.setAiModelName(GameAiPlanner.formatModelLabel(room.getAiModelCode(), false, true));
                return new int[] { mustBlock.row(), mustBlock.col() };
            }
            if (!isCandidateMove(insight, row, col)) {
                return null;
            }
            String modelCode = parseModelCode(move.getModelCode(), room.getAiModelCode());
            boolean fallback = Boolean.TRUE.equals(move.getFallback());
            room.setAiModelCode(modelCode);
            room.setAiModelName(GameAiPlanner.formatModelLabel(modelCode, !fallback, fallback));
            return new int[] { row, col };
        } catch (Exception e) {
            log.warn("五子棋 Python AI 不可用，使用本地智能引擎 roomId={}, error={}", room.getRoomId(), e.getMessage());
            return null;
        }
    }

    private boolean isCandidateMove(AiGobangBoardInsight insight, int row, int col) {
        if (insight == null || insight.getCandidateMoves() == null || insight.getCandidateMoves().isEmpty()) {
            return true;
        }
        for (AiGobangBoardInsight.CandidateMove candidate : insight.getCandidateMoves()) {
            if (candidate != null
                    && candidate.getRow() != null
                    && candidate.getCol() != null
                    && candidate.getRow() == row
                    && candidate.getCol() == col) {
                return true;
            }
        }
        return false;
    }

    private String parseModelCode(String raw, String defaultModelCode) {
        String modelCode = raw == null ? "" : raw.trim();
        if (AI_MODEL_PRO.equals(modelCode)) {
            return AI_MODEL_PRO;
        }
        if (AI_MODEL_FLASH.equals(modelCode)) {
            return AI_MODEL_FLASH;
        }
        return AI_MODEL_PRO.equals(defaultModelCode) ? AI_MODEL_PRO : AI_MODEL_FLASH;
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
        sendStateToRoom(room, type, null);
    }

    private void sendStateToRoom(GobangRoom room, String type, String requestId) {
        cacheRoomState(room);
        gameConnectionRegistry.forEachRoomSession(room.getRoomId(), (userId, session) -> {
            try {
                gameConnectionRegistry.send(
                        session,
                        objectMapper.writeValueAsString(GameWsResponse.ok(
                                type,
                                requestId,
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
        publishPlayerRoomState(room, type, requestId);
    }

    private void publishPlayerRoomState(GobangRoom room, String type, String requestId) {
        try {
            String blackPayload = objectMapper.writeValueAsString(GameWsResponse.ok(
                    type,
                    requestId,
                    toStateVO(room, room.getBlackUserId())
            ));
            gameRoomEventBusService.publishRoomUserEvent(room.getRoomId(), room.getBlackUserId(), blackPayload);
            if (!GameConstants.AI_USER_ID.equals(room.getWhiteUserId())) {
                String whitePayload = objectMapper.writeValueAsString(GameWsResponse.ok(
                        type,
                        requestId,
                        toStateVO(room, room.getWhiteUserId())
                ));
                gameRoomEventBusService.publishRoomUserEvent(room.getRoomId(), room.getWhiteUserId(), whitePayload);
            }
        } catch (Exception e) {
            log.debug("发布五子棋房间状态事件失败 roomId={}, error={}", room.getRoomId(), e.getMessage());
        }
    }

    private void cacheRoomState(GobangRoom room) {
        if (room == null) {
            return;
        }
        gameRoomStateCacheService.saveState(
                GameConstants.GOBANG,
                room.getRoomId(),
                room.getBlackUserId(),
                toStateVO(room, room.getBlackUserId())
        );
        if (!GameConstants.AI_USER_ID.equals(room.getWhiteUserId())) {
            gameRoomStateCacheService.saveState(
                    GameConstants.GOBANG,
                    room.getRoomId(),
                    room.getWhiteUserId(),
                    toStateVO(room, room.getWhiteUserId())
            );
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
