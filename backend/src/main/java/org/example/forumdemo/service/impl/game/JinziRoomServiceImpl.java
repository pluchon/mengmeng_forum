package org.example.forumdemo.service.impl.game;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.mq.ForumProducer;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.common.websocket.game.GameConnectionRegistry;
import org.example.forumdemo.common.websocket.game.GameWsResponse;
import org.example.forumdemo.entity.bo.game.GameRankSettlementCommand;
import org.example.forumdemo.entity.bo.game.GameRankSettlementResult;
import org.example.forumdemo.entity.db.GameJinziMatchRecord;
import org.example.forumdemo.entity.db.GameJinziRoomMove;
import org.example.forumdemo.entity.db.GameRoomPlayer;
import org.example.forumdemo.entity.db.GameSettlementEvent;
import org.example.forumdemo.entity.db.GameUserProfile;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.dto.game.GobangChatRequest;
import org.example.forumdemo.entity.vo.game.GameRoomSnapshotVO;
import org.example.forumdemo.entity.vo.game.GobangBoardPointVO;
import org.example.forumdemo.entity.vo.game.GobangRoomParticipantVO;
import org.example.forumdemo.entity.vo.game.JinziBoardPointVO;
import org.example.forumdemo.entity.vo.game.JinziChatVO;
import org.example.forumdemo.entity.vo.game.JinziMoveVO;
import org.example.forumdemo.entity.vo.game.JinziRoomStateVO;
import org.example.forumdemo.entity.vo.mq.GameFinishedMqVO;
import org.example.forumdemo.mapper.GameJinziMatchRecordMapper;
import org.example.forumdemo.mapper.GameJinziRoomMoveMapper;
import org.example.forumdemo.mapper.GameRoomPlayerMapper;
import org.example.forumdemo.mapper.GameSettlementEventMapper;
import org.example.forumdemo.mapper.GameUserProfileMapper;
import org.example.forumdemo.mapper.UserMapper;
import org.example.forumdemo.service.interfaces.game.GameRoomEventBusService;
import org.example.forumdemo.service.interfaces.game.GameRoomStateCacheService;
import org.example.forumdemo.service.interfaces.game.GameRoomSnapshotService;
import org.example.forumdemo.service.interfaces.game.GameUserProfileService;
import org.example.forumdemo.service.interfaces.game.GameRankService;
import org.example.forumdemo.service.interfaces.game.JinziRoomService;
import org.example.forumdemo.service.interfaces.points.PointsService;
import org.example.forumdemo.service.impl.game.ai.GameAiPlanner;
import org.example.forumdemo.service.impl.game.ai.JinziAiEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

// 井字棋房间服务，复用游戏结算链路但不开放观战角色
@Slf4j
@Service
public class JinziRoomServiceImpl implements JinziRoomService {

    private static final int AI_PRO_SCORE_THRESHOLD = 1600;

    private static final String AI_MODEL_FLASH = "deepseek-v4-flash";

    private static final String AI_MODEL_PRO = "deepseek-v4-pro";

    private static final String DEFAULT_AI_MODEL_NAME = AI_MODEL_FLASH;

    // roomId -> 房间状态
    private final ConcurrentHashMap<String, JinziRoom> rooms = new ConcurrentHashMap<>();

    // userId -> roomId，用于防止同一用户进入多个房间
    private final ConcurrentHashMap<Long, String> userRoomIds = new ConcurrentHashMap<>();

    @Autowired
    private GameConnectionRegistry gameConnectionRegistry;

    @Autowired
    private GameUserProfileService gameUserProfileService;

    @Autowired
    private GameRankService gameRankService;

    @Autowired
    private GameRoomSnapshotService gameRoomSnapshotService;

    @Autowired
    private GameRoomEventBusService gameRoomEventBusService;

    @Autowired
    private GameRoomStateCacheService gameRoomStateCacheService;

    @Autowired
    private GameUserProfileMapper gameUserProfileMapper;

    @Autowired
    private GameJinziMatchRecordMapper gameJinziMatchRecordMapper;

    @Autowired
    private GameRoomPlayerMapper gameRoomPlayerMapper;

    @Autowired
    private GameJinziRoomMoveMapper gameJinziRoomMoveMapper;

    @Autowired
    private GameSettlementEventMapper gameSettlementEventMapper;

    @Autowired
    private PointsService pointsService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JinziRuleEngine jinziRuleEngine;

    @Autowired
    private JinziAiEngine jinziAiEngine;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private ForumProducer forumProducer;

    @Autowired
    private GameMatchRoomHelper gameMatchRoomHelper;

    @Override
    public String createMatchedRoom(Long userIdA, Long userIdB) {
        if (userIdA == null || userIdB == null || userIdA.equals(userIdB)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        return gameMatchRoomHelper.resolveMatchedRoomId(
                "jinzi", userIdA, userIdB,
                () -> createMatchedRoomInternal(userIdA, userIdB),
                rooms::containsKey);
    }

    private String createMatchedRoomInternal(Long userIdA, Long userIdB) {
        JinziRoom room = new JinziRoom(userIdA, userIdB);
        room.setRoomStatus(GameConstants.ROOM_PLAYING);
        rooms.put(room.getRoomId(), room);
        userRoomIds.put(userIdA, room.getRoomId());
        userRoomIds.put(userIdB, room.getRoomId());
        gameUserProfileService.updateStatus(userIdA, GameConstants.JINZI, GameConstants.PROFILE_PLAYING, room.getRoomId());
        gameUserProfileService.updateStatus(userIdB, GameConstants.JINZI, GameConstants.PROFILE_PLAYING, room.getRoomId());
        saveRoomPlayer(room.getRoomId(), userIdA, "BLACK");
        saveRoomPlayer(room.getRoomId(), userIdB, "WHITE");
        saveRoomSnapshot(room);
        cacheRoomState(room);
        return room.getRoomId();
    }

    @Override
    public String createAiRoom(Long userId) {
        if (userId == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        JinziRoom room = new JinziRoom(userId, GameConstants.AI_USER_ID);
        room.setAiRoom(true);
        String modelCode = chooseAiModelCode(userId);
        room.setAiModelCode(modelCode);
        room.setAiModelName(GameAiPlanner.formatModelLabel(modelCode, false, false));
        room.setRoomStatus(GameConstants.ROOM_PLAYING);
        rooms.put(room.getRoomId(), room);
        userRoomIds.put(userId, room.getRoomId());
        gameUserProfileService.updateStatus(userId, GameConstants.JINZI, GameConstants.PROFILE_PLAYING, room.getRoomId());
        saveRoomPlayer(room.getRoomId(), userId, "BLACK");
        saveRoomPlayer(room.getRoomId(), GameConstants.AI_USER_ID, "AI");
        saveRoomSnapshot(room);
        cacheRoomState(room);
        return room.getRoomId();
    }

    @Override
    public JinziRoomStateVO joinRoom(String roomId, Long userId, WebSocketSession session) {
        JinziRoom room = requireRoom(roomId, userId);
        gameConnectionRegistry.enterRoom(roomId, userId, session);
        room.getDisconnectDeadlines().remove(userId);
        broadcast(roomId, GameWsResponse.ok("peer_reconnected", null, getRoomState(roomId, userId)));
        return getRoomState(roomId, userId);
    }

    @Override
    public JinziRoomStateVO getRoomState(String roomId, Long userId) {
        JinziRoom room = rooms.get(roomId);
        if (room == null) {
            JinziRoomStateVO cached = gameRoomStateCacheService.getState(
                    GameConstants.JINZI,
                    roomId,
                    userId,
                    JinziRoomStateVO.class
            );
            if (cached != null) {
                return cached;
            }
            throw new ApplicationException(Result.fail(ResultCode.FAILED_FORBIDDEN));
        }
        if (!room.contains(userId)) {
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
        JinziRoom room = rooms.get(roomId);
        if (room == null) {
            return;
        }
        sendStateToRoom(room, "room_state_updated", requestId);
    }

    @Override
    public void handleMove(String roomId, Long userId, Integer row, Integer col, String requestId) {
        requireActionParams(roomId, userId);
        JinziRoom room = rooms.get(roomId);
        if (room == null) {
            sendRoomError(roomId, userId, requestId, "当前对战已经结束，不能继续落子");
            return;
        }
        synchronized (room) {
            String error = validateMove(room, userId, row, col);
            if (error != null) {
                sendRoomError(roomId, userId, requestId, error);
                return;
            }
            int chess = room.chessOf(userId);
            long now = System.currentTimeMillis();
            long spentMs = Math.max(0, now - room.getTurnStartedAtMs());
            saveMove(room, userId, row, col, chess, spentMs);
            room.consumeTurnTime(now);
            room.getBoard()[row][col] = chess;

            boolean win = jinziRuleEngine.hasLine(room.getBoard(), chess);
            boolean draw = !win && jinziRuleEngine.isDraw(room.getBoard());
            Long nextTurnUserId = win || draw ? null : room.opponentOf(userId);
            if (win) {
                room.setWinningLine(jinziRuleEngine.winningLine(room.getBoard(), chess));
            }
            room.setCurrentTurnUserId(nextTurnUserId);
            room.setTurnStartedAtMs(System.currentTimeMillis());
            saveRoomSnapshot(room);
            cacheRoomState(room);
            JinziMoveVO moveVO = new JinziMoveVO(
                    userId,
                    row,
                    col,
                    chess,
                    nextTurnUserId,
                    win ? userId : null,
                    win ? GameConstants.END_LINE : (draw ? GameConstants.END_DRAW : null),
                    win ? room.getWinningLine() : null
            );
            broadcast(roomId, GameWsResponse.ok("move_accepted", requestId, moveVO));
            if (win) {
                finishRoom(room, userId, GameConstants.END_LINE);
            } else if (draw) {
                finishRoom(room, null, GameConstants.END_DRAW);
            } else if (room.isAiRoom() && GameConstants.AI_USER_ID.equals(room.getCurrentTurnUserId())) {
                scheduleAiMove(room);
            }
        }
    }

    @Override
    public void surrender(String roomId, Long userId, String requestId) {
        requireActionParams(roomId, userId);
        JinziRoom room = rooms.get(roomId);
        if (room == null) {
            sendRoomError(roomId, userId, requestId, "当前对战已经结束，不能认输");
            return;
        }
        synchronized (room) {
            if (!GameConstants.ROOM_PLAYING.equals(room.getRoomStatus())) {
                sendRoomError(roomId, userId, requestId, "当前对战已经结束，不能认输");
                return;
            }
            if (!room.contains(userId)) {
                sendRoomError(roomId, userId, requestId, "只有对局玩家可以认输");
                return;
            }
            finishRoom(room, room.opponentOf(userId), GameConstants.END_SURRENDER);
        }
    }

    @Override
    public void chat(String roomId, Long userId, GobangChatRequest request, String requestId) {
        requireActionParams(roomId, userId);
        JinziRoom room = rooms.get(roomId);
        String error = validateChat(room, userId, request);
        if (error != null) {
            sendRoomError(roomId, userId, requestId, error);
            return;
        }
        String messageType = request == null || request.getMessageType() == null
                ? "TEXT"
                : request.getMessageType().trim().toUpperCase();
        String content = request == null || request.getContent() == null ? "" : request.getContent().trim();
        JinziChatVO vo = new JinziChatVO(
                userId,
                messageType,
                content,
                request == null ? null : request.getEmojiId(),
                request == null ? null : request.getEmojiUrl(),
                System.currentTimeMillis()
        );
        broadcast(roomId, GameWsResponse.ok("room_chat", requestId, vo));
    }

    @Override
    public void handleDisconnect(String roomId, Long userId, WebSocketSession session) {
        JinziRoom room = rooms.get(roomId);
        gameConnectionRegistry.exitRoom(roomId, userId, session);
        if (room == null || userId == null) {
            return;
        }
        synchronized (room) {
            if (!GameConstants.ROOM_PLAYING.equals(room.getRoomStatus())) {
                return;
            }
            room.getDisconnectDeadlines().put(userId, System.currentTimeMillis() + GameConstants.JINZI_RECONNECT_WINDOW_MS);
            broadcast(roomId, GameWsResponse.ok("peer_disconnected", null, toStateVO(room, room.opponentOf(userId))));
        }
    }

    // 定时处理超过重连窗口的玩家，避免短局因断线永久悬挂
    @Scheduled(fixedDelay = 5_000)
    public void settleExpiredDisconnects() {
        long now = System.currentTimeMillis();
        for (JinziRoom room : rooms.values()) {
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
        for (JinziRoom room : rooms.values()) {
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

    private String validateMove(JinziRoom room, Long userId, Integer row, Integer col) {
        if (!GameConstants.ROOM_PLAYING.equals(room.getRoomStatus())) {
            return "当前对战已经结束，不能继续落子";
        }
        if (!room.contains(userId)) {
            return "只有对局玩家可以落子";
        }
        if (!userId.equals(room.getCurrentTurnUserId())) {
            return "还没有轮到你落子";
        }
        if (!jinziRuleEngine.inBoard(row, col)) {
            return "落子位置不在棋盘内";
        }
        if (room.getBoard()[row][col] != 0) {
            return "该位置已经有棋子";
        }
        return null;
    }

    private String validateChat(JinziRoom room, Long userId, GobangChatRequest request) {
        if (room == null || !GameConstants.ROOM_PLAYING.equals(room.getRoomStatus())) {
            return "当前对战已经结束，不能发送消息或表情包";
        }
        if (!room.contains(userId)) {
            return "只有对局玩家可以发送房间消息";
        }
        String messageType = request == null || request.getMessageType() == null
                ? "TEXT"
                : request.getMessageType().trim().toUpperCase();
        String content = request == null || request.getContent() == null ? "" : request.getContent().trim();
        if (!"TEXT".equals(messageType) && !"EMOJI".equals(messageType)) {
            return "不支持的消息类型";
        }
        if (content.isBlank()) {
            return "消息内容不能为空";
        }
        if ("TEXT".equals(messageType) && content.length() > 200) {
            return "消息最多 200 个字";
        }
        return null;
    }

    private void finishRoom(JinziRoom room, Long winnerId, String endReason) {
        if (!GameConstants.ROOM_PLAYING.equals(room.getRoomStatus())) {
            return;
        }
        room.setRoomStatus(GameConstants.ROOM_FINISHED);
        room.setWinnerUserId(winnerId);
        room.setEndReason(endReason);
        Long loserId = winnerId == null ? null : room.opponentOf(winnerId);
        GameFinishedMqVO finishedEvent = transactionTemplate.execute(status -> {
            GameRankSettlementResult rankResult = gameRankService.settleRank(createRankCommand(room, winnerId, loserId, endReason));
            int scoreDelta = winnerDelta(rankResult);
            GameJinziMatchRecord record = new GameJinziMatchRecord();
            record.setRoomId(room.getRoomId());
            record.setBlackUserId(room.getBlackUserId());
            record.setWhiteUserId(room.getWhiteUserId());
            record.setWinnerUserId(winnerId);
            record.setLoserUserId(loserId);
            record.setEndReason(endReason);
            record.setScoreDelta(scoreDelta);
            record.setWinnerScoreDelta(scoreDelta);
            record.setLoserScoreDelta(loserDelta(rankResult));
            record.setStartedAt(room.getStartedAt());
            record.setEndedAt(new Date());
            record.setDeleteState((byte) 0);
            gameJinziMatchRecordMapper.insert(record);
            settlePoints(room, record, winnerId, loserId, scoreDelta, Math.abs(loserDelta(rankResult)));
            return createGameFinishedEvent(room, record, winnerId, loserId, endReason);
        });
        saveRoomSnapshot(room);
        publishGameFinishedEvent(finishedEvent);
        sendFinalStateToRoom(room);
        cleanupRoom(room);
    }

    private void settlePoints(
            JinziRoom room,
            GameJinziMatchRecord record,
            Long winnerId,
            Long loserId,
            int scoreDelta,
            int loserPenalty
    ) {
        if (winnerId == null || loserId == null || scoreDelta <= 0) {
            return;
        }
        if (!GameConstants.AI_USER_ID.equals(winnerId)) {
            pointsService.addPoints(winnerId, scoreDelta,
                    Constant.POINTS_SOURCE_GAME_WIN, record.getId(), "井字棋胜利奖励",
                    "game:jinzi:win:" + room.getRoomId());
        }
        if (!GameConstants.AI_USER_ID.equals(loserId)) {
            User loser = userMapper.selectByIdForUpdate(loserId);
            int loserPoints = loser == null || loser.getPoints() == null ? 0 : loser.getPoints();
            if (loserPenalty > 0 && loserPoints >= loserPenalty) {
                pointsService.deductPoints(loserId, loserPenalty,
                        Constant.POINTS_SOURCE_GAME_LOSE, record.getId(), "井字棋对局扣除",
                        "game:jinzi:lose:" + room.getRoomId());
            }
        }
    }

    private GameRankSettlementCommand createRankCommand(JinziRoom room, Long winnerId, Long loserId, String endReason) {
        GameRankSettlementCommand command = new GameRankSettlementCommand();
        command.setGameCode(GameConstants.JINZI);
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

    private JinziRoomStateVO toStateVO(JinziRoom room, Long userId) {
        long now = System.currentTimeMillis();
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
        Long opponentId = room.opponentOf(userId);
        GobangRoomParticipantVO opponentPlayer = null;
        if (opponentId != null) {
            opponentPlayer = opponentId.equals(room.getBlackUserId()) ? blackPlayer : whitePlayer;
        }
        return new JinziRoomStateVO(
                room.getRoomId(),
                userId,
                opponentId,
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
                room.isAiRoom(),
                room.isAiThinking(),
                room.getWinningLine(),
                blackPlayer,
                whitePlayer,
                opponentPlayer,
                gameConnectionRegistry.countRoomOnline(room.getRoomId())
        );
    }

    private Map<Long, User> loadRoomUsers(JinziRoom room) {
        List<Long> userIds = new ArrayList<>();
        addRealUserId(userIds, room.getBlackUserId());
        addRealUserId(userIds, room.getWhiteUserId());
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, User> userMap = new HashMap<>();
        userMapper.selectByIds(userIds).forEach(user -> userMap.put(user.getId(), user));
        return userMap;
    }

    private Map<Long, GameUserProfile> loadRoomProfiles(JinziRoom room) {
        List<Long> userIds = new ArrayList<>();
        addRealUserId(userIds, room.getBlackUserId());
        addRealUserId(userIds, room.getWhiteUserId());
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, GameUserProfile> profileMap = new HashMap<>();
        gameUserProfileMapper.selectList(new LambdaQueryWrapper<GameUserProfile>()
                .eq(GameUserProfile::getGameCode, GameConstants.JINZI)
                .eq(GameUserProfile::getDeleteState, (byte) 0)
                .in(GameUserProfile::getUserId, userIds))
                .forEach(profile -> profileMap.put(profile.getUserId(), profile));
        return profileMap;
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

    private void scheduleAiMove(JinziRoom room) {
        long minThinkMs = GameAiPlanner.jinziMinThinkMs();
        room.setAiThinking(true);
        sendStateToRoom(room, "room_state_updated");
        CompletableFuture.runAsync(() -> executeAiTurn(room.getRoomId(), minThinkMs));
    }

    private void executeAiTurn(String roomId, long minThinkMs) {
        long started = System.currentTimeMillis();
        try {
            JinziRoom room = rooms.get(roomId);
            if (room == null) {
                return;
            }
            int[] move;
            synchronized (room) {
                if (!GameConstants.ROOM_PLAYING.equals(room.getRoomStatus())
                        || !GameConstants.AI_USER_ID.equals(room.getCurrentTurnUserId())) {
                    return;
                }
                room.setAiModelName(GameAiPlanner.formatModelLabel(room.getAiModelCode(), false, false));
                move = jinziAiEngine.chooseMove(room.getBoard(), 2);
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
                handleMove(roomId, GameConstants.AI_USER_ID, move[0], move[1], null);
                if (room.isAiRoom()) {
                    sendStateToRoom(room, "room_state_updated");
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            JinziRoom room = rooms.get(roomId);
            if (room != null) {
                room.setAiThinking(false);
                sendStateToRoom(room, "room_state_updated");
            }
        }
    }

    private String chooseAiModelCode(Long userId) {
        GameUserProfile profile = gameUserProfileService.getOrCreateProfile(userId, GameConstants.JINZI);
        int score = profile == null || profile.getScore() == null ? 0 : profile.getScore();
        return score < AI_PRO_SCORE_THRESHOLD ? AI_MODEL_FLASH : AI_MODEL_PRO;
    }

    private boolean inBoard(int row, int col) {
        return row >= 0
                && row < GameConstants.JINZI_BOARD_SIZE
                && col >= 0
                && col < GameConstants.JINZI_BOARD_SIZE;
    }

    private void saveRoomSnapshot(JinziRoom room) {
        long now = System.currentTimeMillis();
        GameRoomSnapshotVO snapshot = new GameRoomSnapshotVO(
                GameConstants.JINZI,
                room.getRoomId(),
                room.getRoomStatus(),
                room.getBlackUserId(),
                room.getWhiteUserId(),
                room.getCurrentTurnUserId(),
                room.copyBoard(),
                countMoves(room),
                room.getWinnerUserId(),
                room.getEndReason(),
                toGobangPoints(room.getWinningLine()),
                room.remainingGameMs(room.getBlackUserId(), now),
                room.remainingGameMs(room.getWhiteUserId(), now),
                room.currentTurnRemainingMs(now),
                now
        );
        gameRoomSnapshotService.saveSnapshot(snapshot);
    }

    private GameFinishedMqVO createGameFinishedEvent(
            JinziRoom room,
            GameJinziMatchRecord record,
            Long winnerId,
            Long loserId,
            String endReason
    ) {
        String eventId = UUID.randomUUID().toString();
        GameSettlementEvent event = new GameSettlementEvent();
        event.setEventId(eventId);
        event.setGameCode(GameConstants.JINZI);
        event.setRoomId(room.getRoomId());
        event.setEventType(GameConstants.SETTLEMENT_EVENT_GAME_FINISHED);
        event.setRecordId(record.getId());
        event.setStatus(GameConstants.SETTLEMENT_EVENT_CREATED);
        event.setRetryCount(0);
        event.setDeleteState((byte) 0);
        gameSettlementEventMapper.insert(event);
        return new GameFinishedMqVO(
                eventId,
                GameConstants.JINZI,
                room.getRoomId(),
                record.getId(),
                winnerId,
                loserId,
                endReason,
                toGobangPoints(room.getWinningLine()),
                System.currentTimeMillis()
        );
    }

    private void publishGameFinishedEvent(GameFinishedMqVO event) {
        if (event == null) {
            return;
        }
        try {
            forumProducer.sendGameFinished(event);
            updateSettlementEventStatusFromCreated(event.getEventId(), GameConstants.SETTLEMENT_EVENT_MQ_SENT, null);
        } catch (Exception e) {
            log.error("投递井字棋结束 MQ 失败 roomId={}, eventId={}, error={}",
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

    private void updateSettlementEventStatusFromCreated(String eventId, String status, String lastError) {
        LambdaUpdateWrapper<GameSettlementEvent> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(GameSettlementEvent::getEventId, eventId)
                .eq(GameSettlementEvent::getStatus, GameConstants.SETTLEMENT_EVENT_CREATED)
                .set(GameSettlementEvent::getStatus, status)
                .set(GameSettlementEvent::getLastError, lastError);
        gameSettlementEventMapper.update(null, wrapper);
    }

    private void saveRoomPlayer(String roomId, Long userId, String role) {
        GameRoomPlayer row = new GameRoomPlayer();
        row.setGameCode(GameConstants.JINZI);
        row.setRoomId(roomId);
        row.setUserId(userId);
        row.setRoomRole(role);
        row.setDeleteState((byte) 0);
        try {
            gameRoomPlayerMapper.insert(row);
        } catch (Exception e) {
            log.debug("保存井字棋房间玩家映射失败 roomId={}, userId={}, role={}", roomId, userId, role);
        }
    }

    private void saveMove(JinziRoom room, Long userId, Integer row, Integer col, Integer chess, Long spentMs) {
        GameJinziRoomMove move = new GameJinziRoomMove();
        move.setRoomId(room.getRoomId());
        move.setMoveNo(countMoves(room) + 1);
        move.setUserId(userId);
        move.setRowIndex(row);
        move.setColIndex(col);
        move.setChess(chess);
        move.setSpentMs(spentMs);
        move.setDeleteState((byte) 0);
        gameJinziRoomMoveMapper.insert(move);
    }

    private int countMoves(JinziRoom room) {
        int count = 0;
        for (int row = 0; row < GameConstants.JINZI_BOARD_SIZE; row++) {
            for (int col = 0; col < GameConstants.JINZI_BOARD_SIZE; col++) {
                if (room.getBoard()[row][col] != 0) {
                    count++;
                }
            }
        }
        return count;
    }

    private JinziRoom requireRoom(String roomId, Long userId) {
        if (roomId == null || roomId.isBlank() || userId == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        JinziRoom room = rooms.get(roomId);
        if (room == null || !room.contains(userId)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_FORBIDDEN));
        }
        return room;
    }

    private void requireActionParams(String roomId, Long userId) {
        if (roomId == null || roomId.isBlank() || userId == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
    }

    private void cleanupRoom(JinziRoom room) {
        rooms.remove(room.getRoomId());
        userRoomIds.remove(room.getBlackUserId());
        if (!GameConstants.AI_USER_ID.equals(room.getWhiteUserId())) {
            userRoomIds.remove(room.getWhiteUserId());
        }
    }

    private List<GobangBoardPointVO> toGobangPoints(List<JinziBoardPointVO> points) {
        if (points == null || points.isEmpty()) {
            return List.of();
        }
        List<GobangBoardPointVO> rows = new ArrayList<>(points.size());
        for (JinziBoardPointVO point : points) {
            rows.add(new GobangBoardPointVO(point.getRow(), point.getCol()));
        }
        return rows;
    }

    private void addRealUserId(List<Long> userIds, Long userId) {
        if (userId != null && userId > 0 && !userIds.contains(userId)) {
            userIds.add(userId);
        }
    }

    private void sendRoomError(String roomId, Long userId, String requestId, String message) {
        try {
            gameConnectionRegistry.sendToRoom(
                    roomId,
                    userId,
                    objectMapper.writeValueAsString(GameWsResponse.fail("room_error", requestId, message))
            );
        } catch (Exception e) {
            log.debug("发送井字棋错误响应失败 roomId={}, userId={}, error={}", roomId, userId, e.getMessage());
        }
    }

    private void sendFinalStateToRoom(JinziRoom room) {
        sendStateToRoom(room, "game_finished");
    }

    private void sendStateToRoom(JinziRoom room, String type) {
        sendStateToRoom(room, type, null);
    }

    private void sendStateToRoom(JinziRoom room, String type, String requestId) {
        cacheRoomState(room);
        gameConnectionRegistry.forEachRoomSession(room.getRoomId(), (userId, session) -> {
            try {
                gameConnectionRegistry.send(
                        session,
                        objectMapper.writeValueAsString(GameWsResponse.ok(type, requestId, toStateVO(room, userId)))
                );
            } catch (Exception e) {
                log.debug("发送井字棋状态失败 roomId={}, userId={}, error={}",
                        room.getRoomId(),
                        userId,
                        e.getMessage());
            }
        });
        publishPlayerRoomState(room, type, requestId);
    }

    private void publishPlayerRoomState(JinziRoom room, String type, String requestId) {
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
            log.debug("发布井字棋房间状态事件失败 roomId={}, error={}", room.getRoomId(), e.getMessage());
        }
    }

    private void cacheRoomState(JinziRoom room) {
        if (room == null) {
            return;
        }
        gameRoomStateCacheService.saveState(
                GameConstants.JINZI,
                room.getRoomId(),
                room.getBlackUserId(),
                toStateVO(room, room.getBlackUserId())
        );
        if (!GameConstants.AI_USER_ID.equals(room.getWhiteUserId())) {
            gameRoomStateCacheService.saveState(
                    GameConstants.JINZI,
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
            log.debug("广播井字棋房间消息失败 roomId={}, error={}", roomId, e.getMessage());
        }
    }

    private String truncateError(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 512 ? message : message.substring(0, 512);
    }
}
