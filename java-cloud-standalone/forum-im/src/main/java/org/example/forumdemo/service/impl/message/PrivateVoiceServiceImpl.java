package org.example.forumdemo.service.impl.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.common.utils.ForumDateTimes;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.dto.message.PrivateVoiceSignalRequest;
import org.example.forumdemo.entity.vo.message.PrivateVoiceParticipantVO;
import org.example.forumdemo.entity.vo.message.PrivateVoiceSessionVO;
import org.example.forumdemo.entity.vo.user.UserBriefVO;
import org.example.forumdemo.service.impl.websocket.WebSocketPushService;
import org.example.forumdemo.service.interfaces.message.MessageService;
import org.example.forumdemo.service.interfaces.message.PrivateVoiceService;
import org.example.forumdemo.service.interfaces.user.UserService;
import org.example.forumdemo.service.interfaces.voice.VoiceOccupancyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
// 私聊语音业务实现
public class PrivateVoiceServiceImpl implements PrivateVoiceService {

    // 私聊语音最大席位
    private static final int MAX_SEATS = 2;

    // Redis Key 前缀
    private static final String SESSION_KEY_PREFIX = "private:voice:session:";

    // 会话最长保留时间
    private static final Duration SESSION_TTL = Duration.ofHours(6);

    // 单实例互斥，避免同一对用户并发创建多个状态
    private final Object voiceLock = new Object();

    @Autowired
    private UserService userService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WebSocketPushService webSocketPushService;

    @Autowired
    private VoiceOccupancyService voiceOccupancyService;

    @Autowired
    private MessageService messageService;

    @Override
    public PrivateVoiceSessionVO querySession(Long peerUserId, Long loginUserId) {
        assertPeer(peerUserId, loginUserId);
        VoiceSessionState state = loadState(peerUserId, loginUserId);
        return toSessionVO(peerUserId, state, loginUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PrivateVoiceSessionVO startSession(Long peerUserId, Long loginUserId) {
        assertPeer(peerUserId, loginUserId);
        VoiceSessionState state;
        synchronized (voiceLock) {
            String sessionId = sessionId(peerUserId, loginUserId);
            voiceOccupancyService.assertAvailable(loginUserId, sessionId);
            state = loadState(peerUserId, loginUserId);
            if (state == null || state.getParticipants().isEmpty()) {
                state = newState(peerUserId, loginUserId);
            }
            if (!state.getParticipants().containsKey(String.valueOf(loginUserId))
                    && state.getParticipants().size() >= MAX_SEATS) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_FORBIDDEN, "语音聊天人数已满"));
            }
            putParticipant(state, loginUserId);
            saveState(state);
            voiceOccupancyService.bind(loginUserId, sessionId, SESSION_TTL);
        }
        broadcastStatus(state);
        return toSessionVO(peerUserId, state, loginUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PrivateVoiceSessionVO acceptSession(Long peerUserId, Long loginUserId) {
        assertPeer(peerUserId, loginUserId);
        VoiceSessionState state;
        synchronized (voiceLock) {
            String sessionId = sessionId(peerUserId, loginUserId);
            voiceOccupancyService.assertAvailable(loginUserId, sessionId);
            state = loadState(peerUserId, loginUserId);
            if (state == null || state.getParticipants().isEmpty()) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS, "当前没有私聊语音"));
            }
            if (!state.getParticipants().containsKey(String.valueOf(loginUserId))
                    && state.getParticipants().size() >= MAX_SEATS) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_FORBIDDEN, "语音聊天人数已满"));
            }
            putParticipant(state, loginUserId);
            if (state.getAcceptedAt() == null) {
                state.setAcceptedAt(ForumDateTimes.now());
            }
            saveState(state);
            voiceOccupancyService.bind(loginUserId, sessionId, SESSION_TTL);
        }
        broadcastStatus(state);
        return toSessionVO(peerUserId, state, loginUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PrivateVoiceSessionVO declineSession(Long peerUserId, Long loginUserId) {
        assertPeer(peerUserId, loginUserId);
        VoiceSessionState state;
        boolean wasAccepted;
        synchronized (voiceLock) {
            state = loadState(peerUserId, loginUserId);
            wasAccepted = isAccepted(state);
            if (state != null) {
                releaseParticipants(state);
                deleteState(peerUserId, loginUserId);
            }
        }
        if (state != null) {
            appendVoiceSummaryIfNeeded(state, loginUserId, wasAccepted);
            broadcastInactive(state);
        }
        return toSessionVO(peerUserId, null, loginUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PrivateVoiceSessionVO leaveSession(Long peerUserId, Long loginUserId) {
        assertPeer(peerUserId, loginUserId);
        VoiceSessionState state;
        boolean wasAccepted;
        synchronized (voiceLock) {
            state = loadState(peerUserId, loginUserId);
            wasAccepted = isAccepted(state);
            if (state != null) {
                releaseParticipants(state);
                deleteState(peerUserId, loginUserId);
            }
        }
        if (state != null) {
            appendVoiceSummaryIfNeeded(state, loginUserId, wasAccepted);
            broadcastInactive(state);
        }
        return toSessionVO(peerUserId, null, loginUserId);
    }

    @Override
    public void handleSignal(PrivateVoiceSignalRequest request, Long loginUserId) {
        if (request == null
                || request.getPeerUserId() == null
                || request.getRoomVersion() == null
                || request.getTargetUserId() == null) {
            return;
        }
        if (!StringUtils.hasText(request.getSignalType())
                || !StringUtils.hasText(request.getSenderConnectionId())
                || !StringUtils.hasText(request.getTargetConnectionId())
                || request.getPayload() == null) {
            return;
        }
        if (!isPairMember(request.getPeerUserId(), loginUserId, request.getTargetUserId())) {
            return;
        }
        VoiceSessionState state = loadState(request.getPeerUserId(), loginUserId);
        if (state == null || !Objects.equals(state.getRoomVersion(), request.getRoomVersion())) {
            return;
        }
        ParticipantState sender = state.getParticipants().get(String.valueOf(loginUserId));
        ParticipantState target = state.getParticipants().get(String.valueOf(request.getTargetUserId()));
        if (sender == null || target == null) {
            return;
        }
        if (!Objects.equals(sender.getConnectionId(), request.getSenderConnectionId())
                || !Objects.equals(target.getConnectionId(), request.getTargetConnectionId())) {
            return;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "private_voice_signal");
            payload.put("sessionId", state.getSessionId());
            payload.put("peerUserId", loginUserId);
            payload.put("roomVersion", state.getRoomVersion());
            payload.put("fromUserId", loginUserId);
            payload.put("fromConnectionId", sender.getConnectionId());
            payload.put("targetConnectionId", target.getConnectionId());
            payload.put("signalType", request.getSignalType().trim());
            payload.put("payload", request.getPayload());
            webSocketPushService.push(request.getTargetUserId(), objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            log.warn("私聊语音信令转发失败 from={} target={}",
                    loginUserId, request.getTargetUserId(), e);
        }
    }

    private VoiceSessionState newState(Long peerUserId, Long loginUserId) {
        VoiceSessionState state = new VoiceSessionState();
        state.setSessionId(sessionId(peerUserId, loginUserId));
        state.setRoomVersion(System.currentTimeMillis());
        state.setInitiatorUserId(loginUserId);
        state.setUserAId(Math.min(peerUserId, loginUserId));
        state.setUserBId(Math.max(peerUserId, loginUserId));
        state.setStartedAt(ForumDateTimes.now());
        state.setParticipants(new LinkedHashMap<>());
        return state;
    }

    private void putParticipant(VoiceSessionState state, Long userId) {
        ParticipantState participant = state.getParticipants().get(String.valueOf(userId));
        if (participant == null) {
            participant = new ParticipantState();
            participant.setUserId(userId);
            participant.setJoinedAt(ForumDateTimes.now());
            state.getParticipants().put(String.valueOf(userId), participant);
        }
        participant.setConnectionId(UUID.randomUUID().toString().replace("-", ""));
    }

    private void assertPeer(Long peerUserId, Long loginUserId) {
        if (peerUserId == null || peerUserId <= 0 || loginUserId == null || loginUserId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        if (Objects.equals(peerUserId, loginUserId)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_SEND_MESSAGE_BY_MYSELF));
        }
        User peer = userService.queryUserByUserId(peerUserId);
        if (peer == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
        }
    }

    private boolean isPairMember(Long peerUserId, Long loginUserId, Long targetUserId) {
        return Objects.equals(peerUserId, targetUserId)
                && !Objects.equals(loginUserId, targetUserId);
    }

    private void broadcastStatus(VoiceSessionState state) {
        if (state == null) {
            return;
        }
        pushStatus(state.getUserAId(), state);
        pushStatus(state.getUserBId(), state);
    }

    private void broadcastInactive(VoiceSessionState state) {
        if (state == null) {
            return;
        }
        pushStatus(state.getUserAId(), null, state);
        pushStatus(state.getUserBId(), null, state);
    }

    private void pushStatus(Long targetUserId, VoiceSessionState state) {
        pushStatus(targetUserId, state, state);
    }

    private void pushStatus(Long targetUserId, VoiceSessionState currentState, VoiceSessionState pairState) {
        if (targetUserId == null || pairState == null) {
            return;
        }
        try {
            Long peerUserId = peerId(pairState, targetUserId);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "private_voice_status");
            payload.put("sessionId", pairState.getSessionId());
            payload.put("peerUserId", peerUserId);
            payload.put("session", toSessionVO(peerUserId, currentState, targetUserId));
            webSocketPushService.push(targetUserId, objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            log.warn("私聊语音状态推送失败 targetUserId={}", targetUserId, e);
        }
    }

    private VoiceSessionState loadState(Long peerUserId, Long loginUserId) {
        String raw = stringRedisTemplate.opsForValue().get(sessionKey(peerUserId, loginUserId));
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            VoiceSessionState state = objectMapper.readValue(raw, VoiceSessionState.class);
            if (state.getParticipants() == null) {
                state.setParticipants(new LinkedHashMap<>());
            }
            if (state.getParticipants().isEmpty()) {
                deleteState(peerUserId, loginUserId);
                return null;
            }
            boolean changed = false;
            if (state.getRoomVersion() == null) {
                state.setRoomVersion(System.currentTimeMillis());
                changed = true;
            }
            for (ParticipantState participant : state.getParticipants().values()) {
                if (!StringUtils.hasText(participant.getConnectionId())) {
                    participant.setConnectionId(UUID.randomUUID().toString().replace("-", ""));
                    changed = true;
                }
            }
            if (changed) {
                saveState(state);
            }
            return state;
        } catch (Exception e) {
            log.warn("私聊语音状态读取失败 session={}", sessionId(peerUserId, loginUserId), e);
            deleteState(peerUserId, loginUserId);
            return null;
        }
    }

    private void saveState(VoiceSessionState state) {
        try {
            stringRedisTemplate.opsForValue().set(
                    SESSION_KEY_PREFIX + state.getSessionId(),
                    objectMapper.writeValueAsString(state),
                    SESSION_TTL);
        } catch (Exception e) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED, "语音状态保存失败"));
        }
    }

    private void deleteState(Long peerUserId, Long loginUserId) {
        stringRedisTemplate.delete(sessionKey(peerUserId, loginUserId));
    }

    private void releaseParticipants(VoiceSessionState state) {
        voiceOccupancyService.releaseAll(
                List.of(state.getUserAId(), state.getUserBId()),
                state.getSessionId());
    }

    private String sessionKey(Long peerUserId, Long loginUserId) {
        return SESSION_KEY_PREFIX + sessionId(peerUserId, loginUserId);
    }

    private String sessionId(Long peerUserId, Long loginUserId) {
        long first = Math.min(peerUserId, loginUserId);
        long second = Math.max(peerUserId, loginUserId);
        return "private:" + first + ":" + second;
    }

    private Long peerId(VoiceSessionState state, Long userId) {
        return Objects.equals(state.getUserAId(), userId) ? state.getUserBId() : state.getUserAId();
    }

    private PrivateVoiceSessionVO toSessionVO(Long peerUserId, VoiceSessionState state, Long currentUserId) {
        PrivateVoiceSessionVO vo = new PrivateVoiceSessionVO();
        vo.setSessionId(sessionId(peerUserId, currentUserId));
        vo.setPeerUserId(peerUserId);
        vo.setMaxSeats(MAX_SEATS);
        if (state == null || state.getParticipants() == null || state.getParticipants().isEmpty()) {
            vo.setActive(false);
            vo.setMemberCount(0);
            vo.setCurrentUserJoined(false);
            vo.setCurrentUserInitiator(false);
            vo.setParticipants(List.of());
            return vo;
        }
        ParticipantState currentParticipant = state.getParticipants().get(String.valueOf(currentUserId));
        vo.setSessionId(state.getSessionId());
        vo.setActive(true);
        vo.setRoomVersion(state.getRoomVersion());
        vo.setInitiatorUserId(state.getInitiatorUserId());
        vo.setStartedAt(state.getStartedAt());
        vo.setCurrentUserJoined(currentParticipant != null);
        vo.setCurrentUserInitiator(Objects.equals(state.getInitiatorUserId(), currentUserId));
        vo.setCurrentConnectionId(currentParticipant == null ? null : currentParticipant.getConnectionId());
        List<PrivateVoiceParticipantVO> participants = state.getParticipants().values().stream()
                .sorted(Comparator.comparing(ParticipantState::getJoinedAt, Comparator.nullsLast(Date::compareTo)))
                .map(this::toParticipantVO)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        vo.setParticipants(participants);
        vo.setMemberCount(participants.size());
        return vo;
    }

    private PrivateVoiceParticipantVO toParticipantVO(ParticipantState participant) {
        User user = userService.queryUserByUserId(participant.getUserId());
        if (user == null) {
            return null;
        }
        PrivateVoiceParticipantVO vo = new PrivateVoiceParticipantVO();
        vo.setUser(new UserBriefVO(user));
        vo.setConnectionId(participant.getConnectionId());
        vo.setJoinedAt(participant.getJoinedAt());
        return vo;
    }

    private boolean isAccepted(VoiceSessionState state) {
        return state != null
                && (state.getAcceptedAt() != null
                || (state.getParticipants() != null && state.getParticipants().size() >= MAX_SEATS));
    }

    private void appendVoiceSummaryIfNeeded(VoiceSessionState state, Long operatorUserId, boolean wasAccepted) {
        if (!wasAccepted || state == null || operatorUserId == null) {
            return;
        }
        Long peerUserId = peerId(state, operatorUserId);
        Date startTime = state.getAcceptedAt() == null ? state.getStartedAt() : state.getAcceptedAt();
        messageService.appendVoiceCallSummary(operatorUserId, peerUserId, formatDuration(startTime, ForumDateTimes.now()));
    }

    private String formatDuration(Date startTime, Date endTime) {
        if (startTime == null || endTime == null) {
            return "00:00";
        }
        long seconds = Math.max(0L, Duration.between(startTime.toInstant(), endTime.toInstant()).getSeconds());
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainSeconds = seconds % 60;
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, remainSeconds);
        }
        return String.format("%02d:%02d", minutes, remainSeconds);
    }

    // Redis 中保存的私聊语音会话状态
    @Data
    private static class VoiceSessionState {
        private String sessionId;
        private Long roomVersion;
        private Long initiatorUserId;
        private Long userAId;
        private Long userBId;
        private Date startedAt;
        private Date acceptedAt;
        private Map<String, ParticipantState> participants = new LinkedHashMap<>();
    }

    // Redis 中保存的私聊语音席位状态
    @Data
    private static class ParticipantState {
        private Long userId;
        private String connectionId;
        private Date joinedAt;
    }
}
