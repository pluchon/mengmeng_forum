package org.example.forumdemo.service.impl.groupchat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.config.OssConfig;
import org.example.forumdemo.common.enums.GroupChatMemberRole;
import org.example.forumdemo.common.enums.GroupChatMemberStatus;
import org.example.forumdemo.common.enums.GroupChatMessageStatus;
import org.example.forumdemo.common.enums.GroupChatMessageType;
import org.example.forumdemo.common.enums.GroupChatNotifyMode;
import org.example.forumdemo.common.enums.GroupChatReportStatus;
import org.example.forumdemo.common.enums.GroupChatStatus;
import org.example.forumdemo.common.enums.GroupChatType;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.common.utils.ForumDateTimes;
import org.example.forumdemo.common.utils.PageUtils;
import org.example.forumdemo.common.utils.TransactionHooks;
import org.example.forumdemo.common.utils.UserMuteGuard;
import org.example.forumdemo.converter.GroupChatConverter;
import org.example.forumdemo.entity.db.GroupChat;
import org.example.forumdemo.entity.db.GroupChatMember;
import org.example.forumdemo.entity.db.GroupChatMessage;
import org.example.forumdemo.entity.db.GroupChatReport;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.dto.groupchat.CreateGroupChatRequest;
import org.example.forumdemo.entity.dto.groupchat.GroupInviteMemberRequest;
import org.example.forumdemo.entity.dto.groupchat.GroupMuteMemberRequest;
import org.example.forumdemo.entity.dto.groupchat.ReportGroupChatMessageRequest;
import org.example.forumdemo.entity.dto.groupchat.SendGroupChatMessageRequest;
import org.example.forumdemo.entity.dto.groupchat.UpdateGroupChatRequest;
import org.example.forumdemo.entity.dto.groupchat.UpdateGroupMemberRemarkRequest;
import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.entity.vo.groupchat.GroupChatDetailVO;
import org.example.forumdemo.entity.vo.groupchat.GroupChatMemberVO;
import org.example.forumdemo.entity.vo.groupchat.GroupChatMessageVO;
import org.example.forumdemo.entity.vo.groupchat.GroupChatSessionVO;
import org.example.forumdemo.mapper.GroupChatMapper;
import org.example.forumdemo.mapper.GroupChatMemberMapper;
import org.example.forumdemo.mapper.GroupChatMessageMapper;
import org.example.forumdemo.mapper.GroupChatReportMapper;
import org.example.forumdemo.service.impl.websocket.WebSocketPushService;
import org.example.forumdemo.service.interfaces.groupchat.GroupChatService;
import org.example.forumdemo.service.interfaces.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
// 群聊业务实现
public class GroupChatServiceImpl implements GroupChatService {

    // 群聊主表 Mapper
    @Autowired
    private GroupChatMapper groupChatMapper;

    // 群成员 Mapper
    @Autowired
    private GroupChatMemberMapper groupChatMemberMapper;

    // 群消息 Mapper
    @Autowired
    private GroupChatMessageMapper groupChatMessageMapper;

    // 群举报 Mapper
    @Autowired
    private GroupChatReportMapper groupChatReportMapper;

    // 用户服务
    @Autowired
    private UserService userService;

    // WebSocket 推送服务
    @Autowired
    private WebSocketPushService webSocketPushService;

    // OSS 配置
    @Autowired
    private OssConfig ossConfig;

    // JSON 序列化器
    @Autowired
    private ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GroupChatDetailVO createGroup(CreateGroupChatRequest request, Long loginUserId) {
        User owner = userService.queryUserByUserId(loginUserId);
        assertCertifiedCreator(owner);
        Byte type = normalizeGroupType(request.getGroupType());
        String name = normalizeName(request.getName());
        String intro = normalizeIntro(request.getIntro());
        assertCreateQuota(owner);

        Date now = ForumDateTimes.now();
        GroupChat group = new GroupChat();
        group.setOwnerUserId(loginUserId);
        group.setName(name);
        group.setIntro(intro);
        group.setAvatarUrl(normalizeOptional(request.getAvatarUrl()));
        group.setGroupType(type);
        group.setMemberLimit(memberLimitFor(owner));
        group.setMemberCount(1);
        group.setStatus(GroupChatStatus.NORMAL.getCode());
        group.setDeleteState(Constant.DELETE_STATE_FALSE);
        group.setCreateTime(now);
        group.setUpdateTime(now);
        if (groupChatMapper.insert(group) <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_CREATE));
        }

        GroupChatMember ownerMember = new GroupChatMember();
        ownerMember.setGroupId(group.getId());
        ownerMember.setUserId(loginUserId);
        ownerMember.setRole(GroupChatMemberRole.OWNER.getCode());
        ownerMember.setNotifyMode(GroupChatNotifyMode.NORMAL.getCode());
        ownerMember.setMuteUntil(null);
        ownerMember.setLastReadMessageId(0L);
        ownerMember.setJoinTime(now);
        ownerMember.setStatus(GroupChatMemberStatus.ACTIVE.getCode());
        ownerMember.setDeleteState(Constant.DELETE_STATE_FALSE);
        ownerMember.setCreateTime(now);
        ownerMember.setUpdateTime(now);
        if (groupChatMemberMapper.insert(ownerMember) <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_CREATE));
        }
        return GroupChatConverter.toDetailVO(group);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GroupChatDetailVO updateGroup(Long groupId, UpdateGroupChatRequest request, Long loginUserId) {
        GroupChat group = queryGroup(groupId);
        assertOwner(group, loginUserId);
        Byte type = request.getGroupType() == null ? group.getGroupType() : normalizeGroupType(request.getGroupType());
        String name = request.getName() == null ? group.getName() : normalizeName(request.getName());
        String intro = request.getIntro() == null ? group.getIntro() : normalizeIntro(request.getIntro());
        Date now = ForumDateTimes.now();
        int affected = groupChatMapper.update(null, new LambdaUpdateWrapper<GroupChat>()
                .eq(GroupChat::getId, groupId)
                .ne(GroupChat::getDeleteState, Constant.DELETE_STATE_TRUE)
                .set(GroupChat::getName, name)
                .set(GroupChat::getIntro, intro)
                .set(GroupChat::getAvatarUrl, normalizeOptional(request.getAvatarUrl()))
                .set(GroupChat::getGroupType, type)
                .set(GroupChat::getUpdateTime, now));
        if (affected <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED));
        }
        return GroupChatConverter.toDetailVO(queryGroup(groupId));
    }

    @Override
    public PageResult<GroupChatSessionVO> queryMySessions(Long loginUserId, Integer pageNum, Integer pageSize) {
        userService.queryUserByUserId(loginUserId);
        int validPageNum = PageUtils.getValidPageNum(pageNum);
        int validPageSize = PageUtils.getValidPageSize(pageSize);
        List<GroupChatMember> members = groupChatMemberMapper.selectList(new LambdaQueryWrapper<GroupChatMember>()
                .eq(GroupChatMember::getUserId, loginUserId)
                .eq(GroupChatMember::getStatus, GroupChatMemberStatus.ACTIVE.getCode())
                .ne(GroupChatMember::getDeleteState, Constant.DELETE_STATE_TRUE));
        List<GroupChatSessionVO> all = members.stream()
                .map(member -> buildSession(member, loginUserId))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(GroupChatSessionVO::getLastMessageTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
        return pageList(all, validPageNum, validPageSize);
    }

    @Override
    public PageResult<GroupChatDetailVO> queryPublicGroups(Long loginUserId, Integer pageNum, Integer pageSize) {
        userService.queryUserByUserId(loginUserId);
        int validPageNum = PageUtils.getValidPageNum(pageNum);
        int validPageSize = PageUtils.getValidPageSize(pageSize);
        Page<GroupChat> page = PageUtils.getPage(validPageNum, validPageSize);
        Page<GroupChat> result = groupChatMapper.selectPage(page, new LambdaQueryWrapper<GroupChat>()
                .eq(GroupChat::getGroupType, GroupChatType.PUBLIC.getCode())
                .in(GroupChat::getStatus, List.of(
                        GroupChatStatus.NORMAL.getCode(),
                        GroupChatStatus.FULL.getCode(),
                        GroupChatStatus.OVER_LIMIT_LOCKED.getCode()))
                .ne(GroupChat::getDeleteState, Constant.DELETE_STATE_TRUE)
                .orderByDesc(GroupChat::getUpdateTime));
        List<GroupChatDetailVO> records = result.getRecords().stream()
                .map(this::refreshAndConvert)
                .collect(Collectors.toList());
        return new PageResult<>(records, result.getTotal(), validPageNum, validPageSize,
                result.getPages(), result.hasNext());
    }

    @Override
    public PageResult<GroupChatDetailVO> queryPublicGroupsByOwner(Long loginUserId, Long ownerUserId, Integer pageNum, Integer pageSize) {
        userService.queryUserByUserId(loginUserId);
        userService.queryUserByUserId(ownerUserId);
        int validPageNum = PageUtils.getValidPageNum(pageNum);
        int validPageSize = PageUtils.getValidPageSize(pageSize);
        Page<GroupChat> page = PageUtils.getPage(validPageNum, validPageSize);
        Page<GroupChat> result = groupChatMapper.selectPage(page, new LambdaQueryWrapper<GroupChat>()
                .eq(GroupChat::getOwnerUserId, ownerUserId)
                .eq(GroupChat::getGroupType, GroupChatType.PUBLIC.getCode())
                .in(GroupChat::getStatus, List.of(
                        GroupChatStatus.NORMAL.getCode(),
                        GroupChatStatus.FULL.getCode(),
                        GroupChatStatus.OVER_LIMIT_LOCKED.getCode()))
                .ne(GroupChat::getDeleteState, Constant.DELETE_STATE_TRUE)
                .orderByDesc(GroupChat::getCreateTime));
        List<GroupChatDetailVO> records = result.getRecords().stream()
                .map(this::refreshAndConvert)
                .collect(Collectors.toList());
        return new PageResult<>(records, result.getTotal(), validPageNum, validPageSize,
                result.getPages(), result.hasNext());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GroupChatDetailVO joinPublicGroup(Long groupId, Long loginUserId) {
        User user = userService.queryUserByUserId(loginUserId);
        GroupChat group = refreshGroupLimitStatus(queryGroup(groupId));
        if (!GroupChatType.PUBLIC.getCode().equals(group.getGroupType())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_FORBIDDEN, "私有群只能由群主邀请加入"));
        }
        assertJoinable(group);
        upsertActiveMember(group, user, GroupChatMemberRole.MEMBER.getCode());
        return GroupChatConverter.toDetailVO(refreshGroupLimitStatus(queryGroup(groupId)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GroupChatDetailVO inviteMember(Long groupId, GroupInviteMemberRequest request, Long loginUserId) {
        GroupChat group = refreshGroupLimitStatus(queryGroup(groupId));
        assertOwner(group, loginUserId);
        assertJoinable(group);
        User invitee = userService.queryUserByUserId(request.getInviteeUserId());
        upsertActiveMember(group, invitee, GroupChatMemberRole.MEMBER.getCode());
        return GroupChatConverter.toDetailVO(refreshGroupLimitStatus(queryGroup(groupId)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void leaveGroup(Long groupId, Long loginUserId) {
        GroupChat group = queryGroup(groupId);
        GroupChatMember member = assertActiveMember(groupId, loginUserId);
        if (GroupChatMemberRole.OWNER.getCode().equals(member.getRole())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_FORBIDDEN, "群主需要先解散群聊"));
        }
        updateMemberStatus(member.getId(), GroupChatMemberStatus.LEFT.getCode());
        updateMemberCount(groupId, -1);
        refreshGroupLimitStatus(group);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeMember(Long groupId, Long targetUserId, Long loginUserId) {
        GroupChat group = queryGroup(groupId);
        assertOwner(group, loginUserId);
        GroupChatMember target = assertActiveMember(groupId, targetUserId);
        if (GroupChatMemberRole.OWNER.getCode().equals(target.getRole())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_FORBIDDEN, "不能移除群主"));
        }
        updateMemberStatus(target.getId(), GroupChatMemberStatus.REMOVED.getCode());
        updateMemberCount(groupId, -1);
        refreshGroupLimitStatus(group);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void muteMember(Long groupId, GroupMuteMemberRequest request, Long loginUserId) {
        GroupChat group = queryGroup(groupId);
        assertOwner(group, loginUserId);
        GroupChatMember target = assertActiveMember(groupId, request.getTargetUserId());
        if (GroupChatMemberRole.OWNER.getCode().equals(target.getRole())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_FORBIDDEN, "不能禁言群主"));
        }
        if (request.getMinutes() == null || request.getMinutes() < 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        Date muteUntil = request.getMinutes() == 0
                ? null
                : Date.from(ForumDateTimes.now().toInstant().plus(Duration.ofMinutes(request.getMinutes())));
        groupChatMemberMapper.update(null, new LambdaUpdateWrapper<GroupChatMember>()
                .eq(GroupChatMember::getId, target.getId())
                .set(GroupChatMember::getMuteUntil, muteUntil)
                .set(GroupChatMember::getUpdateTime, ForumDateTimes.now()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void dissolveGroup(Long groupId, Long loginUserId) {
        GroupChat group = queryGroup(groupId);
        assertOwner(group, loginUserId);
        int affected = groupChatMapper.update(null, new LambdaUpdateWrapper<GroupChat>()
                .eq(GroupChat::getId, groupId)
                .ne(GroupChat::getDeleteState, Constant.DELETE_STATE_TRUE)
                .set(GroupChat::getStatus, GroupChatStatus.DISSOLVED.getCode())
                .set(GroupChat::getUpdateTime, ForumDateTimes.now()));
        if (affected <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GroupChatMessageVO sendMessage(SendGroupChatMessageRequest request, Long loginUserId) {
        User sender = userService.queryUserByUserId(loginUserId);
        UserMuteGuard.assertCanPost(sender);
        GroupChat group = refreshGroupLimitStatus(queryGroup(request.getGroupId()));
        assertChatAvailable(group);
        GroupChatMember member = assertActiveMember(group.getId(), loginUserId);
        assertNotMuted(member);
        Byte messageType = normalizeMessageType(request.getMessageType());
        String content = normalizeMessageContent(request.getContent());
        validateGroupMediaContent(messageType, content);
        GroupChatMessage repliedMessage = queryReplyMessage(group.getId(), request.getReplyMessageId());

        Date now = ForumDateTimes.now();
        GroupChatMessage message = new GroupChatMessage();
        message.setGroupId(group.getId());
        message.setSenderUserId(loginUserId);
        message.setMessageType(messageType);
        message.setContent(content);
        if (repliedMessage != null) {
            message.setReplyMessageId(repliedMessage.getId());
            message.setReplySenderName(replySenderName(repliedMessage));
            message.setReplyContent(replyContent(repliedMessage));
        }
        message.setStatus(GroupChatMessageStatus.NORMAL.getCode());
        message.setDeleteState(Constant.DELETE_STATE_FALSE);
        message.setCreateTime(now);
        message.setUpdateTime(now);
        if (groupChatMessageMapper.insert(message) <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_CREATE));
        }
        markRead(group.getId(), message.getId(), loginUserId);
        pushGroupMessage(group, message, loginUserId);
        return GroupChatConverter.toMessageVO(message, sender, loginUserId);
    }

    @Override
    public PageResult<GroupChatMessageVO> queryMessages(Long groupId, Long loginUserId, Integer pageNum, Integer pageSize) {
        assertActiveMember(groupId, loginUserId);
        int validPageNum = PageUtils.getValidPageNum(pageNum);
        int validPageSize = PageUtils.getValidPageSize(pageSize);
        Page<GroupChatMessage> page = PageUtils.getPage(validPageNum, validPageSize);
        Page<GroupChatMessage> result = groupChatMessageMapper.selectPage(page, new LambdaQueryWrapper<GroupChatMessage>()
                .eq(GroupChatMessage::getGroupId, groupId)
                .ne(GroupChatMessage::getMessageType, GroupChatMessageType.SYSTEM.getCode())
                .ne(GroupChatMessage::getDeleteState, Constant.DELETE_STATE_TRUE)
                .orderByAsc(GroupChatMessage::getCreateTime));
        List<GroupChatMessageVO> records = result.getRecords().stream()
                .map(message -> GroupChatConverter.toMessageVO(message, queryUserNullable(message.getSenderUserId()), loginUserId))
                .collect(Collectors.toList());
        return new PageResult<>(records, result.getTotal(), validPageNum, validPageSize,
                result.getPages(), result.hasNext());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markRead(Long groupId, Long messageId, Long loginUserId) {
        GroupChatMember member = assertActiveMember(groupId, loginUserId);
        Long targetMessageId = messageId;
        if (targetMessageId == null || targetMessageId <= 0) {
            GroupChatMessage latest = latestMessage(groupId);
            targetMessageId = latest == null ? 0L : latest.getId();
        }
        if (targetMessageId <= 0 || targetMessageId <= safeLong(member.getLastReadMessageId())) {
            return;
        }
        groupChatMemberMapper.update(null, new LambdaUpdateWrapper<GroupChatMember>()
                .eq(GroupChatMember::getId, member.getId())
                .set(GroupChatMember::getLastReadMessageId, targetMessageId)
                .set(GroupChatMember::getUpdateTime, ForumDateTimes.now()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reportMessage(Long groupId, ReportGroupChatMessageRequest request, Long loginUserId) {
        assertActiveMember(groupId, loginUserId);
        GroupChatMessage message = groupChatMessageMapper.selectOne(new LambdaQueryWrapper<GroupChatMessage>()
                .eq(GroupChatMessage::getId, request.getMessageId())
                .eq(GroupChatMessage::getGroupId, groupId)
                .ne(GroupChatMessage::getDeleteState, Constant.DELETE_STATE_TRUE));
        if (message == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
        if (message.getSenderUserId() == null || Objects.equals(message.getSenderUserId(), loginUserId)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "不能举报该消息"));
        }
        String reason = normalizeReportReason(request.getReason());
        Date now = ForumDateTimes.now();
        GroupChatReport report = new GroupChatReport();
        report.setGroupId(groupId);
        report.setMessageId(message.getId());
        report.setReporterUserId(loginUserId);
        report.setTargetUserId(message.getSenderUserId());
        report.setReason(reason);
        report.setStatus(GroupChatReportStatus.PENDING.getCode());
        report.setDeleteState(Constant.DELETE_STATE_FALSE);
        report.setCreateTime(now);
        report.setUpdateTime(now);
        if (groupChatReportMapper.insert(report) <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_CREATE));
        }
    }

    @Override
    public List<GroupChatMemberVO> queryMembers(Long groupId, Long loginUserId) {
        assertActiveMember(groupId, loginUserId);
        return groupChatMemberMapper.selectList(new LambdaQueryWrapper<GroupChatMember>()
                        .eq(GroupChatMember::getGroupId, groupId)
                        .eq(GroupChatMember::getStatus, GroupChatMemberStatus.ACTIVE.getCode())
                        .ne(GroupChatMember::getDeleteState, Constant.DELETE_STATE_TRUE)
                        .orderByAsc(GroupChatMember::getRole)
                        .orderByAsc(GroupChatMember::getJoinTime))
                .stream()
                .map(member -> {
                    GroupChatMemberVO vo = GroupChatConverter.toMemberVO(member, userService.queryUserByUserId(member.getUserId()));
                    if (vo != null && !Objects.equals(member.getUserId(), loginUserId)) {
                        vo.setRemarkName(null);
                        vo.setNotifyMode(null);
                    }
                    return vo;
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GroupChatMemberVO updateMyRemark(Long groupId, UpdateGroupMemberRemarkRequest request, Long loginUserId) {
        GroupChatMember member = assertActiveMember(groupId, loginUserId);
        String remarkName = normalizeRemarkName(request == null ? null : request.getRemarkName());
        Byte notifyMode = request == null || request.getNotifyMode() == null
                ? safeNotifyMode(member.getNotifyMode())
                : normalizeNotifyMode(request.getNotifyMode());
        int affected = groupChatMemberMapper.update(null, new LambdaUpdateWrapper<GroupChatMember>()
                .eq(GroupChatMember::getId, member.getId())
                .set(GroupChatMember::getRemarkName, remarkName)
                .set(GroupChatMember::getNotifyMode, notifyMode)
                .set(GroupChatMember::getUpdateTime, ForumDateTimes.now()));
        if (affected <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED));
        }
        member.setRemarkName(remarkName);
        member.setNotifyMode(notifyMode);
        member.setUpdateTime(ForumDateTimes.now());
        return GroupChatConverter.toMemberVO(member, userService.queryUserByUserId(loginUserId));
    }

    private GroupChat queryGroup(Long groupId) {
        if (groupId == null || groupId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        GroupChat group = groupChatMapper.selectOne(new LambdaQueryWrapper<GroupChat>()
                .eq(GroupChat::getId, groupId)
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
            throw new ApplicationException(Result.fail(ResultCode.FAILED_FORBIDDEN, "你不是该群成员"));
        }
        return member;
    }

    private void assertOwner(GroupChat group, Long loginUserId) {
        if (!Objects.equals(group.getOwnerUserId(), loginUserId)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_FORBIDDEN, "只有群主可以操作"));
        }
    }

    private void assertCertifiedCreator(User user) {
        if (user == null || !Constant.CREATOR_STATE_CERTIFIED.equals(user.getCreatorState())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_FORBIDDEN, "只有认证创作者可以创建群聊"));
        }
    }

    private void assertCreateQuota(User owner) {
        Long count = groupChatMapper.selectCount(new LambdaQueryWrapper<GroupChat>()
                .eq(GroupChat::getOwnerUserId, owner.getId())
                .in(GroupChat::getStatus, List.of(
                        GroupChatStatus.NORMAL.getCode(),
                        GroupChatStatus.FULL.getCode(),
                        GroupChatStatus.OVER_LIMIT_LOCKED.getCode()))
                .ne(GroupChat::getDeleteState, Constant.DELETE_STATE_TRUE));
        if (count != null && count >= createLimitFor(owner)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_FORBIDDEN, "你的群聊创建数量已达上限"));
        }
    }

    private void assertJoinable(GroupChat group) {
        if (GroupChatStatus.FULL.getCode().equals(group.getStatus())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_FORBIDDEN, "群聊已满员"));
        }
        if (GroupChatStatus.OVER_LIMIT_LOCKED.getCode().equals(group.getStatus())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_FORBIDDEN, "群聊处于超额锁定状态"));
        }
        if (!GroupChatStatus.NORMAL.getCode().equals(group.getStatus())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_FORBIDDEN, "群聊当前不可加入"));
        }
    }

    private void assertChatAvailable(GroupChat group) {
        if (GroupChatStatus.DISSOLVED.getCode().equals(group.getStatus())
                || GroupChatStatus.BANNED.getCode().equals(group.getStatus())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_FORBIDDEN, "群聊当前不可发言"));
        }
    }

    private void assertNotMuted(GroupChatMember member) {
        Date muteUntil = member.getMuteUntil();
        if (muteUntil != null && muteUntil.after(ForumDateTimes.now())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_FORBIDDEN, "你已被群主禁言"));
        }
    }

    private GroupChat refreshGroupLimitStatus(GroupChat group) {
        if (GroupChatStatus.DISSOLVED.getCode().equals(group.getStatus())
                || GroupChatStatus.BANNED.getCode().equals(group.getStatus())) {
            return group;
        }
        User owner = userService.queryUserByUserId(group.getOwnerUserId());
        int limit = memberLimitFor(owner);
        int count = group.getMemberCount() == null ? 0 : group.getMemberCount();
        Byte status = GroupChatStatus.NORMAL.getCode();
        if (count > limit) {
            status = GroupChatStatus.OVER_LIMIT_LOCKED.getCode();
        } else if (count == limit) {
            status = GroupChatStatus.FULL.getCode();
        }
        if (!Objects.equals(group.getMemberLimit(), limit) || !Objects.equals(group.getStatus(), status)) {
            groupChatMapper.update(null, new LambdaUpdateWrapper<GroupChat>()
                    .eq(GroupChat::getId, group.getId())
                    .set(GroupChat::getMemberLimit, limit)
                    .set(GroupChat::getStatus, status)
                    .set(GroupChat::getUpdateTime, ForumDateTimes.now()));
            group.setMemberLimit(limit);
            group.setStatus(status);
        }
        return group;
    }

    private GroupChatDetailVO refreshAndConvert(GroupChat group) {
        return GroupChatConverter.toDetailVO(refreshGroupLimitStatus(group));
    }

    private void upsertActiveMember(GroupChat group, User user, Byte role) {
        GroupChatMember existed = groupChatMemberMapper.selectOne(new LambdaQueryWrapper<GroupChatMember>()
                .eq(GroupChatMember::getGroupId, group.getId())
                .eq(GroupChatMember::getUserId, user.getId())
                .ne(GroupChatMember::getDeleteState, Constant.DELETE_STATE_TRUE));
        if (existed != null && GroupChatMemberStatus.ACTIVE.getCode().equals(existed.getStatus())) {
            return;
        }
        Long latestId = latestMessageId(group.getId());
        Date now = ForumDateTimes.now();
        if (existed == null) {
            GroupChatMember member = new GroupChatMember();
            member.setGroupId(group.getId());
            member.setUserId(user.getId());
            member.setRole(role);
            member.setRemarkName(null);
            member.setNotifyMode(GroupChatNotifyMode.NORMAL.getCode());
            member.setMuteUntil(null);
            member.setLastReadMessageId(latestId);
            member.setJoinTime(now);
            member.setStatus(GroupChatMemberStatus.ACTIVE.getCode());
            member.setDeleteState(Constant.DELETE_STATE_FALSE);
            member.setCreateTime(now);
            member.setUpdateTime(now);
            if (groupChatMemberMapper.insert(member) <= 0) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_CREATE));
            }
        } else {
            groupChatMemberMapper.update(null, new LambdaUpdateWrapper<GroupChatMember>()
                    .eq(GroupChatMember::getId, existed.getId())
                    .set(GroupChatMember::getStatus, GroupChatMemberStatus.ACTIVE.getCode())
                    .set(GroupChatMember::getRole, role)
                    .set(GroupChatMember::getMuteUntil, null)
                    .set(GroupChatMember::getLastReadMessageId, latestId)
                    .set(GroupChatMember::getJoinTime, now)
                    .set(GroupChatMember::getUpdateTime, now));
        }
        updateMemberCount(group.getId(), 1);
    }

    private void updateMemberStatus(Long memberId, Byte status) {
        int affected = groupChatMemberMapper.update(null, new LambdaUpdateWrapper<GroupChatMember>()
                .eq(GroupChatMember::getId, memberId)
                .set(GroupChatMember::getStatus, status)
                .set(GroupChatMember::getUpdateTime, ForumDateTimes.now()));
        if (affected <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED));
        }
    }

    private void updateMemberCount(Long groupId, int delta) {
        GroupChat group = queryGroup(groupId);
        int current = group.getMemberCount() == null ? 0 : group.getMemberCount();
        int next = Math.max(0, current + delta);
        groupChatMapper.update(null, new LambdaUpdateWrapper<GroupChat>()
                .eq(GroupChat::getId, groupId)
                .set(GroupChat::getMemberCount, next)
                .set(GroupChat::getUpdateTime, ForumDateTimes.now()));
    }

    private void appendSystemMessage(Long groupId, String content) {
        Date now = ForumDateTimes.now();
        GroupChatMessage message = new GroupChatMessage();
        message.setGroupId(groupId);
        message.setSenderUserId(null);
        message.setMessageType(GroupChatMessageType.SYSTEM.getCode());
        message.setContent(content);
        message.setStatus(GroupChatMessageStatus.NORMAL.getCode());
        message.setDeleteState(Constant.DELETE_STATE_FALSE);
        message.setCreateTime(now);
        message.setUpdateTime(now);
        groupChatMessageMapper.insert(message);
    }

    private void pushGroupMessage(GroupChat group, GroupChatMessage message, Long senderUserId) {
        List<GroupChatMember> members = groupChatMemberMapper.selectList(new LambdaQueryWrapper<GroupChatMember>()
                .eq(GroupChatMember::getGroupId, group.getId())
                .eq(GroupChatMember::getStatus, GroupChatMemberStatus.ACTIVE.getCode())
                .ne(GroupChatMember::getDeleteState, Constant.DELETE_STATE_TRUE));
        TransactionHooks.afterCommit(() -> {
            for (GroupChatMember member : members) {
                if (Objects.equals(member.getUserId(), senderUserId)) {
                    continue;
                }
                try {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("type", "group_message");
                    payload.put("groupId", group.getId());
                    payload.put("dbMessageId", message.getId());
                    payload.put("fromUserId", senderUserId);
                    payload.put("summary", messageSummary(message));
                    payload.put("notify", shouldNotifyMember(message, member));
                    webSocketPushService.push(member.getUserId(), objectMapper.writeValueAsString(payload));
                } catch (Exception e) {
                    log.warn("群聊 WebSocket 推送失败 groupId={} userId={}", group.getId(), member.getUserId(), e);
                }
            }
        });
    }

    private GroupChatSessionVO buildSession(GroupChatMember member, Long loginUserId) {
        GroupChat group = groupChatMapper.selectById(member.getGroupId());
        if (group == null || Constant.DELETE_STATE_TRUE.equals(group.getDeleteState())) {
            return null;
        }
        refreshGroupLimitStatus(group);
        GroupChatMessage latest = latestMessage(group.getId());
        GroupChatSessionVO vo = new GroupChatSessionVO();
        vo.setGroupId(group.getId());
        vo.setName(sessionDisplayName(group, member));
        vo.setGroupName(group.getName());
        vo.setRemarkName(member.getRemarkName());
        vo.setNotifyMode(safeNotifyMode(member.getNotifyMode()));
        vo.setAvatarUrl(group.getAvatarUrl());
        vo.setIntro(group.getIntro());
        vo.setGroupType(group.getGroupType());
        vo.setStatus(group.getStatus());
        vo.setOwnerUserId(group.getOwnerUserId());
        vo.setMemberCount(group.getMemberCount());
        vo.setMemberLimit(group.getMemberLimit());
        vo.setLastMessage(messageSummary(latest));
        vo.setLastMessageTime(latest == null ? group.getUpdateTime() : latest.getCreateTime());
        vo.setUnreadCount(unreadCount(group.getId(), safeLong(member.getLastReadMessageId()), member, loginUserId));
        return vo;
    }

    private GroupChatMessage latestMessage(Long groupId) {
        return groupChatMessageMapper.selectList(new LambdaQueryWrapper<GroupChatMessage>()
                        .eq(GroupChatMessage::getGroupId, groupId)
                        .ne(GroupChatMessage::getMessageType, GroupChatMessageType.SYSTEM.getCode())
                        .ne(GroupChatMessage::getDeleteState, Constant.DELETE_STATE_TRUE)
                        .orderByDesc(GroupChatMessage::getId)
                        .last("limit 1"))
                .stream()
                .findFirst()
                .orElse(null);
    }

    private Long latestMessageId(Long groupId) {
        GroupChatMessage message = latestMessage(groupId);
        return message == null ? 0L : message.getId();
    }

    private Long unreadCount(Long groupId, Long lastReadMessageId, GroupChatMember member, Long loginUserId) {
        List<GroupChatMessage> unreadMessages = groupChatMessageMapper.selectList(new LambdaQueryWrapper<GroupChatMessage>()
                .eq(GroupChatMessage::getGroupId, groupId)
                .gt(GroupChatMessage::getId, lastReadMessageId)
                .ne(GroupChatMessage::getMessageType, GroupChatMessageType.SYSTEM.getCode())
                .and(w -> w.isNull(GroupChatMessage::getSenderUserId)
                        .or()
                        .ne(GroupChatMessage::getSenderUserId, loginUserId))
                .ne(GroupChatMessage::getDeleteState, Constant.DELETE_STATE_TRUE));
        return unreadMessages.stream()
                .filter(message -> shouldNotifyMember(message, member))
                .count();
    }

    private String messageSummary(GroupChatMessage message) {
        if (message == null) {
            return "暂无消息";
        }
        if (GroupChatMessageType.EMOJI.getCode().equals(message.getMessageType())) {
            return "[表情]";
        }
        if (GroupChatMessageType.IMAGE.getCode().equals(message.getMessageType())) {
            return "[图片]";
        }
        return message.getContent() == null ? "" : message.getContent();
    }

    private String sessionDisplayName(GroupChat group, GroupChatMember member) {
        if (StringUtils.hasText(member.getRemarkName())) {
            return member.getRemarkName();
        }
        return group.getName();
    }

    private boolean shouldNotifyMember(GroupChatMessage message, GroupChatMember member) {
        Byte notifyMode = safeNotifyMode(member.getNotifyMode());
        if (GroupChatNotifyMode.NONE.getCode().equals(notifyMode)) {
            return false;
        }
        if (GroupChatNotifyMode.NORMAL.getCode().equals(notifyMode)) {
            return true;
        }
        if (!GroupChatMessageType.TEXT.getCode().equals(message.getMessageType())) {
            return false;
        }
        String content = message.getContent() == null ? "" : message.getContent();
        if (content.contains("@所有人")) {
            return true;
        }
        User user = userService.queryUserByUserId(member.getUserId());
        return user != null && StringUtils.hasText(user.getNickname()) && content.contains("@" + user.getNickname());
    }

    private User queryUserNullable(Long userId) {
        if (userId == null) {
            return null;
        }
        return userService.queryUserByUserId(userId);
    }

    private Byte normalizeGroupType(Byte groupType) {
        if (GroupChatType.fromCode(groupType) == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "群类型不合法"));
        }
        return groupType;
    }

    private Byte normalizeMessageType(Byte messageType) {
        GroupChatMessageType type = GroupChatMessageType.fromCode(messageType);
        if (type == null || GroupChatMessageType.SYSTEM.equals(type)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "群消息类型不合法"));
        }
        return messageType;
    }

    private Byte normalizeNotifyMode(Byte notifyMode) {
        GroupChatNotifyMode mode = GroupChatNotifyMode.fromCode(notifyMode);
        if (mode == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "提醒模式不合法"));
        }
        return notifyMode;
    }

    private Byte safeNotifyMode(Byte notifyMode) {
        return GroupChatNotifyMode.fromCode(notifyMode) == null
                ? GroupChatNotifyMode.NORMAL.getCode()
                : notifyMode;
    }

    private GroupChatMessage queryReplyMessage(Long groupId, Long replyMessageId) {
        if (replyMessageId == null) {
            return null;
        }
        if (replyMessageId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "回复消息不合法"));
        }
        GroupChatMessage message = groupChatMessageMapper.selectOne(new LambdaQueryWrapper<GroupChatMessage>()
                .eq(GroupChatMessage::getId, replyMessageId)
                .eq(GroupChatMessage::getGroupId, groupId)
                .ne(GroupChatMessage::getDeleteState, Constant.DELETE_STATE_TRUE));
        if (message == null || GroupChatMessageType.SYSTEM.getCode().equals(message.getMessageType())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "回复消息不存在"));
        }
        return message;
    }

    private String replySenderName(GroupChatMessage message) {
        if (message == null || message.getSenderUserId() == null) {
            return "系统";
        }
        return displayName(userService.queryUserByUserId(message.getSenderUserId()));
    }

    private String replyContent(GroupChatMessage message) {
        if (message == null) {
            return "";
        }
        if (GroupChatMessageType.EMOJI.getCode().equals(message.getMessageType())) {
            return "[表情]";
        }
        if (GroupChatMessageType.IMAGE.getCode().equals(message.getMessageType())) {
            return "[图片]";
        }
        String content = message.getContent() == null ? "" : message.getContent().trim();
        return content.length() > 120 ? content.substring(0, 120) : content;
    }

    private String normalizeName(String rawName) {
        String name = normalizeOptional(rawName);
        if (!StringUtils.hasText(name)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "群名称不能为空"));
        }
        if (name.length() > Constant.GROUP_CHAT_NAME_MAX_LEN) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "群名称过长"));
        }
        return name;
    }

    private String normalizeIntro(String rawIntro) {
        String intro = normalizeOptional(rawIntro);
        if (intro != null && intro.length() > Constant.GROUP_CHAT_INTRO_MAX_LEN) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "群简介过长"));
        }
        return intro;
    }

    private String normalizeMessageContent(String rawContent) {
        String content = normalizeOptional(rawContent);
        if (!StringUtils.hasText(content)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "消息内容不能为空"));
        }
        if (content.length() > Constant.GROUP_CHAT_MESSAGE_MAX_LEN) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "消息内容过长"));
        }
        return content;
    }

    private void validateGroupMediaContent(Byte messageType, String content) {
        if (GroupChatMessageType.IMAGE.getCode().equals(messageType)
                && !ossConfig.matchesPublicObjectUrl(content, Constant.OSS_PATH_CHAT_MESSAGE)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_INVALID_OSS_URL));
        }
        if (GroupChatMessageType.EMOJI.getCode().equals(messageType)
                && !ossConfig.matchesPublicObjectUrl(content, Constant.OSS_PATH_CHAT_EMOJI)
                && !ossConfig.matchesPublicObjectUrl(content, Constant.OSS_PATH_CHAT_MESSAGE)
                && !ossConfig.matchesPublicObjectUrl(content, Constant.OSS_PATH_EMOJI_SHOP)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_INVALID_OSS_URL));
        }
    }

    private String normalizeRemarkName(String rawRemarkName) {
        String remarkName = normalizeOptional(rawRemarkName);
        if (remarkName != null && remarkName.length() > 24) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "群备注过长"));
        }
        return remarkName;
    }

    private String normalizeReportReason(String rawReason) {
        String reason = normalizeOptional(rawReason);
        if (!StringUtils.hasText(reason)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "举报原因不能为空"));
        }
        if (reason.length() > 200) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "举报原因过长"));
        }
        return reason;
    }

    private String normalizeOptional(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        return value.isEmpty() ? null : value;
    }

    private int createLimitFor(User user) {
        Byte tier = activeVipTier(user);
        if (Constant.VIP_TIER_MAX.equals(tier)) {
            return Constant.GROUP_CHAT_CREATE_LIMIT_MAX;
        }
        if (Constant.VIP_TIER_PRO.equals(tier)) {
            return Constant.GROUP_CHAT_CREATE_LIMIT_PRO;
        }
        return Constant.GROUP_CHAT_CREATE_LIMIT_FREE;
    }

    private int memberLimitFor(User user) {
        Byte tier = activeVipTier(user);
        if (Constant.VIP_TIER_MAX.equals(tier)) {
            return Constant.GROUP_CHAT_MEMBER_LIMIT_MAX;
        }
        if (Constant.VIP_TIER_PRO.equals(tier)) {
            return Constant.GROUP_CHAT_MEMBER_LIMIT_PRO;
        }
        return Constant.GROUP_CHAT_MEMBER_LIMIT_FREE;
    }

    private Byte activeVipTier(User user) {
        if (user == null || user.getVipTier() == null || Constant.VIP_TIER_FREE.equals(user.getVipTier())) {
            return Constant.VIP_TIER_FREE;
        }
        Date expireAt = user.getVipExpireAt();
        if (expireAt == null || expireAt.after(ForumDateTimes.now())) {
            return user.getVipTier();
        }
        return Constant.VIP_TIER_FREE;
    }

    private String displayName(User user) {
        if (user == null) {
            return "用户";
        }
        if (StringUtils.hasText(user.getNickname())) {
            return user.getNickname();
        }
        return StringUtils.hasText(user.getUsername()) ? user.getUsername() : "用户" + user.getId();
    }

    private Long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private <T> PageResult<T> pageList(List<T> all, int pageNum, int pageSize) {
        long total = all.size();
        long pages = (total + pageSize - 1) / pageSize;
        int startIndex = (pageNum - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, all.size());
        List<T> records = startIndex >= all.size() ? new ArrayList<>() : all.subList(startIndex, endIndex);
        return new PageResult<>(records, total, pageNum, pageSize, pages, pageNum < pages);
    }
}
