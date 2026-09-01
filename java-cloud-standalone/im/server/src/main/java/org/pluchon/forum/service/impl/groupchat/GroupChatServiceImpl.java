package org.pluchon.forum.service.impl.groupchat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.pinyin4j.PinyinHelper;
import org.pluchon.forum.api.UserInternalVO;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.config.OssConfig;
import org.pluchon.forum.common.enums.GroupChatJoinRequestReadState;
import org.pluchon.forum.common.enums.GroupChatMemberRole;
import org.pluchon.forum.common.enums.GroupChatMemberStatus;
import org.pluchon.forum.common.enums.GroupChatJoinRequestStatus;
import org.pluchon.forum.common.enums.GroupChatJoinRequestType;
import org.pluchon.forum.common.enums.GroupChatMessageStatus;
import org.pluchon.forum.common.enums.GroupChatMessageType;
import org.pluchon.forum.common.enums.GroupChatNotifyMode;
import org.pluchon.forum.common.enums.GroupChatStatus;
import org.pluchon.forum.common.enums.GroupChatType;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.utils.ForumDateTimes;
import org.pluchon.forum.common.utils.PageUtils;
import org.pluchon.forum.common.utils.TransactionHooks;
import org.pluchon.forum.converter.GroupChatConverter;
import org.pluchon.forum.entity.db.GroupChat;
import org.pluchon.forum.entity.db.GroupChatJoinRequest;
import org.pluchon.forum.entity.db.GroupChatMember;
import org.pluchon.forum.entity.db.GroupChatMessage;
import org.pluchon.forum.entity.db.GroupChatMessageAlbumImage;
import org.pluchon.forum.entity.dto.groupchat.CreateGroupChatRequest;
import org.pluchon.forum.entity.dto.groupchat.GroupInviteMemberRequest;
import org.pluchon.forum.entity.dto.groupchat.GroupMuteMemberRequest;
import org.pluchon.forum.entity.dto.groupchat.GroupChatAlbumImageRequest;
import org.pluchon.forum.entity.dto.groupchat.SendGroupChatAlbumMessageRequest;
import org.pluchon.forum.entity.dto.groupchat.SendGroupChatMessageRequest;
import org.pluchon.forum.entity.dto.groupchat.UpdateGroupChatRequest;
import org.pluchon.forum.entity.dto.groupchat.UpdateGroupMemberRemarkRequest;
import org.pluchon.forum.entity.dto.groupchat.UpdateGroupMemberRoleRequest;
import org.pluchon.forum.entity.dto.message.SendMessageRequest;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.entity.vo.groupchat.GroupChatDetailVO;
import org.pluchon.forum.entity.vo.groupchat.GroupChatJoinRequestVO;
import org.pluchon.forum.entity.vo.groupchat.GroupChatMemberVO;
import org.pluchon.forum.entity.vo.groupchat.GroupChatMessageVO;
import org.pluchon.forum.entity.vo.groupchat.GroupChatSessionVO;
import org.pluchon.forum.entity.vo.groupchat.GroupChatSessionSearchResponse;
import org.pluchon.forum.mapper.GroupChatMapper;
import org.pluchon.forum.mapper.GroupChatJoinRequestMapper;
import org.pluchon.forum.mapper.GroupChatMemberMapper;
import org.pluchon.forum.mapper.GroupChatMessageMapper;
import org.pluchon.forum.mapper.GroupChatMessageAlbumImageMapper;
import org.pluchon.forum.service.impl.websocket.WebSocketPushService;
import org.pluchon.forum.service.impl.message.OutboundMessageTextAuditService;
import org.pluchon.forum.service.impl.remote.ImUserLookupService;
import org.pluchon.forum.service.impl.remote.ImUserMuteGuard;
import org.pluchon.forum.service.remote.ImShopEmojiAvailabilityService;
import org.pluchon.forum.service.interfaces.groupchat.GroupChatService;
import org.pluchon.forum.service.interfaces.message.MessageService;
import org.pluchon.forum.service.remote.ImAiGatewayService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
// 群聊业务实现
public class GroupChatServiceImpl implements GroupChatService {

    // 私信邀请卡片内容前缀
    // 带关键词搜申请时的候选上限：匹配要用到跨库的群主昵称，没法下推数据库
    private static final int JOIN_REQUEST_SEARCH_CANDIDATE_LIMIT = 500;
    private static final String GROUP_INVITE_CARD_PREFIX = "[[GROUP_INVITE:";

    // 群聊主表 Mapper
    @Autowired
    private ImAiGatewayService imAiGatewayService;

    @Autowired
    private GroupChatMapper groupChatMapper;

    // 群成员 Mapper
    @Autowired
    private GroupChatMemberMapper groupChatMemberMapper;

    // 群加入申请 Mapper
    @Autowired
    private GroupChatJoinRequestMapper groupChatJoinRequestMapper;

    // 群消息 Mapper
    @Autowired
    private GroupChatMessageMapper groupChatMessageMapper;

    // 群聊图集图片 Mapper
    @Autowired
    private GroupChatMessageAlbumImageMapper groupChatMessageAlbumImageMapper;

    // 认证用户查询服务
    @Autowired
    private ImUserLookupService userLookupService;

    // 私信服务
    @Autowired
    private MessageService messageService;

    // WebSocket 推送服务
    @Autowired
    private WebSocketPushService webSocketPushService;

    @Autowired
    private OutboundMessageTextAuditService outboundMessageTextAuditService;

    // OSS 配置
    @Autowired
    private OssConfig ossConfig;

    // JSON 序列化器
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ImShopEmojiAvailabilityService shopEmojiAvailabilityService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GroupChatDetailVO createGroup(CreateGroupChatRequest request, Long loginUserId) {
        UserInternalVO owner = userLookupService.queryUserByUserId(loginUserId);
        assertCertifiedCreator(owner);
        Byte type = normalizeGroupType(request.getGroupType());
        String name = normalizeName(request.getName());
        String intro = normalizeIntro(request.getIntro());
        assertCreateQuota(owner);
        // 群名与简介是公开可见的（公开群还会出现在群列表里），必须过内容审核。
        // 帖子标题、昵称、个人简介都走了这一步，群聊这里原来是漏的。
        assertGroupTextClean(name, intro);

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
        // 只有真的改了才送审，免得每次保存都白跑一次 AI
        assertGroupTextClean(
                Objects.equals(name, group.getName()) ? null : name,
                Objects.equals(intro, group.getIntro()) ? null : intro);
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
        userLookupService.queryUserByUserId(loginUserId);
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
    public PageResult<GroupChatSessionSearchResponse> searchSessions(Long loginUserId, String keyword,
                                                                     Integer pageNum, Integer pageSize) {
        userLookupService.queryUserByUserId(loginUserId);
        int validPageNum = PageUtils.getValidPageNum(pageNum);
        int validPageSize = PageUtils.getValidPageSize(pageSize);
        String validKeyword = keyword == null ? "" : keyword.trim();
        if (!StringUtils.hasText(validKeyword)) {
            return new PageResult<>(new ArrayList<>(), 0L, validPageNum, validPageSize, 0L, false);
        }
        List<Long> activeGroupIds = groupChatMemberMapper.selectList(new LambdaQueryWrapper<GroupChatMember>()
                        .eq(GroupChatMember::getUserId, loginUserId)
                        .eq(GroupChatMember::getStatus, GroupChatMemberStatus.ACTIVE.getCode())
                        .ne(GroupChatMember::getDeleteState, Constant.DELETE_STATE_TRUE))
                .stream()
                .map(GroupChatMember::getGroupId)
                .distinct()
                .toList();
        if (activeGroupIds.isEmpty()) {
            return new PageResult<>(new ArrayList<>(), 0L, validPageNum, validPageSize, 0L, false);
        }
        List<GroupChatMessage> matches = groupChatMessageMapper.selectList(new LambdaQueryWrapper<GroupChatMessage>()
                .in(GroupChatMessage::getGroupId, activeGroupIds)
                .eq(GroupChatMessage::getMessageType, GroupChatMessageType.TEXT.getCode())
                .eq(GroupChatMessage::getStatus, GroupChatMessageStatus.NORMAL.getCode())
                .ne(GroupChatMessage::getDeleteState, Constant.DELETE_STATE_TRUE)
                .like(GroupChatMessage::getContent, validKeyword)
                .orderByDesc(GroupChatMessage::getCreateTime)
                .orderByDesc(GroupChatMessage::getId));
        Map<Long, GroupChatSessionSearchResponse> byGroup = new LinkedHashMap<>();
        for (GroupChatMessage message : matches) {
            byGroup.putIfAbsent(message.getGroupId(), new GroupChatSessionSearchResponse(
                    message.getGroupId(), message.getId(), message.getContent(), message.getCreateTime()));
        }
        return pageList(new ArrayList<>(byGroup.values()), validPageNum, validPageSize);
    }

    @Override
    public PageResult<GroupChatDetailVO> queryPublicGroups(Long loginUserId, Integer pageNum, Integer pageSize) {
        userLookupService.queryUserByUserId(loginUserId);
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
        Map<Long, UserInternalVO> ownerUsers = queryOwnerUsers(result.getRecords());
        List<GroupChatDetailVO> records = result.getRecords().stream()
                .map(group -> refreshAndConvert(group, null, ownerUsers.get(group.getOwnerUserId())))
                .collect(Collectors.toList());
        fillViewerRelations(records, loginUserId);
        attachOwnerUsers(records, ownerUsers);
        return new PageResult<>(records, result.getTotal(), validPageNum, validPageSize,
                result.getPages(), result.hasNext());
    }

    @Override
    public PageResult<GroupChatDetailVO> queryPublicGroupsByOwner(Long loginUserId, Long ownerUserId, Integer pageNum, Integer pageSize) {
        userLookupService.queryUserByUserId(loginUserId);
        userLookupService.queryUserByUserId(ownerUserId);
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
        Map<Long, UserInternalVO> ownerUsers = queryOwnerUsers(result.getRecords());
        List<GroupChatDetailVO> records = result.getRecords().stream()
                .map(group -> refreshAndConvert(group, null, ownerUsers.get(group.getOwnerUserId())))
                .collect(Collectors.toList());
        fillViewerRelations(records, loginUserId);
        attachOwnerUsers(records, ownerUsers);
        return new PageResult<>(records, result.getTotal(), validPageNum, validPageSize,
                result.getPages(), result.hasNext());
    }

    @Override
    public PageResult<GroupChatDetailVO> queryMyOwnedGroups(Long loginUserId, String keyword, Integer pageNum, Integer pageSize) {
        userLookupService.queryUserByUserId(loginUserId);
        int validPageNum = PageUtils.getValidPageNum(pageNum);
        int validPageSize = PageUtils.getValidPageSize(pageSize);
        Page<GroupChat> page = PageUtils.getPage(validPageNum, validPageSize);
        LambdaQueryWrapper<GroupChat> wrapper = new LambdaQueryWrapper<GroupChat>()
                .eq(GroupChat::getOwnerUserId, loginUserId)
                .in(GroupChat::getStatus, List.of(
                        GroupChatStatus.NORMAL.getCode(),
                        GroupChatStatus.FULL.getCode(),
                        GroupChatStatus.OVER_LIMIT_LOCKED.getCode()))
                .ne(GroupChat::getDeleteState, Constant.DELETE_STATE_TRUE)
                .orderByDesc(GroupChat::getCreateTime);
        String normalizedKeyword = normalizeOptional(keyword);
        if (StringUtils.hasText(normalizedKeyword)) {
            wrapper.like(GroupChat::getName, normalizedKeyword);
        }
        Page<GroupChat> result = groupChatMapper.selectPage(page, wrapper);
        List<GroupChatDetailVO> records = result.getRecords().stream()
                .map(group -> refreshAndConvert(group, loginUserId))
                .collect(Collectors.toList());
        return new PageResult<>(records, result.getTotal(), validPageNum, validPageSize,
                result.getPages(), result.hasNext());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GroupChatJoinRequestVO joinPublicGroup(Long groupId, Long loginUserId) {
        UserInternalVO user = userLookupService.queryUserByUserId(loginUserId);
        GroupChat group = refreshGroupLimitStatus(queryGroup(groupId));
        if (!GroupChatType.PUBLIC.getCode().equals(group.getGroupType())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_FORBIDDEN, "私有群只能由群主邀请加入"));
        }
        if (isActiveMember(group.getId(), loginUserId)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_FORBIDDEN, "你已经在群聊中"));
        }
        assertJoinable(group);
        GroupChatJoinRequest request = createOrReuseJoinRequest(group, user.getId(), user.getId(),
                GroupChatJoinRequestType.APPLY.getCode());
        return toJoinRequestVO(request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GroupChatJoinRequestVO inviteMember(Long groupId, GroupInviteMemberRequest request, Long loginUserId) {
        GroupChat group = refreshGroupLimitStatus(queryGroup(groupId));
        assertOwner(group, loginUserId);
        assertJoinable(group);
        UserInternalVO invitee = userLookupService.queryUserByUserId(request.getInviteeUserId());
        if (Objects.equals(invitee.getId(), loginUserId)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "不能邀请自己"));
        }
        if (isActiveMember(group.getId(), invitee.getId())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_FORBIDDEN, "对方已在群聊中"));
        }
        GroupChatJoinRequest joinRequest = createOrReuseJoinRequest(group, invitee.getId(), loginUserId,
                GroupChatJoinRequestType.INVITE.getCode());
        sendGroupInvitePrivateMessage(group, invitee.getId(), loginUserId, joinRequest.getId());
        return toJoinRequestVO(joinRequest);
    }

    @Override
    public GroupChatJoinRequestVO queryJoinRequest(Long requestId, Long loginUserId) {
        GroupChatJoinRequest request = queryJoinRequestEntity(requestId);
        if (!Objects.equals(request.getTargetUserId(), loginUserId)
                && !Objects.equals(request.getInitiatorUserId(), loginUserId)
                && !Objects.equals(request.getOwnerUserId(), loginUserId)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_UNAUTHORIZED));
        }
        return toJoinRequestVO(request);
    }

    @Override
    public PageResult<GroupChatJoinRequestVO> queryReceivedJoinRequests(Long loginUserId, String keyword,
                                                                        Integer pageNum, Integer pageSize) {
        userLookupService.queryUserByUserId(loginUserId);
        int validPageNum = PageUtils.getValidPageNum(pageNum);
        int validPageSize = PageUtils.getValidPageSize(pageSize);
        LambdaQueryWrapper<GroupChatJoinRequest> wrapper = new LambdaQueryWrapper<GroupChatJoinRequest>()
                .eq(GroupChatJoinRequest::getOwnerUserId, loginUserId)
                .eq(GroupChatJoinRequest::getRequestType, GroupChatJoinRequestType.APPLY.getCode())
                .ne(GroupChatJoinRequest::getDeleteState, Constant.DELETE_STATE_TRUE)
                .orderByDesc(GroupChatJoinRequest::getId);
        return pageJoinRequests(wrapper, keyword, validPageNum, validPageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markReceivedJoinRequestsRead(Long loginUserId) {
        userLookupService.queryUserByUserId(loginUserId);
        groupChatJoinRequestMapper.update(null, new LambdaUpdateWrapper<GroupChatJoinRequest>()
                .eq(GroupChatJoinRequest::getOwnerUserId, loginUserId)
                .eq(GroupChatJoinRequest::getRequestType, GroupChatJoinRequestType.APPLY.getCode())
                .eq(GroupChatJoinRequest::getOwnerReadState, GroupChatJoinRequestReadState.UNREAD.getCode())
                .ne(GroupChatJoinRequest::getDeleteState, Constant.DELETE_STATE_TRUE)
                .set(GroupChatJoinRequest::getOwnerReadState, GroupChatJoinRequestReadState.READ.getCode())
                .set(GroupChatJoinRequest::getUpdateTime, ForumDateTimes.now()));
    }

    @Override
    public PageResult<GroupChatJoinRequestVO> queryAppliedJoinRequests(Long loginUserId, String keyword,
                                                                       Integer pageNum,
                                                                       Integer pageSize) {
        userLookupService.queryUserByUserId(loginUserId);
        int validPageNum = PageUtils.getValidPageNum(pageNum);
        int validPageSize = PageUtils.getValidPageSize(pageSize);
        LambdaQueryWrapper<GroupChatJoinRequest> wrapper = new LambdaQueryWrapper<GroupChatJoinRequest>()
                        .eq(GroupChatJoinRequest::getTargetUserId, loginUserId)
                        .eq(GroupChatJoinRequest::getRequestType, GroupChatJoinRequestType.APPLY.getCode())
                        .ne(GroupChatJoinRequest::getStatus, GroupChatJoinRequestStatus.PENDING.getCode())
                        .ne(GroupChatJoinRequest::getDeleteState, Constant.DELETE_STATE_TRUE)
                        .orderByDesc(GroupChatJoinRequest::getId);
        return pageJoinRequests(wrapper, keyword, validPageNum, validPageSize);
    }

    /**
     * 申请列表的分页。
     *
     * <p>没有关键词时直接让数据库分页——原来是把该用户的全部申请查出来，
     * 在内存里转成 VO（每条都要回查群与用户）再切片，申请一多就很吃亏。
     *
     * <p>带关键词时只能内存过滤：匹配的是群名与群主昵称，后者在 auth 库，
     * 跨库没法下推。这条路给候选集封了个上限，不至于无限扩散。
     */
    private PageResult<GroupChatJoinRequestVO> pageJoinRequests(
            LambdaQueryWrapper<GroupChatJoinRequest> wrapper, String keyword, int pageNum, int pageSize) {
        if (keyword == null || keyword.isBlank()) {
            Page<GroupChatJoinRequest> page = groupChatJoinRequestMapper.selectPage(
                    new Page<>(pageNum, pageSize), wrapper);
            List<GroupChatJoinRequestVO> rows = page.getRecords().stream()
                    .map(this::toJoinRequestVO)
                    .collect(Collectors.toList());
            return new PageResult<>(rows, page.getTotal(), pageNum, pageSize,
                    page.getPages(), page.hasNext());
        }
        List<GroupChatJoinRequestVO> filtered = groupChatJoinRequestMapper
                .selectList(wrapper.last("LIMIT " + JOIN_REQUEST_SEARCH_CANDIDATE_LIMIT)).stream()
                .map(this::toJoinRequestVO)
                .filter(vo -> joinRequestMatches(vo, keyword))
                .collect(Collectors.toList());
        return pageJoinRequestVOs(filtered, pageNum, pageSize);
    }

    private boolean joinRequestMatches(GroupChatJoinRequestVO vo, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String query = keyword.trim().toLowerCase(java.util.Locale.ROOT);
        String groupName = vo.getGroup() == null || vo.getGroup().getName() == null
                ? "" : vo.getGroup().getName().toLowerCase(java.util.Locale.ROOT);
        String ownerName = vo.getOwnerUser() == null || vo.getOwnerUser().getNickname() == null
                ? "" : vo.getOwnerUser().getNickname().toLowerCase(java.util.Locale.ROOT);
        return groupName.contains(query) || ownerName.contains(query);
    }

    private PageResult<GroupChatJoinRequestVO> pageJoinRequestVOs(
            List<GroupChatJoinRequestVO> rows, int pageNum, int pageSize) {
        int fromIndex = Math.min((pageNum - 1) * pageSize, rows.size());
        int toIndex = Math.min(fromIndex + pageSize, rows.size());
        long total = rows.size();
        long pages = total == 0 ? 0 : (total + pageSize - 1) / pageSize;
        return new PageResult<>(rows.subList(fromIndex, toIndex), total, pageNum, pageSize,
                pages, toIndex < rows.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAppliedJoinRequestsRead(Long loginUserId) {
        userLookupService.queryUserByUserId(loginUserId);
        groupChatJoinRequestMapper.update(null, new LambdaUpdateWrapper<GroupChatJoinRequest>()
                .eq(GroupChatJoinRequest::getTargetUserId, loginUserId)
                .eq(GroupChatJoinRequest::getRequestType, GroupChatJoinRequestType.APPLY.getCode())
                .ne(GroupChatJoinRequest::getStatus, GroupChatJoinRequestStatus.PENDING.getCode())
                .eq(GroupChatJoinRequest::getApplicantReadState, GroupChatJoinRequestReadState.UNREAD.getCode())
                .ne(GroupChatJoinRequest::getDeleteState, Constant.DELETE_STATE_TRUE)
                .set(GroupChatJoinRequest::getApplicantReadState, GroupChatJoinRequestReadState.READ.getCode())
                .set(GroupChatJoinRequest::getUpdateTime, ForumDateTimes.now()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GroupChatJoinRequestVO approveJoinRequest(Long requestId, Long loginUserId) {
        GroupChatJoinRequest request = queryJoinRequestEntity(requestId);
        assertJoinRequestType(request, GroupChatJoinRequestType.APPLY.getCode());
        GroupChat group = refreshGroupLimitStatus(queryGroup(request.getGroupId()));
        assertOwner(group, loginUserId);
        assertPendingJoinRequest(request);
        if (isActiveMember(group.getId(), request.getTargetUserId())) {
            updateJoinRequestStatus(request.getId(), GroupChatJoinRequestStatus.OBSOLETE.getCode(), loginUserId);
            return toJoinRequestVO(queryJoinRequestEntity(requestId));
        }
        assertJoinable(group);
        UserInternalVO target = userLookupService.queryUserByUserId(request.getTargetUserId());
        upsertActiveMember(group, target, GroupChatMemberRole.MEMBER.getCode());
        updateJoinRequestStatus(request.getId(), GroupChatJoinRequestStatus.APPROVED.getCode(), loginUserId);
        return toJoinRequestVO(queryJoinRequestEntity(requestId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GroupChatJoinRequestVO rejectJoinRequest(Long requestId, Long loginUserId) {
        GroupChatJoinRequest request = queryJoinRequestEntity(requestId);
        assertJoinRequestType(request, GroupChatJoinRequestType.APPLY.getCode());
        GroupChat group = queryGroup(request.getGroupId());
        assertOwner(group, loginUserId);
        assertPendingJoinRequest(request);
        if (isActiveMember(group.getId(), request.getTargetUserId())) {
            updateJoinRequestStatus(request.getId(), GroupChatJoinRequestStatus.OBSOLETE.getCode(), loginUserId);
            return toJoinRequestVO(queryJoinRequestEntity(requestId));
        }
        updateJoinRequestStatus(request.getId(), GroupChatJoinRequestStatus.REJECTED.getCode(), loginUserId);
        return toJoinRequestVO(queryJoinRequestEntity(requestId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GroupChatJoinRequestVO acceptInvitation(Long requestId, Long loginUserId) {
        GroupChatJoinRequest request = queryJoinRequestEntity(requestId);
        assertJoinRequestType(request, GroupChatJoinRequestType.INVITE.getCode());
        if (!Objects.equals(request.getTargetUserId(), loginUserId)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_UNAUTHORIZED));
        }
        assertPendingJoinRequest(request);
        GroupChat group = refreshGroupLimitStatus(queryGroup(request.getGroupId()));
        assertJoinable(group);
        UserInternalVO target = userLookupService.queryUserByUserId(loginUserId);
        upsertActiveMember(group, target, GroupChatMemberRole.MEMBER.getCode());
        updateJoinRequestStatus(request.getId(), GroupChatJoinRequestStatus.APPROVED.getCode(), loginUserId);
        return toJoinRequestVO(queryJoinRequestEntity(requestId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GroupChatJoinRequestVO rejectInvitation(Long requestId, Long loginUserId) {
        GroupChatJoinRequest request = queryJoinRequestEntity(requestId);
        assertJoinRequestType(request, GroupChatJoinRequestType.INVITE.getCode());
        if (!Objects.equals(request.getTargetUserId(), loginUserId)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_UNAUTHORIZED));
        }
        assertPendingJoinRequest(request);
        updateJoinRequestStatus(request.getId(), GroupChatJoinRequestStatus.REJECTED.getCode(), loginUserId);
        return toJoinRequestVO(queryJoinRequestEntity(requestId));
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
    public void updateMemberRole(Long groupId, UpdateGroupMemberRoleRequest request, Long loginUserId) {
        GroupChat group = queryGroup(groupId);
        assertOwner(group, loginUserId);
        GroupChatMember target = assertActiveMember(groupId, request.getTargetUserId());
        if (GroupChatMemberRole.OWNER.getCode().equals(target.getRole())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_FORBIDDEN, "不能修改群主角色"));
        }
        Byte targetRole = request.getRole();
        if (!GroupChatMemberRole.MEMBER.getCode().equals(targetRole)
                && !GroupChatMemberRole.ADMIN.getCode().equals(targetRole)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "群成员角色不合法"));
        }
        groupChatMemberMapper.update(null, new LambdaUpdateWrapper<GroupChatMember>()
                .eq(GroupChatMember::getId, target.getId())
                .set(GroupChatMember::getRole, targetRole)
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
        UserInternalVO sender = userLookupService.queryUserByUserId(loginUserId);
        ImUserMuteGuard.assertCanPost(sender);
        GroupChat group = refreshGroupLimitStatus(queryGroup(request.getGroupId()));
        assertChatAvailable(group);
        GroupChatMember member = assertActiveMember(group.getId(), loginUserId);
        assertNotMuted(member);
        Byte messageType = normalizeMessageType(request.getMessageType());
        String content = normalizeMessageContent(request.getContent());
        validateGroupMediaContent(messageType, content);
        if (GroupChatMessageType.EMOJI.getCode().equals(messageType)
                && ossConfig.matchesPublicObjectUrl(content, Constant.OSS_PATH_EMOJI_SHOP)) {
            shopEmojiAvailabilityService.assertAvailable(loginUserId, request.getEmojiShopId(), content);
        }
        if (GroupChatMessageType.TEXT.getCode().equals(messageType) && content.contains("@所有人")) {
            assertGroupManager(group, member);
        }
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
        if (GroupChatMessageType.TEXT.getCode().equals(messageType)) {
            scheduleGroupTextAudit(group, message, loginUserId);
        }
        return GroupChatConverter.toMessageVO(message, sender, loginUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GroupChatMessageVO sendAlbum(SendGroupChatAlbumMessageRequest request, Long loginUserId) {
        validateGroupAlbumRequest(request);
        UserInternalVO sender = userLookupService.queryUserByUserId(loginUserId);
        ImUserMuteGuard.assertCanPost(sender);
        GroupChat group = refreshGroupLimitStatus(queryGroup(request.getGroupId()));
        assertChatAvailable(group);
        GroupChatMember member = assertActiveMember(group.getId(), loginUserId);
        assertNotMuted(member);
        GroupChatMessage repliedMessage = queryReplyMessage(group.getId(), request.getReplyMessageId());
        String content = normalizeOptional(request.getContent());
        if (content != null && content.length() > Constant.GROUP_CHAT_MESSAGE_MAX_LEN) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "图集说明文字过长"));
        }

        Date now = ForumDateTimes.now();
        GroupChatMessage message = new GroupChatMessage();
        message.setGroupId(group.getId());
        message.setSenderUserId(loginUserId);
        message.setMessageType(GroupChatMessageType.ALBUM.getCode());
        message.setContent(content == null ? "" : content);
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

        for (int index = 0; index < request.getImages().size(); index++) {
            GroupChatAlbumImageRequest source = request.getImages().get(index);
            GroupChatMessageAlbumImage image = new GroupChatMessageAlbumImage();
            image.setMessageId(message.getId());
            image.setMediaUrl(source.getMediaUrl().trim());
            image.setMediaMime(normalizeOptional(source.getMediaMime()));
            image.setMediaSize(source.getMediaSize());
            image.setMediaWidth(source.getMediaWidth());
            image.setMediaHeight(source.getMediaHeight());
            image.setSortOrder(index);
            image.setCreateTime(now);
            image.setUpdateTime(now);
            image.setDeleteState(Constant.DELETE_STATE_FALSE);
            if (groupChatMessageAlbumImageMapper.insert(image) <= 0) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_CREATE));
            }
        }
        markRead(group.getId(), message.getId(), loginUserId);
        pushGroupMessage(group, message, loginUserId);
        scheduleGroupTextAudit(group, message, loginUserId);
        return GroupChatConverter.toMessageVO(message, sender, loginUserId, queryAlbumImages(message.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recallMessage(Long messageId, Long loginUserId) {
        GroupChatMessage message = groupChatMessageMapper.selectOne(new LambdaQueryWrapper<GroupChatMessage>()
                .eq(GroupChatMessage::getId, messageId)
                .ne(GroupChatMessage::getDeleteState, Constant.DELETE_STATE_TRUE));
        if (message == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
        GroupChat group = queryGroup(message.getGroupId());
        assertActiveMember(message.getGroupId(), loginUserId);
        boolean senderRecall = Objects.equals(message.getSenderUserId(), loginUserId);
        boolean ownerModerationRecall = Objects.equals(group.getOwnerUserId(), loginUserId) && !senderRecall;
        if (!senderRecall && !ownerModerationRecall) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_UNAUTHORIZED));
        }
        if (GroupChatMessageStatus.RECALLED.getCode().equals(message.getStatus())) {
            return;
        }
        if (GroupChatMessageType.SYSTEM.getCode().equals(message.getMessageType())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "系统消息不能撤回"));
        }
        long secondsElapsed = (System.currentTimeMillis() - message.getCreateTime().getTime()) / 1000;
        if (senderRecall && secondsElapsed > Constant.MESSAGE_RECALL_WINDOW_SECONDS) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED, "超过撤回时间窗口（2分钟），无法撤回"));
        }
        int affected = groupChatMessageMapper.update(null, new LambdaUpdateWrapper<GroupChatMessage>()
                .eq(GroupChatMessage::getId, messageId)
                .eq(GroupChatMessage::getStatus, GroupChatMessageStatus.NORMAL.getCode())
                .ne(GroupChatMessage::getDeleteState, Constant.DELETE_STATE_TRUE)
                .set(GroupChatMessage::getStatus, GroupChatMessageStatus.RECALLED.getCode())
                .set(GroupChatMessage::getUpdateTime, ForumDateTimes.now()));
        if (affected <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED, "撤回失败，可能已超时或无权限"));
        }
        pushGroupRecall(group, messageId, loginUserId, message.getSenderUserId());
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
                .and(w -> w.ne(GroupChatMessage::getStatus, GroupChatMessageStatus.AUDIT_FAILED.getCode())
                        .or(o -> o.eq(GroupChatMessage::getStatus, GroupChatMessageStatus.AUDIT_FAILED.getCode())
                                .eq(GroupChatMessage::getSenderUserId, loginUserId)))
                .orderByAsc(GroupChatMessage::getCreateTime));
        Map<Long, List<GroupChatMessageAlbumImage>> albumImages = queryAlbumImages(result.getRecords());
        List<GroupChatMessageVO> records = result.getRecords().stream()
                .map(message -> GroupChatConverter.toMessageVO(message, queryUserNullable(message.getSenderUserId()),
                        loginUserId, albumImages.get(message.getId())))
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
    public PageResult<GroupChatMemberVO> queryMembers(Long groupId,
                                                       Long loginUserId,
                                                       String keyword,
                                                       String sortMode,
                                                       Integer pageNum,
                                                       Integer pageSize) {
        assertActiveMember(groupId, loginUserId);
        int validPageNum = PageUtils.getValidPageNum(pageNum);
        int validPageSize = PageUtils.getValidPageSize(pageSize);
        String normalizedKeyword = normalizeOptional(keyword);
        List<GroupChatMemberVO> members = groupChatMemberMapper.selectList(new LambdaQueryWrapper<GroupChatMember>()
                        .eq(GroupChatMember::getGroupId, groupId)
                        .eq(GroupChatMember::getStatus, GroupChatMemberStatus.ACTIVE.getCode())
                        .ne(GroupChatMember::getDeleteState, Constant.DELETE_STATE_TRUE)
                        .orderByAsc(GroupChatMember::getJoinTime))
                .stream()
                .sorted(Comparator.comparingInt(member -> roleSortWeight(member.getRole())))
                .map(member -> {
                    GroupChatMemberVO vo = GroupChatConverter.toMemberVO(member, userLookupService.queryUserByUserId(member.getUserId()));
                    if (vo != null && !Objects.equals(member.getUserId(), loginUserId)) {
                        vo.setRemarkName(null);
                        vo.setNotifyMode(null);
                    }
                    return vo;
                })
                .filter(Objects::nonNull)
                .filter(member -> memberNicknameMatches(member, normalizedKeyword))
                .collect(Collectors.toList());
        Comparator<GroupChatMemberVO> comparator = "nicknameInitial".equalsIgnoreCase(sortMode)
                ? nicknameComparator(normalizedKeyword)
                : Comparator.comparingInt((GroupChatMemberVO member) -> roleSortWeight(member.getRole()))
                        .thenComparing(GroupChatMemberVO::getJoinTime, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(GroupChatMemberVO::getId, Comparator.nullsLast(Comparator.naturalOrder()));
        members.sort(comparator);
        return pageList(members, validPageNum, validPageSize);
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
        return GroupChatConverter.toMemberVO(member, userLookupService.queryUserByUserId(loginUserId));
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

    private boolean isActiveMember(Long groupId, Long userId) {
        if (groupId == null || userId == null) {
            return false;
        }
        Long count = groupChatMemberMapper.selectCount(new LambdaQueryWrapper<GroupChatMember>()
                .eq(GroupChatMember::getGroupId, groupId)
                .eq(GroupChatMember::getUserId, userId)
                .eq(GroupChatMember::getStatus, GroupChatMemberStatus.ACTIVE.getCode())
                .ne(GroupChatMember::getDeleteState, Constant.DELETE_STATE_TRUE));
        return count != null && count > 0;
    }

    private void assertOwner(GroupChat group, Long loginUserId) {
        if (!Objects.equals(group.getOwnerUserId(), loginUserId)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_FORBIDDEN, "只有群主可以操作"));
        }
    }

    private void assertGroupManager(GroupChat group, GroupChatMember member) {
        if (Objects.equals(group.getOwnerUserId(), member.getUserId())
                || GroupChatMemberRole.OWNER.getCode().equals(member.getRole())
                || GroupChatMemberRole.ADMIN.getCode().equals(member.getRole())) {
            return;
        }
        throw new ApplicationException(Result.fail(ResultCode.FAILED_FORBIDDEN, "只有群主或管理员可以@所有人"));
    }

    private int roleSortWeight(Byte role) {
        if (GroupChatMemberRole.OWNER.getCode().equals(role)) {
            return 0;
        }
        if (GroupChatMemberRole.ADMIN.getCode().equals(role)) {
            return 1;
        }
        return 2;
    }

    private boolean memberNicknameMatches(GroupChatMemberVO member, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        return memberNickname(member).toLowerCase().contains(keyword.toLowerCase());
    }

    private Comparator<GroupChatMemberVO> nicknameComparator(String keyword) {
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.toLowerCase() : null;
        return Comparator
                .comparingInt((GroupChatMemberVO member) -> normalizedKeyword != null
                        && memberNickname(member).equalsIgnoreCase(normalizedKeyword) ? 0 : 1)
                .thenComparing(member -> pinyinSortKey(memberNickname(member)))
                .thenComparing(this::memberNickname, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(GroupChatMemberVO::getId, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private String memberNickname(GroupChatMemberVO member) {
        if (member == null || member.getUser() == null || !StringUtils.hasText(member.getUser().getNickname())) {
            return "";
        }
        return member.getUser().getNickname().trim();
    }

    private String pinyinSortKey(String nickname) {
        StringBuilder key = new StringBuilder();
        for (char ch : nickname.toCharArray()) {
            String[] syllables = PinyinHelper.toHanyuPinyinStringArray(ch);
            if (syllables != null && syllables.length > 0) {
                key.append(syllables[0].replaceAll("[0-9]", ""));
            } else {
                key.append(Character.toLowerCase(ch));
            }
        }
        return key.toString();
    }

    private void assertCertifiedCreator(UserInternalVO user) {
        if (user == null || !Constant.CREATOR_STATE_CERTIFIED.equals(user.getCreatorState())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_FORBIDDEN, "只有认证创作者可以创建群聊"));
        }
    }

    /**
     * 群名与群简介的内容审核。
     *
     * <p>审核不可用时放行而不是卡住创建：宁可漏一条也不该让人建不了群，
     * 与站内其它文本审核的取舍一致。
     */
    private void assertGroupTextClean(String name, String intro) {
        assertTextClean(name, "群名称");
        assertTextClean(intro, "群简介");
    }

    private void assertTextClean(String text, String label) {
        if (!StringUtils.hasText(text)) {
            return;
        }
        String violation;
        try {
            violation = imAiGatewayService.validateText(text);
        } catch (Exception e) {
            log.warn("{}审核调用失败，放行", label, e);
            return;
        }
        if (violation != null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED,
                    label + "未通过审核：" + violation));
        }
    }

    private void assertCreateQuota(UserInternalVO owner) {
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
        return refreshGroupLimitStatus(group, userLookupService.queryUserByUserId(group.getOwnerUserId()));
    }

    private GroupChat refreshGroupLimitStatus(GroupChat group, UserInternalVO owner) {
        if (GroupChatStatus.DISSOLVED.getCode().equals(group.getStatus())
                || GroupChatStatus.BANNED.getCode().equals(group.getStatus())) {
            return group;
        }
        int limit = memberLimitFor(owner);
        int count = group.getMemberCount() == null ? 0 : group.getMemberCount();
        Byte status = GroupChatStatus.NORMAL.getCode();
        if (count > limit) {
            status = GroupChatStatus.OVER_LIMIT_LOCKED.getCode();
        } else if (count == limit) {
            status = GroupChatStatus.FULL.getCode();
        }
        if (!Objects.equals(group.getMemberLimit(), limit) || !Objects.equals(group.getStatus(), status)) {
            // 不要动 updateTime：公开群列表按它倒序，纠正一次状态就会把这个群顶到最前面。
            // MyBatis-Plus 的自动填充也要绕开，这里显式锁住原值
            groupChatMapper.update(null, new LambdaUpdateWrapper<GroupChat>()
                    .eq(GroupChat::getId, group.getId())
                    .set(GroupChat::getMemberLimit, limit)
                    .set(GroupChat::getStatus, status)
                    .setSql("update_time = update_time"));
            group.setMemberLimit(limit);
            group.setStatus(status);
        }
        return group;
    }

    private GroupChatDetailVO refreshAndConvert(GroupChat group) {
        return GroupChatConverter.toDetailVO(refreshGroupLimitStatus(group));
    }

    private GroupChatDetailVO refreshAndConvert(GroupChat group, Long loginUserId) {
        GroupChatDetailVO vo = GroupChatConverter.toDetailVO(refreshGroupLimitStatus(group));
        fillViewerRelation(vo, loginUserId);
        return vo;
    }

    private GroupChatDetailVO refreshAndConvert(GroupChat group, Long loginUserId, UserInternalVO owner) {
        GroupChatDetailVO vo = GroupChatConverter.toDetailVO(refreshGroupLimitStatus(group, owner));
        fillViewerRelation(vo, loginUserId);
        return vo;
    }

    // 列表场景：把逐条的 isActiveMember / latestPendingJoinRequest 合并成两次批量查询
    private void fillViewerRelations(List<GroupChatDetailVO> vos, Long loginUserId) {
        if (vos == null || vos.isEmpty() || loginUserId == null) {
            return;
        }
        List<Long> groupIds = vos.stream().map(GroupChatDetailVO::getId)
                .filter(Objects::nonNull).distinct().toList();
        if (groupIds.isEmpty()) {
            return;
        }
        Set<Long> joinedGroupIds = groupChatMemberMapper.selectList(new LambdaQueryWrapper<GroupChatMember>()
                        .in(GroupChatMember::getGroupId, groupIds)
                        .eq(GroupChatMember::getUserId, loginUserId)
                        .eq(GroupChatMember::getStatus, GroupChatMemberStatus.ACTIVE.getCode())
                        .ne(GroupChatMember::getDeleteState, Constant.DELETE_STATE_TRUE)
                        .select(GroupChatMember::getGroupId))
                .stream().map(GroupChatMember::getGroupId).collect(Collectors.toSet());
        Map<Long, GroupChatJoinRequest> pendingByGroup = new HashMap<>();
        groupChatJoinRequestMapper.selectList(new LambdaQueryWrapper<GroupChatJoinRequest>()
                        .in(GroupChatJoinRequest::getGroupId, groupIds)
                        .eq(GroupChatJoinRequest::getTargetUserId, loginUserId)
                        .eq(GroupChatJoinRequest::getStatus, GroupChatJoinRequestStatus.PENDING.getCode())
                        .ne(GroupChatJoinRequest::getDeleteState, Constant.DELETE_STATE_TRUE)
                        .orderByDesc(GroupChatJoinRequest::getId))
                // 同一个群保留 id 最大的那条，与逐条查询时的 limit 1 语义一致
                .forEach(row -> pendingByGroup.putIfAbsent(row.getGroupId(), row));
        for (GroupChatDetailVO vo : vos) {
            vo.setCurrentUserJoined(joinedGroupIds.contains(vo.getId()));
            GroupChatJoinRequest latest = pendingByGroup.get(vo.getId());
            vo.setCurrentUserRequestStatus(latest == null ? null : latest.getStatus());
            vo.setCurrentUserRequestId(latest == null ? null : latest.getId());
        }
    }

    private void fillViewerRelation(GroupChatDetailVO vo, Long loginUserId) {
        if (vo == null || loginUserId == null) {
            return;
        }
        vo.setCurrentUserJoined(isActiveMember(vo.getId(), loginUserId));
        GroupChatJoinRequest latest = latestPendingJoinRequest(vo.getId(), loginUserId);
        vo.setCurrentUserRequestStatus(latest == null ? null : latest.getStatus());
        vo.setCurrentUserRequestId(latest == null ? null : latest.getId());
    }

    private GroupChatJoinRequest createOrReuseJoinRequest(GroupChat group, Long targetUserId, Long initiatorUserId, Byte requestType) {
        GroupChatJoinRequest pending = groupChatJoinRequestMapper.selectList(new LambdaQueryWrapper<GroupChatJoinRequest>()
                        .eq(GroupChatJoinRequest::getGroupId, group.getId())
                        .eq(GroupChatJoinRequest::getTargetUserId, targetUserId)
                        .eq(GroupChatJoinRequest::getRequestType, requestType)
                        .eq(GroupChatJoinRequest::getStatus, GroupChatJoinRequestStatus.PENDING.getCode())
                        .ne(GroupChatJoinRequest::getDeleteState, Constant.DELETE_STATE_TRUE)
                        .orderByDesc(GroupChatJoinRequest::getId)
                        .last("limit 1"))
                .stream()
                .findFirst()
                .orElse(null);
        if (pending != null) {
            return pending;
        }
        Date now = ForumDateTimes.now();
        GroupChatJoinRequest request = new GroupChatJoinRequest();
        request.setGroupId(group.getId());
        request.setTargetUserId(targetUserId);
        request.setInitiatorUserId(initiatorUserId);
        request.setOwnerUserId(group.getOwnerUserId());
        request.setRequestType(requestType);
        request.setStatus(GroupChatJoinRequestStatus.PENDING.getCode());
        request.setOwnerReadState(GroupChatJoinRequestReadState.UNREAD.getCode());
        request.setApplicantReadState(GroupChatJoinRequestReadState.READ.getCode());
        request.setHandledByUserId(null);
        request.setHandleTime(null);
        request.setDeleteState(Constant.DELETE_STATE_FALSE);
        request.setCreateTime(now);
        request.setUpdateTime(now);
        if (groupChatJoinRequestMapper.insert(request) <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_CREATE));
        }
        return request;
    }

    private GroupChatJoinRequest latestPendingJoinRequest(Long groupId, Long targetUserId) {
        return groupChatJoinRequestMapper.selectList(new LambdaQueryWrapper<GroupChatJoinRequest>()
                        .eq(GroupChatJoinRequest::getGroupId, groupId)
                        .eq(GroupChatJoinRequest::getTargetUserId, targetUserId)
                        .eq(GroupChatJoinRequest::getStatus, GroupChatJoinRequestStatus.PENDING.getCode())
                        .ne(GroupChatJoinRequest::getDeleteState, Constant.DELETE_STATE_TRUE)
                        .orderByDesc(GroupChatJoinRequest::getId)
                        .last("limit 1"))
                .stream()
                .findFirst()
                .orElse(null);
    }

    private GroupChatJoinRequest queryJoinRequestEntity(Long requestId) {
        if (requestId == null || requestId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        GroupChatJoinRequest request = groupChatJoinRequestMapper.selectOne(new LambdaQueryWrapper<GroupChatJoinRequest>()
                .eq(GroupChatJoinRequest::getId, requestId)
                .ne(GroupChatJoinRequest::getDeleteState, Constant.DELETE_STATE_TRUE));
        if (request == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
        return request;
    }

    private GroupChatJoinRequestVO toJoinRequestVO(GroupChatJoinRequest request) {
        if (request == null) {
            return null;
        }
        GroupChatJoinRequestVO vo = new GroupChatJoinRequestVO();
        vo.setId(request.getId());
        vo.setGroup(refreshAndConvert(queryGroup(request.getGroupId()), request.getTargetUserId()));
        vo.setTargetUser(org.pluchon.forum.converter.ImUserBriefConverter.toBrief(userLookupService.queryUserByUserId(request.getTargetUserId())));
        vo.setInitiatorUser(org.pluchon.forum.converter.ImUserBriefConverter.toBrief(userLookupService.queryUserByUserId(request.getInitiatorUserId())));
        vo.setOwnerUser(org.pluchon.forum.converter.ImUserBriefConverter.toBrief(userLookupService.queryUserByUserId(request.getOwnerUserId())));
        vo.setRequestType(request.getRequestType());
        vo.setStatus(request.getStatus());
        vo.setOwnerReadState(request.getOwnerReadState());
        vo.setApplicantReadState(request.getApplicantReadState());
        vo.setTargetJoined(isActiveMember(request.getGroupId(), request.getTargetUserId()));
        vo.setCreateTime(request.getCreateTime());
        vo.setHandleTime(request.getHandleTime());
        return vo;
    }

    private void updateJoinRequestStatus(Long requestId, Byte status, Long handledByUserId) {
        GroupChatJoinRequest request = queryJoinRequestEntity(requestId);
        boolean notifyApplicant = GroupChatJoinRequestType.APPLY.getCode().equals(request.getRequestType());
        int affected = groupChatJoinRequestMapper.update(null, new LambdaUpdateWrapper<GroupChatJoinRequest>()
                .eq(GroupChatJoinRequest::getId, requestId)
                .eq(GroupChatJoinRequest::getStatus, GroupChatJoinRequestStatus.PENDING.getCode())
                .set(GroupChatJoinRequest::getStatus, status)
                .set(GroupChatJoinRequest::getOwnerReadState, GroupChatJoinRequestReadState.READ.getCode())
                .set(notifyApplicant, GroupChatJoinRequest::getApplicantReadState,
                        GroupChatJoinRequestReadState.UNREAD.getCode())
                .set(GroupChatJoinRequest::getHandledByUserId, handledByUserId)
                .set(GroupChatJoinRequest::getHandleTime, ForumDateTimes.now())
                .set(GroupChatJoinRequest::getUpdateTime, ForumDateTimes.now()));
        if (affected <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED));
        }
    }

    private void assertPendingJoinRequest(GroupChatJoinRequest request) {
        if (!GroupChatJoinRequestStatus.PENDING.getCode().equals(request.getStatus())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_FORBIDDEN, "该请求已处理"));
        }
    }

    private void assertJoinRequestType(GroupChatJoinRequest request, Byte requestType) {
        if (!Objects.equals(request.getRequestType(), requestType)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "请求类型不合法"));
        }
    }

    private void sendGroupInvitePrivateMessage(GroupChat group, Long inviteeUserId, Long inviterUserId, Long requestId) {
        SendMessageRequest messageRequest = new SendMessageRequest();
        messageRequest.setReceiveUserId(inviteeUserId);
        messageRequest.setContent(GROUP_INVITE_CARD_PREFIX + requestId + "]]");
        messageService.send(messageRequest, inviterUserId);
    }

    private void upsertActiveMember(GroupChat group, UserInternalVO user, Byte role) {
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

    private void scheduleGroupTextAudit(GroupChat group, GroupChatMessage message, Long sendUserId) {
        if (group == null || message == null || !org.springframework.util.StringUtils.hasText(message.getContent())) {
            return;
        }
        List<Long> memberIds = groupChatMemberMapper.selectList(new LambdaQueryWrapper<GroupChatMember>()
                        .eq(GroupChatMember::getGroupId, group.getId())
                        .eq(GroupChatMember::getStatus, GroupChatMemberStatus.ACTIVE.getCode())
                        .ne(GroupChatMember::getDeleteState, Constant.DELETE_STATE_TRUE))
                .stream()
                .map(GroupChatMember::getUserId)
                .toList();
        outboundMessageTextAuditService.scheduleGroupTextAudit(
                message.getId(), group.getId(), message.getContent(), sendUserId, memberIds);
    }

    private void pushGroupMessage(GroupChat group, GroupChatMessage message, Long senderUserId) {
        pushGroupMessage(group, message, senderUserId, true);
    }

    private void pushGroupMessage(GroupChat group, GroupChatMessage message, Long senderUserId, boolean skipSender) {
        List<GroupChatMember> members = groupChatMemberMapper.selectList(new LambdaQueryWrapper<GroupChatMember>()
                .eq(GroupChatMember::getGroupId, group.getId())
                .eq(GroupChatMember::getStatus, GroupChatMemberStatus.ACTIVE.getCode())
                .ne(GroupChatMember::getDeleteState, Constant.DELETE_STATE_TRUE));
        UserInternalVO sender = queryUserNullable(senderUserId);
        String senderNickname = sender == null ? "群成员" : sender.getNickname();
        TransactionHooks.afterCommit(() -> {
            for (GroupChatMember member : members) {
                boolean isSender = Objects.equals(member.getUserId(), senderUserId);
                if (skipSender && isSender) {
                    continue;
                }
                try {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("type", "group_message");
                    payload.put("groupId", group.getId());
                    payload.put("dbMessageId", message.getId());
                    payload.put("fromUserId", senderUserId);
                    payload.put("senderNickname", senderNickname);
                    payload.put("summary", messageSummary(message));
                    payload.put("mentioned", !isSender && isMemberMentioned(message, member));
                    payload.put("notify", !isSender && shouldNotifyMember(message, member));
                    webSocketPushService.push(member.getUserId(), objectMapper.writeValueAsString(payload));
                } catch (Exception e) {
                    log.warn("群聊 WebSocket 推送失败 groupId={} userId={}", group.getId(), member.getUserId(), e);
                }
            }
        });
    }

    private void pushGroupRecall(GroupChat group,
                                 Long messageId,
                                 Long operatorUserId,
                                 Long originalSenderUserId) {
        List<GroupChatMember> members = groupChatMemberMapper.selectList(new LambdaQueryWrapper<GroupChatMember>()
                .eq(GroupChatMember::getGroupId, group.getId())
                .eq(GroupChatMember::getStatus, GroupChatMemberStatus.ACTIVE.getCode())
                .ne(GroupChatMember::getDeleteState, Constant.DELETE_STATE_TRUE));
        TransactionHooks.afterCommit(() -> {
            for (GroupChatMember member : members) {
                try {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("type", "group_message_recalled");
                    payload.put("groupId", group.getId());
                    payload.put("messageId", messageId);
                    payload.put("operatorUserId", operatorUserId);
                    payload.put("originalSenderUserId", originalSenderUserId);
                    webSocketPushService.push(member.getUserId(), objectMapper.writeValueAsString(payload));
                } catch (Exception exception) {
                    log.warn("群消息撤回 WebSocket 推送失败 groupId={} userId={}", group.getId(), member.getUserId(), exception);
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
        vo.setMyRole(member.getRole());
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
        vo.setCreateTime(group.getCreateTime());
        return vo;
    }

    private Map<Long, UserInternalVO> queryOwnerUsers(List<GroupChat> groups) {
        if (groups == null || groups.isEmpty()) {
            return Map.of();
        }
        List<Long> ownerIds = groups.stream()
                .map(GroupChat::getOwnerUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return userLookupService.queryUsersByIds(ownerIds).stream()
                .filter(user -> user != null && user.getId() != null)
                .collect(Collectors.toMap(UserInternalVO::getId, user -> user, (left, right) -> left));
    }

    private void attachOwnerUsers(List<GroupChatDetailVO> records, Map<Long, UserInternalVO> owners) {
        if (records == null || records.isEmpty() || owners == null || owners.isEmpty()) {
            return;
        }
        records.forEach(record -> {
            UserInternalVO owner = owners.get(record.getOwnerUserId());
            if (owner != null) {
                record.setOwnerUser(org.pluchon.forum.converter.ImUserBriefConverter.toBrief(owner));
            }
        });
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
                .eq(GroupChatMessage::getStatus, GroupChatMessageStatus.NORMAL.getCode())
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
        if (GroupChatMessageStatus.RECALLED.getCode().equals(message.getStatus())) {
            return "[消息已被撤回]";
        }
        if (GroupChatMessageType.EMOJI.getCode().equals(message.getMessageType())) {
            return "[表情符号]";
        }
        if (GroupChatMessageType.IMAGE.getCode().equals(message.getMessageType())) {
            return "[图片]";
        }
        if (GroupChatMessageType.ALBUM.getCode().equals(message.getMessageType())) {
            String albumText = message.getContent() == null ? "" : message.getContent().trim();
            return albumText.isEmpty() ? "[图集]" : "[图集] " + albumText;
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
        return isMemberMentioned(message, member);
    }

    private boolean isMemberMentioned(GroupChatMessage message, GroupChatMember member) {
        if (!GroupChatMessageType.TEXT.getCode().equals(message.getMessageType())) {
            return false;
        }
        String content = message.getContent() == null ? "" : message.getContent();
        if (content.contains("@所有人")) {
            return true;
        }
        UserInternalVO user = queryUserNullable(member.getUserId());
        return user != null && StringUtils.hasText(user.getNickname())
                && mentionsNickname(content, user.getNickname());
    }

    /**
     * 文本里是否真的 @ 了这个昵称。
     *
     * <p>原来是 content.contains("@" + 昵称)，昵称互为前缀时会误伤——
     * 「@小明明」里含有「@小明」，于是叫「小明」的人也会收到提醒。
     * 这里要求 @昵称 后面是结尾或分隔符；前端插入时本来就会补一个空格。
     */
    private boolean mentionsNickname(String content, String nickname) {
        String token = "@" + nickname;
        int from = 0;
        while (true) {
            int index = content.indexOf(token, from);
            if (index < 0) {
                return false;
            }
            int after = index + token.length();
            if (after >= content.length() || isMentionBoundary(content.charAt(after))) {
                return true;
            }
            from = index + 1;
        }
    }

    private boolean isMentionBoundary(char ch) {
        return Character.isWhitespace(ch) || ch == '@'
                || ch == ',' || ch == '，' || ch == '.' || ch == '。'
                || ch == '!' || ch == '！' || ch == '?' || ch == '？'
                || ch == ':' || ch == '：' || ch == ';' || ch == '；';
    }

    private UserInternalVO queryUserNullable(Long userId) {
        if (userId == null) {
            return null;
        }
        return userLookupService.queryUserByUserId(userId);
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
        return displayName(userLookupService.queryUserByUserId(message.getSenderUserId()));
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
        if (GroupChatMessageType.ALBUM.getCode().equals(message.getMessageType())) {
            return "[图集]";
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

    private void validateGroupAlbumRequest(SendGroupChatAlbumMessageRequest request) {
        if (request.getImages() == null || request.getImages().isEmpty() || request.getImages().size() > 10) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "图集需包含1至10张图片"));
        }
        for (GroupChatAlbumImageRequest image : request.getImages()) {
            if (image == null || !StringUtils.hasText(image.getMediaUrl())
                    || !ossConfig.matchesPublicObjectUrl(image.getMediaUrl().trim(), Constant.OSS_PATH_CHAT_MESSAGE)) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_INVALID_OSS_URL));
            }
        }
    }

    private List<GroupChatMessageAlbumImage> queryAlbumImages(Long messageId) {
        if (messageId == null) {
            return List.of();
        }
        return groupChatMessageAlbumImageMapper.selectList(new LambdaQueryWrapper<GroupChatMessageAlbumImage>()
                .eq(GroupChatMessageAlbumImage::getMessageId, messageId)
                .ne(GroupChatMessageAlbumImage::getDeleteState, Constant.DELETE_STATE_TRUE)
                .orderByAsc(GroupChatMessageAlbumImage::getSortOrder));
    }

    private Map<Long, List<GroupChatMessageAlbumImage>> queryAlbumImages(List<GroupChatMessage> messages) {
        List<Long> albumMessageIds = messages.stream()
                .filter(message -> GroupChatMessageType.ALBUM.getCode().equals(message.getMessageType()))
                .map(GroupChatMessage::getId)
                .toList();
        if (albumMessageIds.isEmpty()) {
            return Map.of();
        }
        return groupChatMessageAlbumImageMapper.selectList(new LambdaQueryWrapper<GroupChatMessageAlbumImage>()
                        .in(GroupChatMessageAlbumImage::getMessageId, albumMessageIds)
                        .ne(GroupChatMessageAlbumImage::getDeleteState, Constant.DELETE_STATE_TRUE)
                        .orderByAsc(GroupChatMessageAlbumImage::getMessageId)
                        .orderByAsc(GroupChatMessageAlbumImage::getSortOrder))
                .stream()
                .collect(Collectors.groupingBy(GroupChatMessageAlbumImage::getMessageId,
                        LinkedHashMap::new, Collectors.toList()));
    }

    private String normalizeRemarkName(String rawRemarkName) {
        String remarkName = normalizeOptional(rawRemarkName);
        if (remarkName != null && remarkName.length() > 24) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "群备注过长"));
        }
        return remarkName;
    }

    private String normalizeOptional(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        return value.isEmpty() ? null : value;
    }

    // 群聊上限不再随群主 VIP 变化，见 ForumBusinessConstants 上的说明
    private int createLimitFor(UserInternalVO user) {
        return Constant.GROUP_CHAT_CREATE_LIMIT;
    }

    private int memberLimitFor(UserInternalVO user) {
        return Constant.GROUP_CHAT_MEMBER_LIMIT;
    }

    private String displayName(UserInternalVO user) {
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
