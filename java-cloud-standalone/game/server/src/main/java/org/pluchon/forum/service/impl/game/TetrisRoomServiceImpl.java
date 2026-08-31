package org.pluchon.forum.service.impl.game;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.websocket.game.GameConnectionRegistry;
import org.pluchon.forum.common.websocket.game.GameWsResponse;
import org.pluchon.forum.entity.bo.game.GameRankSettlementCommand;
import org.pluchon.forum.entity.bo.game.GameRankSettlementResult;
import org.pluchon.forum.entity.db.GameTetrisPkMatchRecord;
import org.pluchon.forum.entity.db.GameUserProfile;
import org.pluchon.forum.api.UserInternalVO;
import org.pluchon.forum.entity.dto.game.TetrisChatRequest;
import org.pluchon.forum.entity.vo.game.GobangRoomParticipantVO;
import org.pluchon.forum.entity.vo.common.PageResult;
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
import org.pluchon.forum.common.config.OssConfig;
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

    // 单条发言的字数上限，与前端输入框的 maxlength 对齐
    private static final int MAX_CHAT_LENGTH = 200;

    // 两条发言之间的最小间隔，挡住刷屏
    private static final long CHAT_INTERVAL_MS = 1_000L;

    private final ConcurrentHashMap<String, TetrisRoom> rooms = new ConcurrentHashMap<>();

    // 房间号必须对活跃房间查重：撞号会让后建的房间把先建的从 rooms 里挤掉
    @Autowired
    private GameLobbyBroadcaster gameLobbyBroadcaster;

    private String nextRoomId() {
        return GameRoomIdGenerator.generateRoomId(rooms::containsKey);
    }

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
    private ObjectMapper objectMapper;

    @Autowired
    private TransactionTemplate transactionTemplate;

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
                "tetris", userIdA, userIdB,
                () -> createMatchedRoomInternal(userIdA, userIdB),
                rooms::containsKey);
    }

    private String createMatchedRoomInternal(Long userIdA, Long userIdB) {
        boolean swap = garbageRandom.nextBoolean();
        Long redUserId = swap ? userIdB : userIdA;
        Long blueUserId = swap ? userIdA : userIdB;
        TetrisRoom room = new TetrisRoom(nextRoomId(), userIdA, userIdB, redUserId, blueUserId);
        rooms.put(room.getRoomId(), room);
        gameLobbyBroadcaster.roomsChanged(GameConstants.TETRIS_PK);
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
            // 只广播「谁重连了」。以前这里发的是整份带棋盘的状态，而且是某一个人的视角——
            // 房里所有人都会收到同一份，既把对手的 hold/next 泄露出去，视角也是错的
            broadcast(roomId, GameWsResponse.ok("peer_reconnected", null, Map.of("userId", userId)));
        } else {
            room.getSpectatorJoinedAt().putIfAbsent(userId, System.currentTimeMillis());
            broadcastState(room, "room_state_updated", null);
        }
        return toStateVO(room, userId, true);
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
            // 缓存只为多实例部署兜底：房间在别的实例上还活着。
            // 已经结束的对局不能从这里放行，否则拿着旧房号还能「进」一个散了的房间。
            if (cached != null && !GameConstants.ROOM_FINISHED.equals(cached.getRoomStatus())) {
                return cached;
            }
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
        return toStateVO(room, userId, true);
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
            if (now >= room.getDeadlineMs()) {
                finishRoom(room, resolveRaceWinner(room), GameConstants.END_RACE);
                return;
            }
            if (state.advanceLockIfReady(now)) {
                checkFinishAfterMove(room, userId);
            }
            if (!GameConstants.ROOM_PLAYING.equals(room.getRoomStatus())) {
                return;
            }
            if (state.isGameOver()) {
                sendRoomError(roomId, userId, requestId, "本局已结束");
                return;
            }
            state.handleInput(action, now);
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
        if (!room.tryChat(userId, System.currentTimeMillis(), CHAT_INTERVAL_MS)) {
            sendRoomError(roomId, userId, requestId, "发言太快了，慢一点");
            return;
        }
        String messageType = request == null || request.getMessageType() == null
                ? "TEXT"
                : request.getMessageType().trim().toUpperCase();
        String content = request == null ? "" : String.valueOf(request.getContent() == null ? "" : request.getContent()).trim();
        if ("EMOJI".equals(messageType)) {
            // 表情的 content 会被前端当成 <img src> 渲染给房里所有人，
            // 不限制来源等于让任何人往别人页面里塞任意外链
            String emojiUrl = request == null || request.getEmojiUrl() == null || request.getEmojiUrl().isBlank()
                    ? content
                    : request.getEmojiUrl().trim();
            if (!isTrustedEmojiUrl(emojiUrl)) {
                sendRoomError(roomId, userId, requestId, "表情来源不合法");
                return;
            }
            content = emojiUrl;
        } else {
            messageType = "TEXT";
            if (content.isEmpty()) {
                sendRoomError(roomId, userId, requestId, "消息不能为空");
                return;
            }
            // 前端的 maxlength 只是提示，服务端不拦就等于没有上限
            if (content.length() > MAX_CHAT_LENGTH) {
                sendRoomError(roomId, userId, requestId, "消息太长了，最多 " + MAX_CHAT_LENGTH + " 个字");
                return;
            }
        }
        TetrisChatVO vo = new TetrisChatVO(
                userId,
                messageType,
                content,
                request == null ? null : request.getEmojiId(),
                "EMOJI".equals(messageType) ? content : null
        );
        synchronized (room) {
            room.appendChat(vo);
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
            broadcast(roomId, GameWsResponse.ok("peer_disconnected", null, Map.of("userId", userId)));
        }
    }

    @Override
    public PageResult<TetrisActiveRoomVO> pageActiveRooms(String roomId, Integer pageNum, Integer pageSize) {
        String wanted = roomId == null ? "" : roomId.trim();
        if (!wanted.isEmpty() && !GameRoomIdGenerator.isValidRoomId(wanted)) {
            // 房间号固定 6 位数字，非法输入直接返回空而不是拿去查
            return GameActiveRoomPaging.emptyPage(pageNum, pageSize);
        }
        List<TetrisRoom> matched = new ArrayList<>();
        for (TetrisRoom room : rooms.values()) {
            if (!GameConstants.ROOM_PLAYING.equals(room.getRoomStatus())) {
                continue;
            }
            if (!wanted.isEmpty() && !wanted.equals(room.getRoomId())) {
                continue;
            }
            matched.add(room);
        }
        matched.sort(Comparator.comparing(TetrisRoom::getStartedAt,
                Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        // 先分页再查用户信息
        List<TetrisRoom> pageRooms = GameActiveRoomPaging.slice(matched, pageNum, pageSize);
        Set<Long> userIds = new HashSet<>();
        for (TetrisRoom room : pageRooms) {
            collectActiveUserId(userIds, room.getRedUserId());
            collectActiveUserId(userIds, room.getBlueUserId());
        }
        Map<Long, UserInternalVO> userMap = loadActiveRoomUsers(userIds);
        List<TetrisActiveRoomVO> rows = new ArrayList<>(pageRooms.size());
        for (TetrisRoom room : pageRooms) {
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
        return GameActiveRoomPaging.toPage(rows, matched.size(), pageNum, pageSize);
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
                if (now >= room.getDeadlineMs()) {
                    finishRoom(room, resolveRaceWinner(room), GameConstants.END_RACE);
                    continue;
                }
                boolean changed = false;
                if (room.getPlayer1State().tickFall(now)) {
                    checkFinishAfterMove(room, room.getPlayer1UserId());
                    changed = true;
                }
                if (GameConstants.ROOM_PLAYING.equals(room.getRoomStatus())
                        && room.getPlayer2State().tickFall(now)) {
                    checkFinishAfterMove(room, room.getPlayer2UserId());
                    changed = true;
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

    /**
     * 竞速局到点后的裁定。
     *
     * <p>先比消行数——这是竞速真正比的东西；消行数打平再比分数，分数天然把四消与连击
     * 的技巧含量算进去了。两项都相同才算平局。
     *
     * <p>注意分数里含落锁分，所以不能把消行数与分数加权相加：那等于把消行的贡献算两遍。
     */
    private Long resolveRaceWinner(TetrisRoom room) {
        Long player1Id = room.getPlayer1UserId();
        Long player2Id = room.getPlayer2UserId();
        int lines1 = room.linesOf(player1Id);
        int lines2 = room.linesOf(player2Id);
        if (lines1 != lines2) {
            return lines1 > lines2 ? player1Id : player2Id;
        }
        int score1 = room.scoreOf(player1Id);
        int score2 = room.scoreOf(player2Id);
        if (score1 != score2) {
            return score1 > score2 ? player1Id : player2Id;
        }
        return null;
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

    // 竞速局中途堆到顶直接判负，不等时间到——否则「平铺不消行、只刷落锁分」会变成有效战术
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
        gameLobbyBroadcaster.roomsChanged(GameConstants.TETRIS_PK);
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
            record.setPlayer1Lines(room.linesOf(room.getPlayer1UserId()));
            record.setPlayer2Lines(room.linesOf(room.getPlayer2UserId()));
            record.setEndReason(endReason);
            GameRankSettlementResult rankResult = gameRankService.settleRank(createRankCommand(room, winnerId, loserId, endReason));
            int scoreDelta = winnerDelta(rankResult);
            record.setScoreDelta(scoreDelta);
            record.setWinnerScoreDelta(scoreDelta);
            record.setLoserScoreDelta(loserDelta(rankResult));
            record.setReplayPayload(buildReplayPayload(room));
            record.setStartedAt(room.getStartedAt());
            record.setEndedAt(new Date());
            record.setDeleteState(GameConstants.NOT_DELETED);
            gameTetrisPkMatchRecordMapper.insert(record);
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
            payload.put("player1Lines", room.linesOf(room.getPlayer1UserId()));
            payload.put("player2Lines", room.linesOf(room.getPlayer2UserId()));
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

    /**
     * 房间散场。
     *
     * <p>以前只清了内存里的两张表，Redis 里的房间状态缓存和匹配建房记录都还在，各自躺两小时。
     * 于是拿着旧房号仍然能「进房」——getRoomState 会从缓存把最后一帧还给你，看起来就像
     * 又回到了那局已经结束的对局。
     */
    private void cleanupRoom(TetrisRoom room) {
        userRoomIds.remove(room.getPlayer1UserId());
        userRoomIds.remove(room.getPlayer2UserId());
        rooms.remove(room.getRoomId());
        gameRoomStateCacheService.clearState(
                GameConstants.TETRIS_PK,
                room.getRoomId(),
                room.getPlayer1UserId(),
                room.getPlayer2UserId());
        gameMatchRoomHelper.releaseMatchedRoom("tetris", room.getPlayer1UserId(), room.getPlayer2UserId());
        // 排位结算里也会把状态改回 IDLE，但那条链路有幂等短路（同房号七天内只结算一次），
        // 一旦短路 profile 就还挂着旧房号，首页的按钮会一直显示「继续对局」并把人送回散了的房间。
        // 这里再显式归位一次，让「房间没了」和「玩家空闲了」这两件事不依赖同一条链路。
        releasePlayerStatus(room.getPlayer1UserId());
        releasePlayerStatus(room.getPlayer2UserId());
    }

    private void releasePlayerStatus(Long userId) {
        if (userId == null || GameConstants.AI_USER_ID.equals(userId)) {
            return;
        }
        try {
            gameUserProfileService.updateStatus(userId, GameConstants.TETRIS_PK, GameConstants.PROFILE_IDLE, null);
        } catch (Exception e) {
            log.warn("重置俄罗斯方块玩家状态失败 userId={}", userId, e);
        }
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
        return toStateVO(room, userId, false);
    }

    /**
     * @param includeChat 是否带上聊天记录。棋盘每推进一格都要广播一次，把整段聊天
     *                    塞进每一帧纯属浪费——只有首次进房和 HTTP 拉取才需要。
     */
    private TetrisRoomStateVO toStateVO(TetrisRoom room, Long userId, boolean includeChat) {
        boolean spectator = userId == null || !room.contains(userId);
        Map<Long, UserInternalVO> userMap = userSnapshotOf(room);
        Map<Long, GameUserProfile> profileMap = profileSnapshotOf(room);
        long startedAtMs = room.getStartedAt().getTime();
        GobangRoomParticipantVO player1 = toParticipant(room.getPlayer1UserId(), "PLAYER1", startedAtMs, userMap, profileMap);
        GobangRoomParticipantVO player2 = toParticipant(room.getPlayer2UserId(), "PLAYER2", startedAtMs, userMap, profileMap);
        Long opponentId = spectator ? null : room.opponentOf(userId);
        GobangRoomParticipantVO opponentPlayer = null;
        if (opponentId != null) {
            opponentPlayer = opponentId.equals(room.getPlayer1UserId()) ? player1 : player2;
        }
        int redScore = room.scoreOf(room.getRedUserId());
        int blueScore = room.scoreOf(room.getBlueUserId());
        int redLines = room.linesOf(room.getRedUserId());
        int blueLines = room.linesOf(room.getBlueUserId());
        int leftPercent = calcPkBarPercent(redLines, blueLines);
        TetrisBoardViewVO myBoard;
        TetrisBoardViewVO opponentBoard;
        if (!spectator) {
            myBoard = toBoardView(room.stateOf(userId), true);
            opponentBoard = toBoardView(room.stateOf(opponentId), false);
        } else {
            myBoard = toBoardView(room.stateOf(room.getPlayer1UserId()), false);
            opponentBoard = toBoardView(room.stateOf(room.getPlayer2UserId()), false);
        }
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
                redLines,
                blueLines,
                Math.max(0L, room.getDeadlineMs() - System.currentTimeMillis()),
                leftPercent,
                opponentPlayer,
                player1,
                player2,
                countSpectators(room),
                gameConnectionRegistry.countRoomOnline(room.getRoomId()),
                includeChat ? new ArrayList<>(room.getChatHistory()) : List.of()
        );
    }

    // 观战人数：房里在线的人减去两位对局玩家
    private int countSpectators(TetrisRoom room) {
        int count = 0;
        for (Long id : gameConnectionRegistry.roomUserIds(room.getRoomId())) {
            if (id != null && !room.contains(id)) {
                count++;
            }
        }
        return count;
    }
    // 顶部进度条按消行数分配左右宽度。双方都是 0 时必须给 50，
    // 原来的写法在 0:0 会算出 0% 再被夹到 5%，开局进度条就是歪的
    private int calcPkBarPercent(int redLines, int blueLines) {
        int total = redLines + blueLines;
        if (total <= 0 || redLines == blueLines) {
            return 50;
        }
        int percent = (int) Math.round(redLines * 100.0 / total);
        return Math.max(15, Math.min(85, percent));
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
                state.getCombo(),
                state.isGameOver(),
                revealHoldNext
        );
    }

    private TetrisCurPieceVO toPieceVO(TetrisBlock block) {
        return new TetrisCurPieceVO(block.getType(), block.getXy(), block.getShape());
    }

    /**
     * 房内两位玩家的用户资料。
     *
     * <p>棋盘每推进一格就要广播一次状态，而一次广播会对房里每个 session 各生成一份 VO。
     * 以前每份 VO 都去查一次 auth（Feign）和 profile 表，手速快的时候一个房间每秒能打出
     * 几十次跨域调用，全都只为了拿两个一局里根本不会变的昵称头像。这里在房间上缓存一次。
     *
     * <p>观战者不在缓存里——观战席现在只报人数不报名单，不需要他们的资料。
     */
    private Map<Long, UserInternalVO> userSnapshotOf(TetrisRoom room) {
        Map<Long, UserInternalVO> cached = room.getUserSnapshot();
        if (cached != null) {
            return cached;
        }
        loadSnapshots(room);
        Map<Long, UserInternalVO> loaded = room.getUserSnapshot();
        return loaded == null ? Map.of() : loaded;
    }

    private Map<Long, GameUserProfile> profileSnapshotOf(TetrisRoom room) {
        Map<Long, GameUserProfile> cached = room.getProfileSnapshot();
        if (cached != null) {
            return cached;
        }
        loadSnapshots(room);
        Map<Long, GameUserProfile> loaded = room.getProfileSnapshot();
        return loaded == null ? Map.of() : loaded;
    }

    private void loadSnapshots(TetrisRoom room) {
        List<Long> userIds = new ArrayList<>();
        addRealUserId(userIds, room.getPlayer1UserId());
        addRealUserId(userIds, room.getPlayer2UserId());
        if (userIds.isEmpty()) {
            room.cacheSnapshots(Map.of(), Map.of());
            return;
        }
        Map<Long, UserInternalVO> userMap = new HashMap<>();
        Map<Long, GameUserProfile> profileMap = new HashMap<>();
        try {
            gameUserLookupService.listByIds(userIds).forEach(user -> userMap.put(user.getId(), user));
            gameUserProfileMapper.selectList(new LambdaQueryWrapper<GameUserProfile>()
                    .eq(GameUserProfile::getGameCode, GameConstants.TETRIS_PK)
                    .eq(GameUserProfile::getDeleteState, GameConstants.NOT_DELETED)
                    .in(GameUserProfile::getUserId, userIds))
                    .forEach(profile -> profileMap.put(profile.getUserId(), profile));
        } catch (Exception e) {
            // 查不到就退化成没有昵称头像，绝不能因为拉资料失败把对局卡住
            log.warn("加载俄罗斯方块房间用户资料失败 roomId={}", room.getRoomId(), e);
            return;
        }
        room.cacheSnapshots(userMap, profileMap);
    }
    private void addRealUserId(List<Long> userIds, Long userId) {
        if (userId != null && userId > 0 && !userIds.contains(userId)) {
            userIds.add(userId);
        }
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
                log.debug("广播俄罗斯方块房间状态失败 roomId={}, userId={}, error={}", room.getRoomId(), uid, e.getMessage());
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
            log.debug("广播俄罗斯方块房间消息失败 roomId={}, error={}", roomId, e.getMessage());
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
            log.debug("发布俄罗斯方块定向状态失败 roomId={}, error={}", room.getRoomId(), e.getMessage());
        }
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

    private void sendRoomError(String roomId, Long userId, String requestId, String message) {
        try {
            gameConnectionRegistry.sendToRoom(
                    roomId,
                    userId,
                    objectMapper.writeValueAsString(GameWsResponse.fail("room_error", requestId, message))
            );
        } catch (Exception e) {
            log.debug("发送俄罗斯方块房间错误失败 roomId={}, userId={}, error={}", roomId, userId, e.getMessage());
        }
    }
}
