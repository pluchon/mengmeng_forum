package org.pluchon.forum.service.impl.game;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.websocket.game.GameConnectionRegistry;
import org.pluchon.forum.common.websocket.game.GameWsResponse;
import org.pluchon.forum.entity.bo.game.GameRankSettlementCommand;
import org.pluchon.forum.entity.bo.game.GameRankSettlementResult;
import org.pluchon.forum.entity.db.GameTetrisPkMatchRecord;
import org.pluchon.forum.entity.db.GameUserProfile;
import org.pluchon.forum.api.auth.UserInternalVO;
import org.pluchon.forum.cloud.feign.GamePointsInternalFeignClient;
import org.pluchon.forum.entity.dto.game.TetrisChatRequest;
import org.pluchon.forum.entity.vo.game.GobangRoomParticipantVO;
import org.pluchon.forum.entity.vo.game.TetrisActiveRoomVO;
import org.pluchon.forum.entity.vo.game.TetrisBoardViewVO;
import org.pluchon.forum.entity.vo.game.TetrisChatVO;
import org.pluchon.forum.entity.vo.game.TetrisCurPieceVO;
import org.pluchon.forum.entity.vo.game.TetrisRoomStateVO;
import org.pluchon.forum.mapper.GameTetrisPkMatchRecordMapper;
import org.pluchon.forum.mapper.GameUserProfileMapper;
import org.pluchon.forum.service.security.GameUserLookupService;
import org.pluchon.forum.service.impl.game.tetris.TetrisBlock;
import org.pluchon.forum.service.impl.game.tetris.TetrisEngineConstants;
import org.pluchon.forum.service.impl.game.tetris.TetrisMatrixUtil;
import org.pluchon.forum.service.impl.game.tetris.TetrisPlayerState;
import org.pluchon.forum.service.interfaces.game.GameRankService;
import org.pluchon.forum.service.interfaces.game.GameRoomEventBusService;
import org.pluchon.forum.service.interfaces.game.GameRoomStateCacheService;
import org.pluchon.forum.service.interfaces.game.GameUserProfileService;
import org.pluchon.forum.service.interfaces.game.TetrisRoomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// 俄罗斯方块 PK 房间服务，服务端权威推进双方棋盘
@Slf4j
@ConditionalOnProperty(name = "forum.features.game-runtime", havingValue = "true")
@Service
public class TetrisRoomServiceImpl implements TetrisRoomService {

    private static final long RECONNECT_WINDOW_MS = GameConstants.TETRIS_RECONNECT_WINDOW_MS;

    private static final int PK_SCORE_DELTA = 3;

    private final ConcurrentHashMap<String, TetrisRoom> rooms = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<Long, String> userRoomIds = new ConcurrentHashMap<>();

    private final Random garbageRandom = new Random();

    @Autowired
    private GameConnectionRegistry gameConnectionRegistry;

    @Autowired
    private GameUserProfileService gameUserProfileService;

    @Autowired
    private GameRoomEventBusService gameRoomEventBusService;

    @Autowired
    private GameRoomStateCacheService gameRoomStateCacheService;

    @Autowired
    private GameRankService gameRankService;

    @Autowired
    private GameUserProfileMapper gameUserProfileMapper;

    @Autowired
    private GameTetrisPkMatchRecordMapper gameTetrisPkMatchRecordMapper;

    @Autowired
    private GameUserLookupService gameUserLookupService;

    @Autowired
    private GamePointsInternalFeignClient gamePointsInternalFeignClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private GameMatchRoomHelper gameMatchRoomHelper;

    @Override
    public String createMatchedRoom(Long userIdA, Long userIdB) {
        if (userIdA == null || userIdB == null || userIdA.equals(userIdB)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        return gameMatchRoomHelper.resolveMatchedRoomId(
                "tetris", userIdA, userIdB,
                () -> createMatchedRoomInternal(userIdA, userIdB),
                rooms::containsKey);
    }

    private String createMatchedRoomInternal(Long userIdA, Long userIdB) {
        boolean swap = garbageRandom.nextBoolean();
        Long redUserId = swap ? userIdB : userIdA;
        Long blueUserId = swap ? userIdA : userIdB;
        TetrisRoom room = new TetrisRoom(userIdA, userIdB, redUserId, blueUserId);
        rooms.put(room.getRoomId(), room);
        userRoomIds.put(userIdA, room.getRoomId());
        userRoomIds.put(userIdB, room.getRoomId());
        gameUserProfileService.updateStatus(userIdA, GameConstants.TETRIS_PK, GameConstants.PROFILE_PLAYING, room.getRoomId());
        gameUserProfileService.updateStatus(userIdB, GameConstants.TETRIS_PK, GameConstants.PROFILE_PLAYING, room.getRoomId());
        cacheRoomState(room);
        return room.getRoomId();
    }

    @Override
    public TetrisRoomStateVO joinRoom(String roomId, Long userId, WebSocketSession session) {
        TetrisRoom room = requireExistingRoom(roomId);
        boolean spectator = !room.contains(userId);
        gameConnectionRegistry.enterRoom(roomId, userId, session);
        if (!spectator) {
            room.getDisconnectDeadlines().remove(userId);
            broadcast(roomId, GameWsResponse.ok("peer_reconnected", null, toStateVO(room, userId)));
        } else {
            room.getSpectatorJoinedAt().putIfAbsent(userId, System.currentTimeMillis());
            broadcastState(room, "room_state_updated", null);
        }
        return toStateVO(room, userId);
    }

    @Override
    public TetrisRoomStateVO getRoomState(String roomId, Long userId) {
        TetrisRoom room = rooms.get(roomId);
        if (room == null) {
            TetrisRoomStateVO cached = gameRoomStateCacheService.getState(
                    GameConstants.TETRIS_PK,
                    roomId,
                    userId,
                    TetrisRoomStateVO.class
            );
            if (cached != null) {
                return cached;
            }
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
        return toStateVO(room, userId);
    }

    @Override
    public boolean hasLocalRoom(String roomId) {
        return roomId != null && rooms.containsKey(roomId);
    }

    @Override
    public void pushRoomState(String roomId, String requestId) {
        TetrisRoom room = rooms.get(roomId);
        if (room == null) {
            return;
        }
        broadcastState(room, "room_state_updated", requestId);
    }

    @Override
    public void handleInput(String roomId, Long userId, String action, String requestId) {
        requireRoomActionParams(roomId, userId, action);
        TetrisRoom room = rooms.get(roomId);
        if (room == null) {
            sendRoomError(roomId, userId, requestId, "房间不存在或已结束");
            return;
        }
        synchronized (room) {
            if (!room.contains(userId)) {
                sendRoomError(roomId, userId, requestId, "观众不能操作棋盘");
                return;
            }
            if (!GameConstants.ROOM_PLAYING.equals(room.getRoomStatus())) {
                sendRoomError(roomId, userId, requestId, "对局已结束");
                return;
            }
            TetrisPlayerState state = room.stateOf(userId);
            if (state == null || state.isGameOver()) {
                sendRoomError(roomId, userId, requestId, "本局已结束");
                return;
            }
            long now = System.currentTimeMillis();
            int lockGarbage = state.advanceLockIfReady(now);
            if (lockGarbage != TetrisPlayerState.TICK_UNCHANGED) {
                applyGarbageToOpponent(room, userId, lockGarbage, now, requestId);
                checkFinishAfterMove(room, userId);
            }
            if (!GameConstants.ROOM_PLAYING.equals(room.getRoomStatus())) {
                return;
            }
            if (state.isGameOver()) {
                sendRoomError(roomId, userId, requestId, "本局已结束");
                return;
            }
            int garbage = state.handleInput(action, now);
            applyGarbageToOpponent(room, userId, garbage, now, requestId);
            checkFinishAfterMove(room, userId);
            if (GameConstants.ROOM_PLAYING.equals(room.getRoomStatus())) {
                broadcastState(room, "room_state_updated", requestId);
            }
        }
    }

    @Override
    public void chat(String roomId, Long userId, TetrisChatRequest request, String requestId) {
        requireRoomActionParams(roomId, userId, "chat");
        TetrisRoom room = rooms.get(roomId);
        if (room == null) {
            sendRoomError(roomId, userId, requestId, "房间不存在或已结束");
            return;
        }
        if (!room.contains(userId)) {
            sendRoomError(roomId, userId, requestId, "观众不能发言");
            return;
        }
        String messageType = request == null || request.getMessageType() == null
                ? "TEXT"
                : request.getMessageType().trim().toUpperCase();
        String content = request == null ? "" : String.valueOf(request.getContent() == null ? "" : request.getContent()).trim();
        if ("TEXT".equals(messageType) && content.isEmpty()) {
            sendRoomError(roomId, userId, requestId, "消息不能为空");
            return;
        }
        TetrisChatVO vo = new TetrisChatVO(
                userId,
                messageType,
                content,
                request == null ? null : request.getEmojiId(),
                request == null ? null : request.getEmojiUrl(),
                System.currentTimeMillis()
        );
        synchronized (room) {
            room.getChatHistory().add(vo);
        }
        broadcast(roomId, GameWsResponse.ok("room_chat", requestId, vo));
    }

    @Override
    public void surrender(String roomId, Long userId, String requestId) {
        requireRoomActionParams(roomId, userId, "surrender");
        TetrisRoom room = rooms.get(roomId);
        if (room == null) {
            sendRoomError(roomId, userId, requestId, "房间不存在或已结束");
            return;
        }
        synchronized (room) {
            if (!room.contains(userId)) {
                sendRoomError(roomId, userId, requestId, "观众不能认输");
                return;
            }
            if (!GameConstants.ROOM_PLAYING.equals(room.getRoomStatus())) {
                sendRoomError(roomId, userId, requestId, "对局已结束");
                return;
            }
            finishRoom(room, room.opponentOf(userId), GameConstants.END_SURRENDER);
        }
    }

    @Override
    public void handleDisconnect(String roomId, Long userId, WebSocketSession session) {
        TetrisRoom room = rooms.get(roomId);
        if (room == null || userId == null) {
            gameConnectionRegistry.exitRoom(roomId, userId, session);
            return;
        }
        if (!room.contains(userId)) {
            gameConnectionRegistry.exitRoom(roomId, userId, session);
            room.getSpectatorJoinedAt().remove(userId);
            broadcastState(room, "room_state_updated", null);
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
    public List<TetrisActiveRoomVO> listActiveRooms() {
        List<TetrisActiveRoomVO> rows = new ArrayList<>();
        Set<Long> userIds = new HashSet<>();
        for (TetrisRoom room : rooms.values()) {
            if (!GameConstants.ROOM_PLAYING.equals(room.getRoomStatus())) {
                continue;
            }
            collectActiveUserId(userIds, room.getRedUserId());
            collectActiveUserId(userIds, room.getBlueUserId());
        }
        Map<Long, UserInternalVO> userMap = loadActiveRoomUsers(userIds);
        for (TetrisRoom room : rooms.values()) {
            if (!GameConstants.ROOM_PLAYING.equals(room.getRoomStatus())) {
                continue;
            }
            rows.add(new TetrisActiveRoomVO(
                    room.getRoomId(),
                    room.getPlayer1UserId(),
                    room.getPlayer2UserId(),
                    room.getRedUserId(),
                    activeRoomNickname(userMap.get(room.getRedUserId()), room.getRedUserId()),
                    room.getBlueUserId(),
                    activeRoomNickname(userMap.get(room.getBlueUserId()), room.getBlueUserId()),
                    room.scoreOf(room.getRedUserId()),
                    room.scoreOf(room.getBlueUserId()),
                    room.getStartedAt()
            ));
        }
        rows.sort(Comparator.comparing(TetrisActiveRoomVO::getStartedAt).reversed());
        return rows;
    }

    private void collectActiveUserId(Set<Long> userIds, Long userId) {
        if (userId != null) {
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

    // 定时推进双方重力
    @Scheduled(fixedDelay = 80)
    public void tickRooms() {
        long now = System.currentTimeMillis();
        for (TetrisRoom room : rooms.values()) {
            synchronized (room) {
                if (!GameConstants.ROOM_PLAYING.equals(room.getRoomStatus())) {
                    continue;
                }
                boolean changed = false;
                Long player1Id = room.getPlayer1UserId();
                Long player2Id = room.getPlayer2UserId();
                if (!room.getPlayer1State().isGameOver()) {
                    int garbage = room.getPlayer1State().tickFall(now);
                    if (garbage != TetrisPlayerState.TICK_UNCHANGED) {
                        applyGarbageToOpponent(room, player1Id, garbage, now, null);
                        checkFinishAfterMove(room, player1Id);
                        changed = true;
                    }
                }
                if (!room.getPlayer2State().isGameOver()) {
                    int garbage = room.getPlayer2State().tickFall(now);
                    if (garbage != TetrisPlayerState.TICK_UNCHANGED) {
                        applyGarbageToOpponent(room, player2Id, garbage, now, null);
                        checkFinishAfterMove(room, player2Id);
                        changed = true;
                    }
                }
                if (!changed) {
                    continue;
                }
                if (GameConstants.ROOM_PLAYING.equals(room.getRoomStatus())) {
                    broadcastState(room, "room_state_updated", null);
                }
            }
        }
    }

    // 消行后向对手追加垃圾行，与单人模式 lock → clear 链路一致
    private void applyGarbageToOpponent(
            TetrisRoom room,
            Long actorUserId,
            int garbage,
            long nowMs,
            String requestId
    ) {
        if (garbage <= 0) {
            return;
        }
        Long opponentId = room.opponentOf(actorUserId);
        TetrisPlayerState opponentState = room.stateOf(opponentId);
        if (opponentState == null || opponentState.isGameOver()) {
            return;
        }
        opponentState.addGarbageLines(garbage, garbageRandom, nowMs);
        broadcast(room.getRoomId(), GameWsResponse.ok("garbage_received", requestId, Map.of(
                "targetUserId", opponentId,
                "lines", garbage
        )));
    }

    @Scheduled(fixedDelay = 5_000)
    public void settleExpiredDisconnects() {
        long now = System.currentTimeMillis();
        for (TetrisRoom room : rooms.values()) {
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

    private void checkFinishAfterMove(TetrisRoom room, Long actorUserId) {
        TetrisPlayerState actorState = room.stateOf(actorUserId);
        if (actorState != null && actorState.isGameOver()) {
            finishRoom(room, room.opponentOf(actorUserId), GameConstants.END_LINE);
            return;
        }
        Long opponentId = room.opponentOf(actorUserId);
        TetrisPlayerState opponentState = room.stateOf(opponentId);
        if (opponentState != null && opponentState.isGameOver()) {
            finishRoom(room, actorUserId, GameConstants.END_LINE);
        }
    }

    private void finishRoom(TetrisRoom room, Long winnerId, String endReason) {
        if (!GameConstants.ROOM_PLAYING.equals(room.getRoomStatus())) {
            return;
        }
        room.setRoomStatus(GameConstants.ROOM_FINISHED);
        room.setWinnerUserId(winnerId);
        room.setEndReason(endReason);
        Long loserId = winnerId == null ? null : room.opponentOf(winnerId);
        transactionTemplate.execute(status -> {
            GameTetrisPkMatchRecord record = new GameTetrisPkMatchRecord();
            record.setRoomId(room.getRoomId());
            record.setPlayer1UserId(room.getPlayer1UserId());
            record.setPlayer2UserId(room.getPlayer2UserId());
            record.setRedUserId(room.getRedUserId());
            record.setBlueUserId(room.getBlueUserId());
            record.setWinnerUserId(winnerId);
            record.setLoserUserId(loserId);
            record.setPlayer1Score(room.scoreOf(room.getPlayer1UserId()));
            record.setPlayer2Score(room.scoreOf(room.getPlayer2UserId()));
            record.setEndReason(endReason);
            GameRankSettlementResult rankResult = gameRankService.settleRank(createRankCommand(room, winnerId, loserId, endReason));
            int scoreDelta = winnerDelta(rankResult);
            int loserPenalty = Math.abs(loserDelta(rankResult));
            record.setScoreDelta(scoreDelta);
            record.setWinnerScoreDelta(scoreDelta);
            record.setLoserScoreDelta(loserDelta(rankResult));
            record.setReplayPayload(buildReplayPayload(room));
            record.setStartedAt(room.getStartedAt());
            record.setEndedAt(new Date());
            record.setDeleteState((byte) 0);
            gameTetrisPkMatchRecordMapper.insert(record);
            if (winnerId != null && loserId != null && scoreDelta > 0) {
                gamePointsInternalFeignClient.addPoints(winnerId, scoreDelta,
                        Constant.POINTS_SOURCE_GAME_WIN, record.getId(), "俄罗斯方块PK胜利奖励",
                        "game:tetrispk:win:" + room.getRoomId());
                Integer balance = gamePointsInternalFeignClient.getBalance(loserId);
                int loserPoints = balance == null ? 0 : balance;
                if (loserPenalty > 0 && loserPoints >= loserPenalty) {
                    gamePointsInternalFeignClient.deductPoints(loserId, loserPenalty,
                            Constant.POINTS_SOURCE_GAME_LOSE, record.getId(), "俄罗斯方块PK对局扣除",
                            "game:tetrispk:lose:" + room.getRoomId());
                }
            }
            return null;
        });
        broadcastState(room, "game_finished", null);
        cleanupRoom(room);
    }

    private String buildReplayPayload(TetrisRoom room) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("player1Seed", room.getPlayer1State().getSeed());
            payload.put("player2Seed", room.getPlayer2State().getSeed());
            payload.put("player1Score", room.scoreOf(room.getPlayer1UserId()));
            payload.put("player2Score", room.scoreOf(room.getPlayer2UserId()));
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return "{}";
        }
    }

    private GameRankSettlementCommand createRankCommand(TetrisRoom room, Long winnerId, Long loserId, String endReason) {
        GameRankSettlementCommand command = new GameRankSettlementCommand();
        command.setGameCode(GameConstants.TETRIS_PK);
        command.setRoomId(room.getRoomId());
        command.setPlayerAUserId(room.getPlayer1UserId());
        command.setPlayerBUserId(room.getPlayer2UserId());
        command.setWinnerUserId(winnerId);
        command.setLoserUserId(loserId);
        command.setEndReason(endReason);
        command.setEffectiveForRank(Math.max(
                room.scoreOf(room.getPlayer1UserId()),
                room.scoreOf(room.getPlayer2UserId())) >= 300);
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

    private void cleanupRoom(TetrisRoom room) {
        userRoomIds.remove(room.getPlayer1UserId());
        userRoomIds.remove(room.getPlayer2UserId());
        rooms.remove(room.getRoomId());
    }

    private TetrisRoom requireExistingRoom(String roomId) {
        TetrisRoom room = rooms.get(roomId);
        if (room == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "房间不存在或已结束"));
        }
        return room;
    }

    private void requireRoomActionParams(String roomId, Long userId, String action) {
        if (roomId == null || roomId.isBlank() || userId == null || action == null || action.isBlank()) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
    }

    private TetrisRoomStateVO toStateVO(TetrisRoom room, Long userId) {
        boolean spectator = userId == null || !room.contains(userId);
        Map<Long, UserInternalVO> userMap = loadRoomUsers(room);
        Map<Long, GameUserProfile> profileMap = loadRoomProfiles(room);
        GobangRoomParticipantVO player1 = toParticipant(room.getPlayer1UserId(), "PLAYER1", room.getStartedAt().getTime(), userMap, profileMap);
        GobangRoomParticipantVO player2 = toParticipant(room.getPlayer2UserId(), "PLAYER2", room.getStartedAt().getTime(), userMap, profileMap);
        Long opponentId = spectator ? null : room.opponentOf(userId);
        GobangRoomParticipantVO opponentPlayer = null;
        if (opponentId != null) {
            opponentPlayer = opponentId.equals(room.getPlayer1UserId()) ? player1 : player2;
        }
        int redScore = room.scoreOf(room.getRedUserId());
        int blueScore = room.scoreOf(room.getBlueUserId());
        int leftPercent = calcPkBarPercent(redScore, blueScore);
        TetrisBoardViewVO myBoard = null;
        TetrisBoardViewVO opponentBoard = null;
        if (!spectator) {
            myBoard = toBoardView(room.stateOf(userId), true);
            opponentBoard = toBoardView(room.stateOf(opponentId), false);
        } else {
            myBoard = toBoardView(room.stateOf(room.getPlayer1UserId()), false);
            opponentBoard = toBoardView(room.stateOf(room.getPlayer2UserId()), false);
        }
        List<GobangRoomParticipantVO> spectators = buildSpectators(room, userMap, profileMap);
        return new TetrisRoomStateVO(
                room.getRoomId(),
                userId,
                opponentId,
                room.getPlayer1UserId(),
                room.getPlayer2UserId(),
                room.getRedUserId(),
                room.getBlueUserId(),
                room.getRoomStatus(),
                room.getWinnerUserId(),
                room.getEndReason(),
                spectator,
                myBoard,
                opponentBoard,
                redScore,
                blueScore,
                leftPercent,
                opponentPlayer,
                player1,
                player2,
                spectators,
                spectators.size(),
                gameConnectionRegistry.countRoomOnline(room.getRoomId()),
                System.currentTimeMillis(),
                new ArrayList<>(room.getChatHistory())
        );
    }

    private int calcPkBarPercent(int redScore, int blueScore) {
        int total = Math.max(1, redScore + blueScore);
        int percent = (int) Math.round(redScore * 100.0 / total);
        return Math.max(5, Math.min(95, percent));
    }

    private TetrisBoardViewVO toBoardView(TetrisPlayerState state, boolean revealHoldNext) {
        if (state == null) {
            return new TetrisBoardViewVO(
                    TetrisEngineConstants.createBlankMatrix(),
                    null,
                    null,
                    null,
                    null,
                    0,
                    0,
                    true,
                    revealHoldNext
            );
        }
        TetrisBlock cur = state.getCur();
        TetrisCurPieceVO curVo = cur == null ? null : toPieceVO(cur);
        TetrisCurPieceVO ghostVo = null;
        if (cur != null) {
            TetrisBlock ghost = TetrisMatrixUtil.ghostDrop(cur, state.getMatrix());
            ghostVo = toPieceVO(ghost);
        }
        return new TetrisBoardViewVO(
                state.getMatrix(),
                curVo,
                ghostVo,
                revealHoldNext ? state.getNextType() : null,
                revealHoldNext ? state.getHoldType() : null,
                state.getPoints(),
                state.getClearLines(),
                state.isGameOver(),
                revealHoldNext
        );
    }

    private TetrisCurPieceVO toPieceVO(TetrisBlock block) {
        return new TetrisCurPieceVO(block.getType(), block.getXy(), block.getShape());
    }

    private Map<Long, GameUserProfile> loadRoomProfiles(TetrisRoom room) {
        List<Long> userIds = new ArrayList<>();
        addRealUserId(userIds, room.getPlayer1UserId());
        addRealUserId(userIds, room.getPlayer2UserId());
        gameConnectionRegistry.roomUserIds(room.getRoomId()).forEach(id -> addRealUserId(userIds, id));
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, GameUserProfile> profileMap = new HashMap<>();
        gameUserProfileMapper.selectList(new LambdaQueryWrapper<GameUserProfile>()
                .eq(GameUserProfile::getGameCode, GameConstants.TETRIS_PK)
                .eq(GameUserProfile::getDeleteState, (byte) 0)
                .in(GameUserProfile::getUserId, userIds))
                .forEach(profile -> profileMap.put(profile.getUserId(), profile));
        return profileMap;
    }

    private Map<Long, UserInternalVO> loadRoomUsers(TetrisRoom room) {
        List<Long> userIds = new ArrayList<>();
        addRealUserId(userIds, room.getPlayer1UserId());
        addRealUserId(userIds, room.getPlayer2UserId());
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
            TetrisRoom room,
            Map<Long, UserInternalVO> userMap,
            Map<Long, GameUserProfile> profileMap
    ) {
        Set<Long> onlineIds = gameConnectionRegistry.roomUserIds(room.getRoomId());
        List<GobangRoomParticipantVO> spectators = new ArrayList<>();
        onlineIds.forEach(id -> {
            if (id != null && !room.contains(id)) {
                spectators.add(toParticipant(
                        id,
                        "SPECTATOR",
                        room.getSpectatorJoinedAt().getOrDefault(id, System.currentTimeMillis()),
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
            Map<Long, UserInternalVO> userMap,
            Map<Long, GameUserProfile> profileMap
    ) {
        UserInternalVO user = userMap.get(userId);
        GameUserProfile profile = profileMap.get(userId);
        int totalCount = profile == null || profile.getTotalCount() == null ? 0 : profile.getTotalCount();
        int winCount = profile == null || profile.getWinCount() == null ? 0 : profile.getWinCount();
        int winRate = totalCount <= 0 ? 0 : (int) Math.round(winCount * 100.0 / totalCount);
        return new GobangRoomParticipantVO(
                userId,
                user == null ? null : user.getUsername(),
                activeRoomNickname(user, userId),
                user == null ? null : user.getAvatarUrl(),
                (byte) 0,
                false,
                role,
                joinedAtMs,
                false,
                null,
                totalCount,
                winRate
        );
    }

    private void broadcastState(TetrisRoom room, String type, String requestId) {
        cacheRoomState(room);
        gameConnectionRegistry.forEachRoomSession(room.getRoomId(), (uid, session) -> {
            try {
                String payload = objectMapper.writeValueAsString(GameWsResponse.ok(type, requestId, toStateVO(room, uid)));
                gameConnectionRegistry.send(session, payload);
            } catch (Exception e) {
                log.debug("广播俄罗斯方块房间状态失败 roomId={}, userId={}", room.getRoomId(), uid);
            }
        });
        publishPlayerRoomState(room, type, requestId);
    }

    private void broadcast(String roomId, GameWsResponse<?> response) {
        try {
            String payload = objectMapper.writeValueAsString(response);
            gameConnectionRegistry.broadcastRoom(roomId, payload);
            gameRoomEventBusService.publishRoomEvent(roomId, payload);
        } catch (Exception e) {
            log.debug("广播俄罗斯方块房间消息失败 roomId={}", roomId);
        }
    }

    private void cacheRoomState(TetrisRoom room) {
        if (room == null) {
            return;
        }
        gameRoomStateCacheService.saveState(
                GameConstants.TETRIS_PK,
                room.getRoomId(),
                room.getPlayer1UserId(),
                toStateVO(room, room.getPlayer1UserId())
        );
        gameRoomStateCacheService.saveState(
                GameConstants.TETRIS_PK,
                room.getRoomId(),
                room.getPlayer2UserId(),
                toStateVO(room, room.getPlayer2UserId())
        );
    }

    private void publishPlayerRoomState(TetrisRoom room, String type, String requestId) {
        try {
            String player1Payload = objectMapper.writeValueAsString(GameWsResponse.ok(
                    type,
                    requestId,
                    toStateVO(room, room.getPlayer1UserId())
            ));
            gameRoomEventBusService.publishRoomUserEvent(room.getRoomId(), room.getPlayer1UserId(), player1Payload);
            String player2Payload = objectMapper.writeValueAsString(GameWsResponse.ok(
                    type,
                    requestId,
                    toStateVO(room, room.getPlayer2UserId())
            ));
            gameRoomEventBusService.publishRoomUserEvent(room.getRoomId(), room.getPlayer2UserId(), player2Payload);
        } catch (Exception e) {
            log.debug("发布俄罗斯方块定向状态失败 roomId={}", room.getRoomId());
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
            log.debug("发送俄罗斯方块房间错误失败 roomId={}, userId={}", roomId, userId);
        }
    }
}
