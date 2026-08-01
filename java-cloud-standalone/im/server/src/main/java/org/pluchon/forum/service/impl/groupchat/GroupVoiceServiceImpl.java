package org.pluchon.forum.service.impl.groupchat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.enums.GroupChatMemberRole;
import org.pluchon.forum.common.enums.GroupChatMemberStatus;
import org.pluchon.forum.common.enums.GroupChatStatus;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.utils.ForumDateTimes;
import org.pluchon.forum.entity.db.GroupChat;
import org.pluchon.forum.entity.db.GroupChatMember;
import org.pluchon.forum.entity.db.User;
import org.pluchon.forum.entity.dto.groupchat.GroupVoiceSignalRequest;
import org.pluchon.forum.entity.vo.groupchat.GroupVoiceParticipantVO;
import org.pluchon.forum.entity.vo.groupchat.GroupVoiceSessionVO;
import org.pluchon.forum.entity.vo.user.UserBriefVO;
import org.pluchon.forum.mapper.GroupChatMapper;
import org.pluchon.forum.mapper.GroupChatMemberMapper;
import org.pluchon.forum.service.interfaces.groupchat.GroupChatService;
import org.pluchon.forum.service.impl.websocket.WebSocketPushService;
import org.pluchon.forum.service.interfaces.groupchat.GroupVoiceService;
import org.pluchon.forum.service.interfaces.user.UserService;
import org.pluchon.forum.service.interfaces.voice.VoiceOccupancyService;
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
// 群语音业务实现
public class GroupVoiceServiceImpl implements GroupVoiceService {

    // 群语音最多席位数
    private static final int MAX_SEATS = 6;

    // Redis Key 前缀
    private static final String SESSION_KEY_PREFIX = "group:voice:session:";

    // 会话最长保留时间，防异常退出残留
    private static final Duration SESSION_TTL = Duration.ofHours(6);

    // 当前单实例互斥，避免同一群并发创建多个语音状态
    private final Object voiceLock = new Object();

    @Autowired
    private GroupChatMapper groupChatMapper;

    @Autowired
    private GroupChatMemberMapper groupChatMemberMapper;

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
    private GroupChatService groupChatService;

    @Override
    public GroupVoiceSessionVO querySession(Long groupId, Long loginUserId) {
        assertActiveMember(groupId, loginUserId);
        VoiceSessionState state = loadState(groupId);
        return toSessionVO(groupId, state, loginUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GroupVoiceSessionVO startSession(Long groupId, Long loginUserId) {
        GroupChat group = queryGroup(groupId);
        GroupChatMember member = assertActiveMember(groupId, loginUserId);
        assertManager(member);
        VoiceSessionState state;
        synchronized (voiceLock) {
            String occupancySessionId = occupancySessionId(groupId);
            voiceOccupancyService.assertAvailable(loginUserId, occupancySessionId);
            state = loadState(groupId);
            if (state == null || state.getParticipants().isEmpty()) {
                state = new VoiceSessionState();
                state.setGroupId(groupId);
                state.setRoomVersion(System.currentTimeMillis());
                state.setInitiatorUserId(loginUserId);
                state.setStartedAt(ForumDateTimes.now());
                state.setParticipants(new LinkedHashMap<>());
            }
            putParticipant(state, loginUserId, true);
            saveState(state);
            voiceOccupancyService.bind(loginUserId, occupancySessionId, SESSION_TTL);
        }
        broadcastStatus(group);
        return toSessionVO(groupId, state, loginUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GroupVoiceSessionVO joinSession(Long groupId, Long loginUserId) {
        GroupChat group = queryGroup(groupId);
        assertActiveMember(groupId, loginUserId);
        VoiceSessionState state;
        synchronized (voiceLock) {
            String occupancySessionId = occupancySessionId(groupId);
            voiceOccupancyService.assertAvailable(loginUserId, occupancySessionId);
            state = loadState(groupId);
            if (state == null || state.getParticipants().isEmpty()) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS, "当前没有群语音聊天"));
            }
            if (!state.getParticipants().containsKey(String.valueOf(loginUserId))
                    && state.getParticipants().size() >= MAX_SEATS) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_FORBIDDEN, "语音聊天人数已满"));
            }
            putParticipant(state, loginUserId, true);
            saveState(state);
            voiceOccupancyService.bind(loginUserId, occupancySessionId, SESSION_TTL);
        }
        broadcastStatus(group);
        return toSessionVO(groupId, state, loginUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GroupVoiceSessionVO leaveSession(Long groupId, Long loginUserId) {
        GroupChat group = queryGroup(groupId);
        assertActiveMember(groupId, loginUserId);
        VoiceSessionState state;
        boolean ended = false;
        String durationText = null;
        synchronized (voiceLock) {
            state = loadState(groupId);
            if (state != null) {
                state.getParticipants().remove(String.valueOf(loginUserId));
                if (state.getParticipants().isEmpty()) {
                    ended = true;
                    durationText = formatDuration(state.getStartedAt(), ForumDateTimes.now());
                    deleteState(groupId);
                } else {
                    saveState(state);
                }
            }
            voiceOccupancyService.release(loginUserId, occupancySessionId(groupId));
        }
        if (ended) {
            groupChatService.appendVoiceCallSummary(groupId, loginUserId, durationText);
        }
        broadcastStatus(group);
        return toSessionVO(groupId, state, loginUserId);
    }

    @Override
    public void handleSignal(GroupVoiceSignalRequest request, Long loginUserId) {
        if (request == null
                || request.getGroupId() == null
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
        assertActiveMember(request.getGroupId(), loginUserId);
        VoiceSessionState state = loadState(request.getGroupId());
        if (state == null) {
            return;
        }
        if (!Objects.equals(state.getRoomVersion(), request.getRoomVersion())) {
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
            payload.put("type", "group_voice_signal");
            payload.put("groupId", request.getGroupId());
            payload.put("roomVersion", state.getRoomVersion());
            payload.put("fromUserId", loginUserId);
            payload.put("fromConnectionId", sender.getConnectionId());
            payload.put("targetConnectionId", target.getConnectionId());
            payload.put("signalType", request.getSignalType().trim());
            payload.put("payload", request.getPayload());
            String json = objectMapper.writeValueAsString(payload);
            webSocketPushService.push(request.getTargetUserId(), json);
        } catch (Exception e) {
            log.warn("群语音信令转发失败 groupId={} from={} target={}",
                    request.getGroupId(), loginUserId, request.getTargetUserId(), e);
        }
    }

    private void putParticipant(VoiceSessionState state, Long userId, boolean renewConnection) {
        ParticipantState participant = state.getParticipants().get(String.valueOf(userId));
        if (participant == null) {
            participant = new ParticipantState();
            participant.setUserId(userId);
            participant.setJoinedAt(ForumDateTimes.now());
            state.getParticipants().put(String.valueOf(userId), participant);
        }
        if (renewConnection || !StringUtils.hasText(participant.getConnectionId())) {
            participant.setConnectionId(UUID.randomUUID().toString().replace("-", ""));
        }
    }

    private GroupChat queryGroup(Long groupId) {
        if (groupId == null || groupId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        GroupChat group = groupChatMapper.selectOne(new LambdaQueryWrapper<GroupChat>()
                .eq(GroupChat::getId, groupId)
                .in(GroupChat::getStatus, List.of(
                        GroupChatStatus.NORMAL.getCode(),
                        GroupChatStatus.FULL.getCode(),
                        GroupChatStatus.OVER_LIMIT_LOCKED.getCode()))
                .ne(GroupChat::getDeleteState, Constant.DELETE_STATE_TRUE));
        if (group == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
        return group;
    }

    private GroupChatMember assertActiveMember(Long groupId, Long userId) {
        GroupChatMember member = groupChatMemberMapper.selectOne(new LambdaQueryWrapper<GroupChatMember>()
                .eq(GroupChatMember::getGroupId, groupId)
                .eq(GroupChatMember::getUserId, userId)
                .eq(GroupChatMember::getStatus, GroupChatMemberStatus.ACTIVE.getCode())
                .ne(GroupChatMember::getDeleteState, Constant.DELETE_STATE_TRUE));
        if (member == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_FORBIDDEN, "你不在该群聊中"));
        }
        return member;
    }

    private void assertManager(GroupChatMember member) {
        if (member == null
                || (!GroupChatMemberRole.OWNER.getCode().equals(member.getRole())
                && !GroupChatMemberRole.ADMIN.getCode().equals(member.getRole()))) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_FORBIDDEN, "只有群主或管理员可以发起语音聊天"));
        }
    }

    private List<GroupChatMember> activeMembers(Long groupId) {
        return groupChatMemberMapper.selectList(new LambdaQueryWrapper<GroupChatMember>()
                .eq(GroupChatMember::getGroupId, groupId)
                .eq(GroupChatMember::getStatus, GroupChatMemberStatus.ACTIVE.getCode())
                .ne(GroupChatMember::getDeleteState, Constant.DELETE_STATE_TRUE));
    }

    private void broadcastStatus(GroupChat group) {
        List<GroupChatMember> members = activeMembers(group.getId());
        for (GroupChatMember member : members) {
            try {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("type", "group_voice_status");
                payload.put("groupId", group.getId());
                payload.put("session", toSessionVO(group.getId(), loadState(group.getId()), member.getUserId()));
                webSocketPushService.push(member.getUserId(), objectMapper.writeValueAsString(payload));
            } catch (Exception e) {
                log.warn("群语音状态推送失败 groupId={} userId={}", group.getId(), member.getUserId(), e);
            }
        }
    }

    private VoiceSessionState loadState(Long groupId) {
        String raw = stringRedisTemplate.opsForValue().get(sessionKey(groupId));
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            VoiceSessionState state = objectMapper.readValue(raw, VoiceSessionState.class);
            if (state.getParticipants() == null) {
                state.setParticipants(new LinkedHashMap<>());
            }
            if (state.getParticipants().isEmpty()) {
                deleteState(groupId);
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
            log.warn("群语音状态读取失败 groupId={}", groupId, e);
            deleteState(groupId);
            return null;
        }
    }

    private void saveState(VoiceSessionState state) {
        try {
            stringRedisTemplate.opsForValue().set(
                    sessionKey(state.getGroupId()),
                    objectMapper.writeValueAsString(state),
                    SESSION_TTL);
        } catch (Exception e) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED, "语音状态保存失败"));
        }
    }

    private void deleteState(Long groupId) {
        stringRedisTemplate.delete(sessionKey(groupId));
    }

    private String sessionKey(Long groupId) {
        return SESSION_KEY_PREFIX + groupId;
    }

    private String occupancySessionId(Long groupId) {
        return "group:" + groupId;
    }

    private GroupVoiceSessionVO toSessionVO(Long groupId, VoiceSessionState state, Long currentUserId) {
        GroupChatMember currentMember = null;
        try {
            currentMember = assertActiveMember(groupId, currentUserId);
        } catch (Exception ignored) {
            // 查询入口已经做权限校验，这里只兜底避免状态广播中断。
        }
        GroupVoiceSessionVO vo = new GroupVoiceSessionVO();
        vo.setGroupId(groupId);
        vo.setMaxSeats(MAX_SEATS);
        vo.setCurrentUserManager(currentMember != null
                && (GroupChatMemberRole.OWNER.getCode().equals(currentMember.getRole())
                || GroupChatMemberRole.ADMIN.getCode().equals(currentMember.getRole())));
        if (state == null || state.getParticipants() == null || state.getParticipants().isEmpty()) {
            vo.setActive(false);
            vo.setMemberCount(0);
            vo.setCurrentUserJoined(false);
            vo.setParticipants(List.of());
            return vo;
        }
        vo.setActive(true);
        vo.setRoomVersion(state.getRoomVersion());
        vo.setInitiatorUserId(state.getInitiatorUserId());
        vo.setStartedAt(state.getStartedAt());
        ParticipantState currentParticipant = state.getParticipants().get(String.valueOf(currentUserId));
        vo.setCurrentUserJoined(currentParticipant != null);
        vo.setCurrentConnectionId(currentParticipant == null ? null : currentParticipant.getConnectionId());
        List<GroupVoiceParticipantVO> participants = state.getParticipants().values().stream()
                .sorted(Comparator.comparing(ParticipantState::getJoinedAt, Comparator.nullsLast(Date::compareTo)))
                .map(this::toParticipantVO)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        vo.setParticipants(participants);
        vo.setMemberCount(participants.size());
        return vo;
    }

    private GroupVoiceParticipantVO toParticipantVO(ParticipantState participant) {
        User user = userService.queryUserByUserId(participant.getUserId());
        if (user == null) {
            return null;
        }
        GroupVoiceParticipantVO vo = new GroupVoiceParticipantVO();
        vo.setUser(org.pluchon.forum.converter.ImUserBriefConverter.toBrief(user));
        vo.setConnectionId(participant.getConnectionId());
        vo.setJoinedAt(participant.getJoinedAt());
        return vo;
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

    // Redis 中保存的群语音会话状态
    @Data
    private static class VoiceSessionState {
        private Long groupId;
        private Long roomVersion;
        private Long initiatorUserId;
        private Date startedAt;
        private Map<String, ParticipantState> participants = new LinkedHashMap<>();
    }

    // Redis 中保存的群语音席位状态
    @Data
    private static class ParticipantState {
        private Long userId;
        private String connectionId;
        private Date joinedAt;
    }
}
