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
import org.pluchon.forum.entity.bo.game.GameRankSettlementCommand;
import org.pluchon.forum.entity.bo.game.GameRankSettlementResult;
import org.pluchon.forum.entity.db.GameJinziMatchRecord;
import org.pluchon.forum.entity.db.GameJinziRoomMove;
import org.pluchon.forum.entity.db.GameRoomPlayer;
import org.pluchon.forum.entity.db.GameSettlementEvent;
import org.pluchon.forum.entity.db.GameUserProfile;
import org.pluchon.forum.api.UserInternalVO;
import org.pluchon.forum.entity.dto.game.GobangChatRequest;
import org.pluchon.forum.entity.vo.game.GobangRoomParticipantVO;
import org.pluchon.forum.entity.vo.game.JinziChatVO;
import org.pluchon.forum.entity.vo.game.JinziMoveVO;
import org.pluchon.forum.entity.vo.game.JinziRoomStateVO;
import org.pluchon.forum.entity.vo.mq.GameFinishedMqVO;
import org.pluchon.forum.mapper.GameJinziMatchRecordMapper;
import org.pluchon.forum.mapper.GameJinziRoomMoveMapper;
import org.pluchon.forum.mapper.GameRoomPlayerMapper;
import org.pluchon.forum.mapper.GameSettlementEventMapper;
import org.pluchon.forum.mapper.GameUserProfileMapper;
import org.pluchon.forum.service.security.GameUserLookupService;
import org.pluchon.forum.service.interfaces.game.GameRoomEventBusService;
import org.pluchon.forum.service.interfaces.game.GameRoomStateCacheService;
import org.pluchon.forum.service.interfaces.game.GameUserProfileService;
import org.pluchon.forum.service.interfaces.game.GameRankService;
import org.pluchon.forum.service.interfaces.game.JinziRoomService;
import org.pluchon.forum.common.config.OssConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

// 井字棋房间服务，复用游戏结算链路但不开放观战角色
@Slf4j
@ConditionalOnProperty(name = "forum.features.game-runtime", havingValue = "true")
@Service
public class JinziRoomServiceImpl implements JinziRoomService {

    // roomId > 房间状态
    // 两条发言之间的最小间隔，挡住刷屏
    private static final long CHAT_INTERVAL_MS = 1_000L;

    private final ConcurrentHashMap<String, JinziRoom> rooms = new ConcurrentHashMap<>();

    // 房间号必须对活跃房间查重：撞号会让后建的房间把先建的从 rooms 里挤掉
    private String nextRoomId() {
        return GameRoomIdGenerator.generateRoomId(rooms::containsKey);
    }

    // userId > roomId，用于防止同一用户进入多个房间
    private final ConcurrentHashMap<Long, String> userRoomIds = new ConcurrentHashMap<>();

    private final ScheduledExecutorService roundScheduler = Executors.newSingleThreadScheduledExecutor();

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
    private GameJinziMatchRecordMapper gameJinziMatchRecordMapper;

    @Autowired
    private GameRoomPlayerMapper gameRoomPlayerMapper;

    @Autowired
    private GameJinziRoomMoveMapper gameJinziRoomMoveMapper;

    @Autowired
    private GameSettlementEventMapper gameSettlementEventMapper;

    @Autowired
    private GameUserLookupService gameUserLookupService;

    @Autowired
    private JinziRuleEngine jinziRuleEngine;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private ForumProducer forumProducer;

    @Autowired
    private GameMatchRoomHelper gameMatchRoomHelper;

    @Autowired
    private OssConfig ossConfig;

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
        String existingRoomA = userRoomIds.get(userIdA);
        if (existingRoomA != null && rooms.containsKey(existingRoomA)) {
            return existingRoomA;
        }
        String existingRoomB = userRoomIds.get(userIdB);
        if (existingRoomB != null && rooms.containsKey(existingRoomB)) {
            return existingRoomB;
        }
        JinziRoom room = new JinziRoom(nextRoomId(), userIdA, userIdB);
        room.setRoomStatus(GameConstants.ROOM_PLAYING);
        rooms.put(room.getRoomId(), room);
        userRoomIds.put(userIdA, room.getRoomId());
        userRoomIds.put(userIdB, room.getRoomId());
        gameUserProfileService.updateStatus(userIdA, GameConstants.JINZI, GameConstants.PROFILE_PLAYING, room.getRoomId());
        gameUserProfileService.updateStatus(userIdB, GameConstants.JINZI, GameConstants.PROFILE_PLAYING, room.getRoomId());
        saveRoomPlayer(room.getRoomId(), userIdA, "BLACK");
        saveRoomPlayer(room.getRoomId(), userIdB, "WHITE");
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

            if (win) {
                room.recordRoundWin(chess, userId, GameConstants.END_LINE, jinziRuleEngine.winningLine(room.getBoard(), chess));
            } else if (draw) {
                room.recordRoundDraw(GameConstants.END_DRAW);
            }

            boolean matchOver = room.isMatchOver();
            Long matchWinnerUserId = matchOver ? room.getMatchWinnerId() : null;
            String matchEndReason = matchOver ? (win ? GameConstants.END_LINE : (draw ? GameConstants.END_DRAW : room.getRoundEndReason())) : null;

            Long nextTurnUserId = (win || draw || matchOver) ? null : room.opponentOf(userId);
            room.setCurrentTurnUserId(nextTurnUserId);
            room.setTurnStartedAtMs(System.currentTimeMillis());
            cacheRoomState(room);

            JinziMoveVO moveVO = new JinziMoveVO(
                    userId,
                    row,
                    col,
                    chess,
                    nextTurnUserId,
                    (win || draw),
                    win ? userId : null,
                    win ? GameConstants.END_LINE : (draw ? GameConstants.END_DRAW : null),
                    win ? room.getWinningLine() : null,
                    room.getBlackWins(),
                    room.getWhiteWins(),
                    room.getDrawRounds(),
                    room.getCurrentRound(),
                    matchOver,
                    matchWinnerUserId,
                    matchEndReason
            );
            broadcast(roomId, GameWsResponse.ok("move_accepted", requestId, moveVO));

            if (matchOver) {
                finishRoom(room, matchWinnerUserId, matchEndReason != null ? matchEndReason : GameConstants.END_LINE);
            } else if (win || draw) {
                scheduleNextRound(room);
            }
        }
    }

    private void scheduleNextRound(JinziRoom room) {
        roundScheduler.schedule(() -> {
            try {
                synchronized (room) {
                    if (GameConstants.ROOM_PLAYING.equals(room.getRoomStatus()) && room.isRoundFinished()) {
                        room.startNextRound();
                        cacheRoomState(room);
                        sendStateToRoom(room, "round_started");
                    }
                }
            } catch (Exception e) {
                log.error("井字棋开启下一小局失败 roomId={}, error={}", room.getRoomId(), e.getMessage());
            }
        }, 5000, TimeUnit.MILLISECONDS);
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
        if (!room.tryChat(userId, System.currentTimeMillis(), CHAT_INTERVAL_MS)) {
            sendRoomError(roomId, userId, requestId, "发言太快了，慢一点");
            return;
        }
        String messageType = request == null || request.getMessageType() == null
                ? "TEXT"
                : request.getMessageType().trim().toUpperCase();
        String content = request == null || request.getContent() == null ? "" : request.getContent().trim();
        if ("EMOJI".equals(messageType)) {
            // 表情地址会被前端当成 <img src> 渲染给房里所有人，
            // 不限制来源等于让任何人往别人页面里塞任意外链
            String emojiUrl = request == null || request.getEmojiUrl() == null || request.getEmojiUrl().isBlank()
                    ? content
                    : request.getEmojiUrl().trim();
            if (!isTrustedEmojiUrl(emojiUrl)) {
                sendRoomError(roomId, userId, requestId, "表情来源不合法");
                return;
            }
            content = emojiUrl;
        }
        JinziChatVO vo = new JinziChatVO(
                userId,
                messageType,
                content,
                request == null ? null : request.getEmojiId(),
                "EMOJI".equals(messageType) ? content : null
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
            // 只广播「谁掉线了」。以前发的是整份按某一个人视角生成的状态，
            // 房里所有人收到同一份，视角本身就是错的
            broadcast(roomId, GameWsResponse.ok("peer_disconnected", null, Map.of("userId", userId)));
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
                List<Long> expired = new ArrayList<>();
                for (Map.Entry<Long, Long> entry : room.getDisconnectDeadlines().entrySet()) {
                    if (entry.getValue() <= now) {
                        expired.add(entry.getKey());
                    }
                }
                if (expired.isEmpty()) {
                    continue;
                }
                if (expired.size() >= 2) {
                    // 两个人都掉线超时，谁也不该赢。以前是取遍历到的第一个判负，
                    // 等于让哈希表的迭代顺序决定谁输
                    finishRoom(room, null, GameConstants.END_DRAW);
                    continue;
                }
                finishRoom(room, room.opponentOf(expired.get(0)), GameConstants.END_DISCONNECT);
            }
        }
    }

    @Scheduled(fixedDelay = 1_000)
    public void settleTimeoutRooms() {
        long now = System.currentTimeMillis();
        for (JinziRoom room : rooms.values()) {
            synchronized (room) {
                if (!GameConstants.ROOM_PLAYING.equals(room.getRoomStatus()) || room.isRoundFinished() || room.getCurrentTurnUserId() == null) {
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
        if (room.isRoundFinished()) {
            return "本小局已结束，即将开始下一局";
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
            record.setDeleteState(GameConstants.NOT_DELETED);
            gameJinziMatchRecordMapper.insert(record);
            return createGameFinishedEvent(room, record, winnerId, loserId, endReason);
        });
        publishGameFinishedEvent(finishedEvent);
        sendFinalStateToRoom(room);
        cleanupRoom(room);
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
        Map<Long, UserInternalVO> userMap = loadRoomUsers(room);
        Map<Long, GameUserProfile> profileMap = loadRoomProfiles(room);
        GobangRoomParticipantVO blackPlayer = toParticipant(
                room.getBlackUserId(),
                "BLACK",
                room.getStartedAt().getTime(),
                userMap,
                profileMap
        );
        GobangRoomParticipantVO whitePlayer = toParticipant(
                room.getWhiteUserId(),
                "WHITE",
                room.getStartedAt().getTime(),
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
                room.getBlackWins(),
                room.getWhiteWins(),
                room.getDrawRounds(),
                room.getCurrentRound(),
                room.getRoundStartingChess(),
                room.isRoundFinished(),
                room.getRoundWinnerUserId(),
                room.getRoundEndReason(),
                room.remainingGameMs(room.getBlackUserId(), now),
                room.remainingGameMs(room.getWhiteUserId(), now),
                room.currentTurnRemainingMs(now),
                room.getWinningLine(),
                blackPlayer,
                whitePlayer,
                opponentPlayer,
                gameConnectionRegistry.countRoomOnline(room.getRoomId())
        );
    }

    private Map<Long, UserInternalVO> loadRoomUsers(JinziRoom room) {
        List<Long> userIds = new ArrayList<>();
        addRealUserId(userIds, room.getBlackUserId());
        addRealUserId(userIds, room.getWhiteUserId());
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, UserInternalVO> userMap = new HashMap<>();
        gameUserLookupService.listByIds(userIds).forEach(user -> userMap.put(user.getId(), user));
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
                .eq(GameUserProfile::getDeleteState, GameConstants.NOT_DELETED)
                .in(GameUserProfile::getUserId, userIds))
                .forEach(profile -> profileMap.put(profile.getUserId(), profile));
        return profileMap;
    }

    private GobangRoomParticipantVO toParticipant(
            Long userId,
            String role,
            Long joinedAtMs,
            Map<Long, UserInternalVO> userMap,
            Map<Long, GameUserProfile> profileMap
    ) {
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
        event.setDeleteState(GameConstants.NOT_DELETED);
        gameSettlementEventMapper.insert(event);
        return new GameFinishedMqVO(
                eventId,
                GameConstants.JINZI,
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
        row.setDeleteState(GameConstants.NOT_DELETED);
        try {
            gameRoomPlayerMapper.insert(row);
        } catch (Exception e) {
            log.warn("保存井字棋房间玩家映射失败 roomId={}, userId={}, role={}, error={}", roomId, userId, role, e.getMessage());
        }
    }

    private void saveMove(JinziRoom room, Long userId, Integer row, Integer col, Integer chess, Long spentMs) {
        GameJinziRoomMove move = new GameJinziRoomMove();
        move.setRoomId(room.getRoomId());
        move.setMoveNo(room.nextMoveNo());
        move.setUserId(userId);
        move.setRowIndex(row);
        move.setColIndex(col);
        move.setChess(chess);
        move.setSpentMs(spentMs);
        move.setDeleteState(GameConstants.NOT_DELETED);
        gameJinziRoomMoveMapper.insert(move);
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

    // 表情只接受站内地址：相对路径，或配置里那个 OSS 前缀
    private boolean isTrustedEmojiUrl(String url) {
        if (url == null || url.isBlank() || url.length() > 512) {
            return false;
        }
        String trimmed = url.trim();
        if (trimmed.startsWith("/")) {
            return !trimmed.startsWith("//");
        }
        String prefix = ossConfig.getUrlPrefix() == null ? "" : ossConfig.getUrlPrefix().trim();
        return !prefix.isEmpty() && trimmed.startsWith(prefix);
    }

    private void requireActionParams(String roomId, Long userId) {
        if (roomId == null || roomId.isBlank() || userId == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
    }

    // 同时清掉 Redis 里的房间状态缓存与匹配建房记录，否则拿旧房号还能「进」一个散了的房间
    private void cleanupRoom(JinziRoom room) {
        rooms.remove(room.getRoomId());
        userRoomIds.remove(room.getBlackUserId());
        if (!GameConstants.AI_USER_ID.equals(room.getWhiteUserId())) {
            userRoomIds.remove(room.getWhiteUserId());
        }
        gameRoomStateCacheService.clearState(
                GameConstants.JINZI,
                room.getRoomId(),
                room.getBlackUserId(),
                room.getWhiteUserId());
        if (!GameConstants.AI_USER_ID.equals(room.getWhiteUserId())) {
            gameMatchRoomHelper.releaseMatchedRoom("jinzi", room.getBlackUserId(), room.getWhiteUserId());
        }
        // 排位结算那条链路有幂等短路，短路时 profile 还挂着旧房号，
        // 首页按钮就会一直显示「继续对局」并把人送回散了的房间
        releasePlayerStatus(room.getBlackUserId());
        releasePlayerStatus(room.getWhiteUserId());
    }

    private void releasePlayerStatus(Long userId) {
        if (userId == null || GameConstants.AI_USER_ID.equals(userId)) {
            return;
        }
        try {
            gameUserProfileService.updateStatus(userId, GameConstants.JINZI, GameConstants.PROFILE_IDLE, null);
        } catch (Exception e) {
            log.warn("重置玩家状态失败 gameCode={}, userId={}", GameConstants.JINZI, userId, e);
        }
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
