package org.pluchon.forum.service.impl.message;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.config.OssConfig;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.converter.MessageConverter;
import org.pluchon.forum.common.utils.ForumDateTimes;
import org.pluchon.forum.common.enums.GroupChatMemberStatus;
import org.pluchon.forum.common.enums.GroupChatMessageStatus;
import org.pluchon.forum.common.enums.GroupChatMessageType;
import org.pluchon.forum.common.enums.MessageStatus;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.mq.ForumProducer;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.service.impl.websocket.WebSocketPushService;
import org.pluchon.forum.service.impl.message.OutboundMessageTextAuditService;
import org.pluchon.forum.common.utils.PageUtils;
import org.pluchon.forum.common.utils.TransactionHooks;
import org.pluchon.forum.entity.db.Message;
import org.pluchon.forum.entity.db.MessageAlbumImage;
import org.pluchon.forum.entity.db.MessageSessionVisibility;
import org.pluchon.forum.entity.db.GroupChat;
import org.pluchon.forum.entity.db.GroupChatJoinRequest;
import org.pluchon.forum.entity.db.GroupChatMember;
import org.pluchon.forum.entity.db.GroupChatMessage;
import org.pluchon.forum.api.UserInternalVO;
import org.pluchon.forum.entity.db.UserChatEmoji;
import org.pluchon.forum.entity.dto.message.FavoriteEmojiRequest;
import org.pluchon.forum.entity.dto.message.AlbumImageRequest;
import org.pluchon.forum.entity.dto.message.MessageReplyRequest;
import org.pluchon.forum.entity.dto.message.SendAlbumMessageRequest;
import org.pluchon.forum.entity.dto.message.SendImageMessageRequest;
import org.pluchon.forum.entity.dto.message.SendMessageRequest;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.entity.vo.message.MessageDetailResponse;
import org.pluchon.forum.entity.vo.message.MessageSessionResponse;
import org.pluchon.forum.entity.vo.message.MessageSessionSearchResponse;
import org.pluchon.forum.entity.vo.message.MessageVO;
import org.pluchon.forum.entity.vo.message.UserChatEmojiResponse;
import org.pluchon.forum.entity.vo.mq.MessageNotifyMqVO;
import org.pluchon.forum.mapper.MessageMapper;
import org.pluchon.forum.mapper.MessageAlbumImageMapper;
import org.pluchon.forum.mapper.MessageSessionVisibilityMapper;
import org.pluchon.forum.mapper.GroupChatMapper;
import org.pluchon.forum.mapper.GroupChatJoinRequestMapper;
import org.pluchon.forum.mapper.GroupChatMemberMapper;
import org.pluchon.forum.mapper.GroupChatMessageMapper;
import org.pluchon.forum.mapper.UserChatEmojiMapper;
import org.pluchon.forum.service.impl.message.guard.MessageSendContext;
import org.pluchon.forum.service.impl.message.guard.MessageSendGuardChain;
import org.pluchon.forum.service.impl.message.guard.MessageSendGuardResult;
import org.pluchon.forum.service.interfaces.message.MessageService;
import org.pluchon.forum.service.impl.remote.ImUserLookupService;
import org.pluchon.forum.service.remote.ImShopEmojiAvailabilityService;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
public class MessageServiceImpl implements MessageService {

    // 私信发送限频：窗口长度与窗口内的条数上限
    private static final long SEND_RATE_WINDOW_SECONDS = 10L;
    private static final long SEND_RATE_MAX_IN_WINDOW = 20L;

    // 最多能置顶多少个会话
    private static final int MAX_PINNED_SESSIONS = 10;

    // 私信群邀请卡片内容格式
    private static final Pattern GROUP_INVITE_CARD_PATTERN = Pattern.compile("^\\[\\[GROUP_INVITE:(\\d+)]]$");

    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private MessageAlbumImageMapper messageAlbumImageMapper;

    @Autowired
    private MessageSessionVisibilityMapper messageSessionVisibilityMapper;

    @Autowired
    private UserChatEmojiMapper userChatEmojiMapper;

    @Autowired
    private GroupChatMapper groupChatMapper;

    @Autowired
    private GroupChatJoinRequestMapper groupChatJoinRequestMapper;

    @Autowired
    private GroupChatMemberMapper groupChatMemberMapper;

    @Autowired
    private GroupChatMessageMapper groupChatMessageMapper;

    @Autowired
    private ImUserLookupService userService;

    @Autowired
    private ForumProducer forumProducer;

    @Autowired
    private LocalMessageOutboxService localMessageOutboxService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OssConfig ossConfig;

    @Autowired
    private WebSocketPushService webSocketPushService;

    @Autowired
    private ImShopEmojiAvailabilityService shopEmojiAvailabilityService;

    @Autowired
    private OutboundMessageTextAuditService outboundMessageTextAuditService;

    @Autowired
    private MessageSendGuardChain messageSendGuardChain;

    
    // 发送 / 撤回 / 回复
    

    // 对方批量已读了我发给 TA 的消息时，通知我 发送方 以便前端刷新「已读」
    private void pushPeerReadAll(Long messageAuthorId, Long readerUserId) {
        try {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", "message_read");
            m.put("readerUserId", readerUserId);
            String payload = objectMapper.writeValueAsString(m);
            webSocketPushService.push(messageAuthorId, payload);
        } catch (Exception e) {
            log.warn("WebSocket 推送已读回执失败 author={} reader={}", messageAuthorId, readerUserId, e);
        }
    }

    private void stampMessageTimes(Message message) {
        Date now = ForumDateTimes.now();
        message.setCreateTime(now);
        message.setUpdateTime(now);
    }

    private void pushPeerReadOne(Long messageAuthorId, Long readerUserId, Long dbMessageId) {
        try {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", "message_read");
            m.put("readerUserId", readerUserId);
            m.put("messageId", dbMessageId);
            String payload = objectMapper.writeValueAsString(m);
            webSocketPushService.push(messageAuthorId, payload);
        } catch (Exception e) {
            log.warn("WebSocket 推送单条已读失败", e);
        }
    }
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MessageVO send(SendMessageRequest req, Long sendUserId) {
        checkMessageSendGuard(MessageSendContext.text(req, sendUserId));
        Long receiveUserId = req.getReceiveUserId();
        Message newMessage = new Message();
        newMessage.setPostUserId(sendUserId);
        newMessage.setReceiveUserId(receiveUserId);
        newMessage.setMessageType(Constant.MESSAGE_TYPE_TEXT);
        newMessage.setContent(req.getContent());
        newMessage.setState(Constant.MESSAGE_STATE_UNREAD);
        stampMessageTimes(newMessage);
        if (messageMapper.insert(newMessage) <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_CREATE));
        }
        Set<Long> restoredUsers = restoreHiddenPair(sendUserId, receiveUserId);
        publishNotify(newMessage.getId(), sendUserId, receiveUserId, sessionSummary(newMessage));
        incrementUnreadCount(receiveUserId, 1L);
        invalidateSessionCache(sendUserId, receiveUserId);
        restoredUsers.forEach(this::invalidateUnreadCache);
        outboundMessageTextAuditService.schedulePrivateTextAudit(
                newMessage.getId(), newMessage.getContent(), sendUserId, receiveUserId);
        return MessageConverter.toMessageVO(queryMessageByMessageId(newMessage.getId()));
    }

    // 发送图片/GIF 消息. 与 send 完全独立: 不接受 content, 由 mediaUrl 表达图片地址. mediaUrl 须为本站 OSS 下 {@link Constant#OSS_PATH_CHAT_MESSAGE} 聊天临时上传 或 {@link Constant#OSS_PATH_CHAT_EMOJI} 表情收藏库 之一，二者均经上传接口 + AI 审核写入.
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MessageVO sendImage(SendImageMessageRequest req, Long sendUserId) {
        if (req != null && ossConfig.matchesPublicObjectUrl(req.getMediaUrl(), Constant.OSS_PATH_EMOJI_SHOP)) {
            shopEmojiAvailabilityService.assertAvailable(sendUserId, req.getEmojiShopId(), req.getMediaUrl());
        }
        checkMessageSendGuard(MessageSendContext.image(req, sendUserId));
        Long receiveUserId = req.getReceiveUserId();

        Message newMessage = new Message();
        newMessage.setPostUserId(sendUserId);
        newMessage.setReceiveUserId(receiveUserId);
        newMessage.setMessageType(req.getMessageType());
        newMessage.setContent(normalizeAlbumContent(req.getContent()));
        newMessage.setMediaUrl(req.getMediaUrl());
        newMessage.setMediaMime(req.getMediaMime());
        newMessage.setMediaSize(req.getMediaSize());
        newMessage.setMediaWidth(req.getMediaWidth());
        newMessage.setMediaHeight(req.getMediaHeight());
        newMessage.setState(Constant.MESSAGE_STATE_UNREAD);
        stampMessageTimes(newMessage);
        if (messageMapper.insert(newMessage) <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_CREATE));
        }
        Set<Long> restoredUsers = restoreHiddenPair(sendUserId, receiveUserId);
        publishNotify(newMessage.getId(), sendUserId, receiveUserId, sessionSummary(newMessage));
        incrementUnreadCount(receiveUserId, 1L);
        invalidateSessionCache(sendUserId, receiveUserId);
        restoredUsers.forEach(this::invalidateUnreadCache);
        // 附带的文字同样要走后审，否则单图会成为绕过文本审核的口子
        outboundMessageTextAuditService.schedulePrivateTextAudit(
                newMessage.getId(), newMessage.getContent(), sendUserId, receiveUserId);
        return MessageConverter.toMessageVO(queryMessageByMessageId(newMessage.getId()));
    }

    // 发送图集私信，主消息和图片明细在同一事务内完成
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MessageVO sendAlbum(SendAlbumMessageRequest req, Long sendUserId) {
        validateAlbumRequest(req);
        checkMessageSendGuard(MessageSendContext.album(req, sendUserId));

        Message message = new Message();
        message.setPostUserId(sendUserId);
        message.setReceiveUserId(req.getReceiveUserId());
        message.setMessageType(Constant.MESSAGE_TYPE_ALBUM);
        message.setContent(normalizeAlbumContent(req.getContent()));
        message.setState(Constant.MESSAGE_STATE_UNREAD);
        stampMessageTimes(message);
        if (messageMapper.insert(message) <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_CREATE));
        }

        Date now = ForumDateTimes.now();
        for (int index = 0; index < req.getImages().size(); index++) {
            AlbumImageRequest source = req.getImages().get(index);
            MessageAlbumImage image = new MessageAlbumImage();
            image.setMessageId(message.getId());
            image.setMediaUrl(source.getMediaUrl().trim());
            image.setMediaMime(source.getMediaMime());
            image.setMediaSize(source.getMediaSize());
            image.setMediaWidth(source.getMediaWidth());
            image.setMediaHeight(source.getMediaHeight());
            image.setSortOrder(index);
            image.setCreateTime(now);
            image.setUpdateTime(now);
            image.setDeleteState(Constant.DELETE_STATE_FALSE);
            if (messageAlbumImageMapper.insert(image) <= 0) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_CREATE));
            }
        }

        Long receiveUserId = req.getReceiveUserId();
        Set<Long> restoredUsers = restoreHiddenPair(sendUserId, receiveUserId);
        publishNotify(message.getId(), sendUserId, receiveUserId, sessionSummary(message));
        incrementUnreadCount(receiveUserId, 1L);
        invalidateSessionCache(sendUserId, receiveUserId);
        restoredUsers.forEach(this::invalidateUnreadCache);
        outboundMessageTextAuditService.schedulePrivateTextAudit(
                message.getId(), message.getContent(), sendUserId, receiveUserId);
        return MessageConverter.toMessageVO(message, queryAlbumImages(message.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replyMessage(MessageReplyRequest req, Long sendUserId) {
        checkMessageSendGuard(MessageSendContext.reply(req, sendUserId));
        Long receiveId = req.getReceiveId();
        Message reply = new Message();
        reply.setPostUserId(sendUserId);
        reply.setReceiveUserId(receiveId);
        reply.setMessageType(Constant.MESSAGE_TYPE_TEXT);
        reply.setContent(req.getContent());
        reply.setState(Constant.MESSAGE_STATE_UNREAD);
        stampMessageTimes(reply);
        if (messageMapper.insert(reply) <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED));
        }
        Set<Long> restoredUsers = restoreHiddenPair(sendUserId, receiveId);
        publishNotify(reply.getId(), sendUserId, receiveId, sessionSummary(reply));
        incrementUnreadCount(receiveId, 1L);
        invalidateSessionCache(sendUserId, receiveId);
        restoredUsers.forEach(this::invalidateUnreadCache);
        outboundMessageTextAuditService.schedulePrivateTextAudit(
                reply.getId(), reply.getContent(), sendUserId, receiveId);
    }

    // 撤回私信消息
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recallMessage(Long messageId, Long loginUserId) {
        Message msg = queryMessageByMessageId(messageId);
        if (Objects.equals(msg.getState(), Constant.MESSAGE_STATE_RECALLED)) {
            return;
        }
        if (!msg.getPostUserId().equals(loginUserId)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_UNAUTHORIZED));
        }
        String content = msg.getContent() == null ? "" : msg.getContent().trim();
        if (GROUP_INVITE_CARD_PATTERN.matcher(content).matches()) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "入群邀请不能撤回"));
        }
        long secondsElapsed = (System.currentTimeMillis() - msg.getCreateTime().getTime()) / 1000;
        if (secondsElapsed > Constant.MESSAGE_RECALL_WINDOW_SECONDS) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED, "超过撤回时间窗口（2分钟），无法撤回"));
        }
        int result = messageMapper.update(null, new LambdaUpdateWrapper<Message>()
                .eq(Message::getId, messageId)
                .eq(Message::getPostUserId, loginUserId)
                .ne(Message::getDeleteState, Constant.DELETE_STATE_TRUE)
                .ne(Message::getState, Constant.MESSAGE_STATE_RECALLED)
                .set(Message::getState, Constant.MESSAGE_STATE_RECALLED));
        if (result <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED, "撤回失败，可能已超时或无权限"));
        }
        if (Objects.equals(msg.getState(), Constant.MESSAGE_STATE_UNREAD)) {
            decrementUnreadCount(msg.getReceiveUserId(), 1L);
        }
        invalidateSessionCache(loginUserId, msg.getReceiveUserId());
        pushMessageRecalled(messageId, loginUserId, msg.getReceiveUserId());
    }

    
    // 状态修改 / 未读数
    
    @Override
    public Long selectMessageUnRead(Long userId) {
        userService.queryUserByUserId(userId);
        String cacheKey = Constant.REDIS_KEY_MESSAGE_UNREAD_COUNT + userId;
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return Long.valueOf(cached);
        }
        LambdaQueryWrapper<Message> query = new LambdaQueryWrapper<Message>()
                .eq(Message::getReceiveUserId, userId)
                .eq(Message::getState, Constant.MESSAGE_STATE_UNREAD)
                .ne(Message::getDeleteState, Constant.DELETE_STATE_TRUE);
        Set<Long> hiddenPeerIds = queryHiddenPeerIds(userId);
        if (!hiddenPeerIds.isEmpty()) {
            query.notIn(Message::getPostUserId, hiddenPeerIds);
        }
        Long count = messageMapper.selectCount(query);
        stringRedisTemplate.opsForValue().set(cacheKey, String.valueOf(count), Constant.REDIS_TTL_MESSAGE_SESSIONS, TimeUnit.SECONDS);
        return count;
    }

    @Override
    public void updateMessageStatusByMessageId(Long messageId, Byte status, Long loginUserId) {
        Message msg = queryMessageByMessageId(messageId);
        // 用枚举校验状态合法性，杜绝魔法数字
        if (status == null || MessageStatus.fromCode(status) == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        if (!Objects.equals(loginUserId, msg.getPostUserId()) && !Objects.equals(loginUserId, msg.getReceiveUserId())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_UNAUTHORIZED));
        }
        if (Objects.equals(status, Constant.MESSAGE_STATE_RECALLED)) {
            recallMessage(messageId, loginUserId);
            return;
        }
        if (!Objects.equals(status, Constant.MESSAGE_STATE_READ)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE,
                    "只允许通过该接口标记已读; 撤回请调用 /message/recallMessage"));
        }
        if (!Objects.equals(loginUserId, msg.getReceiveUserId())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_UNAUTHORIZED));
        }
        if (Objects.equals(msg.getState(), Constant.MESSAGE_STATE_RECALLED)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "已撤回消息不能标记为已读"));
        }
        if (Objects.equals(msg.getState(), Constant.MESSAGE_STATE_READ)) {
            return;
        }
        int result = messageMapper.update(null, new LambdaUpdateWrapper<Message>().eq(Message::getId, messageId)
                .eq(Message::getReceiveUserId, loginUserId)
                .eq(Message::getState, Constant.MESSAGE_STATE_UNREAD)
                .ne(Message::getDeleteState, Constant.DELETE_STATE_TRUE).set(Message::getState, status));
        if (result <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED));
        }
        // 已读状态变更要刷新接收者的会话缓存 未读数会变
        if (Objects.equals(status, Constant.MESSAGE_STATE_READ)) {
            if (Objects.equals(msg.getState(), Constant.MESSAGE_STATE_UNREAD)) {
                decrementUnreadCount(msg.getReceiveUserId(), 1L);
            }
            invalidateSessionCache(msg.getPostUserId(), msg.getReceiveUserId());
            // 通知发送方：接收者已读 用于私信「已读」展示
            if (Objects.equals(msg.getState(), Constant.MESSAGE_STATE_UNREAD)) {
                pushPeerReadOne(msg.getPostUserId(), msg.getReceiveUserId(), messageId);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAllMessageReadBySender(Long selfId, Long senderId) {
        userService.queryUserByUserId(selfId);
        userService.queryUserByUserId(senderId);
        int result = messageMapper.update(null, new LambdaUpdateWrapper<Message>().eq(Message::getPostUserId, senderId)
                .eq(Message::getReceiveUserId, selfId).eq(Message::getState, Constant.MESSAGE_STATE_UNREAD)
                .ne(Message::getDeleteState, Constant.DELETE_STATE_TRUE).set(Message::getState, Constant.MESSAGE_STATE_READ));
        if (result > 0) {
            decrementUnreadCount(selfId, (long) result);
            invalidateSessionCache(selfId, senderId);
            // selfId 已读 senderId 发来的消息 → 通知 senderId 发件人 刷新对我方会话的已读角标
            pushPeerReadAll(senderId, selfId);
        }
    }

    
    // 私信列表 / 会话列表 / 聊天详情：全部分页
    
    // 我的会话列表 按对方聚合 会话计算需要扫描全部消息，因此先通过 buildAllSessions 取出完整聚合结果 自带 5 分钟缓存 ， 再在内存中切页返回，避免高频用户每次都扫表
    @Override
    public PageResult<MessageSessionResponse> queryMessageSessionWithPage(Long userId, Integer pageNum, Integer pageSize) {
        int validPageNum = PageUtils.getValidPageNum(pageNum);
        int validPageSize = PageUtils.getValidPageSize(pageSize);
        Set<Long> hiddenPeerIds = queryHiddenPeerIds(userId);
        Map<Long, Date> pinnedAtByPeer = queryPinnedAtByPeer(userId);
        Set<Long> mutedPeerIds = queryMutedPeerIds(userId);
        List<MessageSessionResponse> all = buildAllSessions(userId).stream()
                .filter(session -> session.getUser() != null
                        && !hiddenPeerIds.contains(session.getUser().getId()))
                .peek(session -> {
                    session.setPinnedAt(pinnedAtByPeer.get(session.getUser().getId()));
                    session.setMuted(mutedPeerIds.contains(session.getUser().getId()));
                })
                // buildAllSessions 已按最后消息时间倒序；这里只把置顶的提到前面。
                // sorted 是稳定排序，未置顶的那批仍保持原有的时间顺序
                .sorted(pinnedFirst())
                .collect(Collectors.toList());
        return pageList(all, validPageNum, validPageSize);
    }

    @Override
    public PageResult<MessageSessionSearchResponse> searchMessageSessions(Long userId, String keyword,
                                                                           Integer pageNum, Integer pageSize) {
        userService.queryUserByUserId(userId);
        int validPageNum = PageUtils.getValidPageNum(pageNum);
        int validPageSize = PageUtils.getValidPageSize(pageSize);
        String validKeyword = keyword == null ? "" : keyword.trim();
        if (!StringUtils.hasText(validKeyword)) {
            return new PageResult<>(new ArrayList<>(), 0L, validPageNum, validPageSize, 0L, false);
        }
        Set<Long> hiddenPeerIds = queryHiddenPeerIds(userId);
        List<Message> matches = messageMapper.selectList(new LambdaQueryWrapper<Message>()
                .and(wrapper -> wrapper.eq(Message::getPostUserId, userId)
                        .or(orWrapper -> orWrapper.eq(Message::getReceiveUserId, userId)))
                .eq(Message::getMessageType, Constant.MESSAGE_TYPE_TEXT)
                .ne(Message::getState, Constant.MESSAGE_STATE_RECALLED)
                .ne(Message::getDeleteState, Constant.DELETE_STATE_TRUE)
                .like(Message::getContent, validKeyword)
                .orderByDesc(Message::getCreateTime)
                .orderByDesc(Message::getId));
        Map<Long, MessageSessionSearchResponse> byPeer = new LinkedHashMap<>();
        for (Message message : matches) {
            Long peerUserId = Objects.equals(message.getPostUserId(), userId)
                    ? message.getReceiveUserId() : message.getPostUserId();
            if (hiddenPeerIds.contains(peerUserId) || byPeer.containsKey(peerUserId)) {
                continue;
            }
            byPeer.put(peerUserId, new MessageSessionSearchResponse(peerUserId, message.getId(),
                    message.getContent(), message.getCreateTime()));
        }
        return pageList(new ArrayList<>(byPeer.values()), validPageNum, validPageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void hideMessageSession(Long userId, Long peerUserId) {
        validateSessionPeer(userId, peerUserId);
        boolean sessionExists = buildAllSessions(userId).stream()
                .anyMatch(session -> session.getUser() != null
                        && Objects.equals(session.getUser().getId(), peerUserId));
        if (!sessionExists) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "私信会话不存在"));
        }
        MessageSessionVisibility current = querySessionVisibility(userId, peerUserId);
        if (current == null) {
            MessageSessionVisibility visibility = new MessageSessionVisibility();
            visibility.setUserId(userId);
            visibility.setPeerUserId(peerUserId);
            visibility.setHiddenState((byte) 1);
            try {
                messageSessionVisibilityMapper.insert(visibility);
            } catch (DuplicateKeyException exception) {
                updateSessionHiddenState(userId, peerUserId, (byte) 1);
            }
        } else if (!Objects.equals(current.getHiddenState(), (byte) 1)
                || Objects.equals(current.getDeleteState(), Constant.DELETE_STATE_TRUE)) {
            updateSessionHiddenState(userId, peerUserId, (byte) 1);
        }
        // 归档的同时收回置顶，否则一个看不见的会话还占着十个名额里的一个
        clearSessionPin(userId, peerUserId);
        invalidateUnreadCache(userId);
    }

    /**
     * 置顶或取消置顶。
     *
     * <p>置顶状态与归档共用 message_session_visibility 的同一行：这张表本来就是
     * 「某用户对某会话的个人设置」，两者天然属于一起，也省掉跨表一致性。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pinMessageSession(Long userId, Long peerUserId, boolean pinned) {
        validateSessionPeer(userId, peerUserId);
        if (!pinned) {
            clearSessionPin(userId, peerUserId);
            return;
        }
        boolean sessionExists = buildAllSessions(userId).stream()
                .anyMatch(session -> session.getUser() != null
                        && Objects.equals(session.getUser().getId(), peerUserId));
        if (!sessionExists) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "私信会话不存在"));
        }
        MessageSessionVisibility current = querySessionVisibility(userId, peerUserId);
        if (current != null && current.getPinnedAt() != null
                && !Objects.equals(current.getDeleteState(), Constant.DELETE_STATE_TRUE)) {
            // 已经是置顶了，重复点不该刷新它的位置
            return;
        }
        if (countPinnedSessions(userId) >= MAX_PINNED_SESSIONS) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED,
                    "最多只能置顶 " + MAX_PINNED_SESSIONS + " 个聊天"));
        }
        Date now = new Date();
        if (current == null) {
            MessageSessionVisibility visibility = new MessageSessionVisibility();
            visibility.setUserId(userId);
            visibility.setPeerUserId(peerUserId);
            visibility.setHiddenState((byte) 0);
            visibility.setPinnedAt(now);
            try {
                messageSessionVisibilityMapper.insert(visibility);
            } catch (DuplicateKeyException exception) {
                updateSessionPinnedAt(userId, peerUserId, now);
            }
            return;
        }
        updateSessionPinnedAt(userId, peerUserId, now);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restoreMessageSession(Long userId, Long peerUserId) {
        validateSessionPeer(userId, peerUserId);
        updateSessionHiddenState(userId, peerUserId, (byte) 0);
        invalidateUnreadCache(userId);
    }

    @Override
    public PageResult<MessageSessionResponse> queryHiddenMessageSessions(Long userId, Integer pageNum,
                                                                         Integer pageSize) {
        int validPageNum = PageUtils.getValidPageNum(pageNum);
        int validPageSize = PageUtils.getValidPageSize(pageSize);
        Set<Long> hiddenPeerIds = queryHiddenPeerIds(userId);
        List<MessageSessionResponse> all = buildAllSessions(userId).stream()
                .filter(session -> session.getUser() != null
                        && hiddenPeerIds.contains(session.getUser().getId()))
                .collect(Collectors.toList());
        return pageList(all, validPageNum, validPageSize);
    }

    @Override
    public PageResult<MessageDetailResponse> queryMessageDetailWithPage(Long userId, Long receiveId,
                                                                        Integer pageNum, Integer pageSize) {
        int validPageNum = PageUtils.getValidPageNum(pageNum);
        int validPageSize = PageUtils.getValidPageSize(pageSize);
        UserInternalVO selfUser = userService.queryUserByUserId(userId);
        UserInternalVO otherUser = userService.queryUserByUserId(receiveId);
        Page<Message> page = PageUtils.getPage(validPageNum, validPageSize);
        // MyBatis Plus 注意：.or .and ... 中 or 会被吞掉，必须使用 or lambda 写法
        Page<Message> result = messageMapper.selectPage(page, new LambdaQueryWrapper<Message>().and(w -> w
                        .and(i -> i.eq(Message::getPostUserId, userId).eq(Message::getReceiveUserId, receiveId))
                        .or(j -> j.eq(Message::getPostUserId, receiveId).eq(Message::getReceiveUserId, userId)))
                .ne(Message::getDeleteState, Constant.DELETE_STATE_TRUE)
                // 后审没过的消息双方都不再出现：发送方那侧原来还留着一条「未通过审核」，
                // 现在一并隐藏，聊天记录里就像这条消息没发生过
                .ne(Message::getState, Constant.MESSAGE_STATE_AUDIT_FAILED)
                // 倒序分页：第一页是「最近的一页」。
                //
                // 原来是正序分页，于是第一页永远是最古老的那批——前端固定取第一页，
                // 一旦某个会话超过一页，打开它看到的就是最早的消息而不是最新的，
                // 也没法「往上翻更早」。现在 pageNum 越大越往前翻。
                .orderByDesc(Message::getCreateTime)
                .orderByDesc(Message::getId));
        // 取出来是倒序，按时间正序交给前端渲染
        List<Message> rows = new ArrayList<>(result.getRecords());
        Collections.reverse(rows);
        Map<Long, List<MessageAlbumImage>> albumImages = queryAlbumImages(rows);
        List<MessageDetailResponse> records = rows.stream().map(msg -> {
            boolean isSelf = Objects.equals(msg.getPostUserId(), userId);
            UserInternalVO fromUser = isSelf ? selfUser : otherUser;
            return new MessageDetailResponse(org.pluchon.forum.converter.ImUserBriefConverter.toBrief(fromUser),
                    MessageConverter.toMessageVO(msg, albumImages.get(msg.getId())), isSelf);
        }).collect(Collectors.toList());
        return new PageResult<>(records, result.getTotal(), validPageNum, validPageSize, result.getPages(), result.hasNext());
    }

    @Override
    public MessageDetailResponse queryMessageDetailById(Long messageId, Long loginUserId) {
        Message msg = queryMessageByMessageId(messageId);
        if (!Objects.equals(loginUserId, msg.getPostUserId()) && !Objects.equals(loginUserId, msg.getReceiveUserId())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_UNAUTHORIZED));
        }
        boolean isSelf = Objects.equals(msg.getPostUserId(), loginUserId);
        UserInternalVO fromUser = userService.queryUserByUserId(msg.getPostUserId());
        List<MessageAlbumImage> albumImages = Constant.MESSAGE_TYPE_ALBUM.equals(msg.getMessageType())
                ? queryAlbumImages(msg.getId()) : List.of();
        return new MessageDetailResponse(org.pluchon.forum.converter.ImUserBriefConverter.toBrief(fromUser),
                MessageConverter.toMessageVO(msg, albumImages), isSelf);
    }

    // 收藏表情
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserChatEmojiResponse favoriteEmoji(FavoriteEmojiRequest req, Long userId) {
        if (req == null || req.getMediaUrl() == null || !StringUtils.hasLength(req.getMediaUrl().trim())
                || req.getMediaType() == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        if (!Constant.EMOJI_MEDIA_TYPE_IMAGE.equals(req.getMediaType())
                && !Constant.EMOJI_MEDIA_TYPE_GIF.equals(req.getMediaType())) {
            // 兼容 JSON 反序列化为 Integer 时 Byte.equals 失败
            Integer mediaTypeInt = req.getMediaType() == null ? null : req.getMediaType().intValue();
            boolean typeOk = mediaTypeInt != null
                    && (mediaTypeInt.equals(Constant.EMOJI_MEDIA_TYPE_IMAGE.intValue())
                    || mediaTypeInt.equals(Constant.EMOJI_MEDIA_TYPE_GIF.intValue()));
            if (!typeOk) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
            }
        }
        userService.queryUserByUserId(userId);

        String mediaUrl = req.getMediaUrl().trim();
        Long rawOriginId = req.getOriginMessageId();
        Long rawGroupOriginId = req.getOriginGroupMessageId();
        boolean quoteFromPrivateChat = rawOriginId != null && rawOriginId > 0;
        boolean quoteFromGroupChat = rawGroupOriginId != null && rawGroupOriginId > 0;
        if (quoteFromPrivateChat && quoteFromGroupChat) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }

        if (!quoteFromPrivateChat && !quoteFromGroupChat) {
            // 自上传 > 必须落在 emoji 子目录
            validateChatMediaUrl(mediaUrl, Constant.OSS_PATH_CHAT_EMOJI);
        } else {
            // 收藏会话中的图/GIF：仅允许对方自上传 message/ 或聊天表情库 emoji/ ，禁止商城图 emoji_shop/
            if (isChatMediaUnderPrefix(mediaUrl, Constant.OSS_PATH_EMOJI_SHOP)) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_EMOJI_SHOP_NOT_FAVORITABLE));
            }
            boolean okMsg = isChatMediaUnderPrefix(mediaUrl, Constant.OSS_PATH_CHAT_MESSAGE);
            boolean okEmoji = isChatMediaUnderPrefix(mediaUrl, Constant.OSS_PATH_CHAT_EMOJI);
            if (!okMsg && !okEmoji) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_INVALID_OSS_URL));
            }
            if (quoteFromPrivateChat) {
                Message origin = queryMessageByMessageId(rawOriginId);
                if (!userId.equals(origin.getPostUserId()) && !userId.equals(origin.getReceiveUserId())) {
                    throw new ApplicationException(Result.fail(ResultCode.FAILED_UNAUTHORIZED));
                }
                if (!mediaUrl.equals(origin.getMediaUrl())) {
                    throw new ApplicationException(Result.fail(ResultCode.FAILED_INVALID_OSS_URL));
                }
            } else {
                GroupChatMessage origin = groupChatMessageMapper.selectById(rawGroupOriginId);
                if (origin == null
                        || Constant.DELETE_STATE_TRUE.equals(origin.getDeleteState())
                        || !GroupChatMessageStatus.NORMAL.getCode().equals(origin.getStatus())
                        || (!GroupChatMessageType.EMOJI.getCode().equals(origin.getMessageType())
                        && !GroupChatMessageType.IMAGE.getCode().equals(origin.getMessageType()))) {
                    throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
                }
                Long memberCount = groupChatMemberMapper.selectCount(new LambdaQueryWrapper<GroupChatMember>()
                        .eq(GroupChatMember::getGroupId, origin.getGroupId())
                        .eq(GroupChatMember::getUserId, userId)
                        .eq(GroupChatMember::getStatus, GroupChatMemberStatus.ACTIVE.getCode())
                        .ne(GroupChatMember::getDeleteState, Constant.DELETE_STATE_TRUE));
                if (memberCount == null || memberCount <= 0) {
                    throw new ApplicationException(Result.fail(ResultCode.FAILED_UNAUTHORIZED));
                }
                if (!mediaUrl.equals(origin.getContent())) {
                    throw new ApplicationException(Result.fail(ResultCode.FAILED_INVALID_OSS_URL));
                }
            }
        }

        // 数量上限: 业务侧二选一, 简单 count 足够
        Long current = userChatEmojiMapper.selectCount(new LambdaQueryWrapper<UserChatEmoji>()
                .eq(UserChatEmoji::getUserId, userId)
                .ne(UserChatEmoji::getDeleteState, Constant.DELETE_STATE_TRUE));
        if (current != null && current >= Constant.EMOJI_MAX_PER_USER) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_EMOJI_LIMIT_EXCEEDED));
        }

        UserChatEmoji emoji = new UserChatEmoji();
        emoji.setUserId(userId);
        emoji.setMediaUrl(mediaUrl);
        emoji.setMediaType(req.getMediaType());
        emoji.setMediaMime(req.getMediaMime());
        emoji.setMediaSize(req.getMediaSize());
        emoji.setOriginMessageId(quoteFromPrivateChat ? rawOriginId : null);
        emoji.setOriginGroupMessageId(quoteFromGroupChat ? rawGroupOriginId : null);
        try {
            if (userChatEmojiMapper.insert(emoji) <= 0) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_CREATE));
            }
        } catch (DuplicateKeyException e) {
            // uix_user_url 命中: 已收藏过同一 URL
            throw new ApplicationException(Result.fail(ResultCode.FAILED_EMOJI_DUPLICATE));
        }
        invalidateEmojiCache(userId);
        return MessageConverter.toEmojiResponse(emoji);
    }

    // 取消收藏 软删, 只能删自己的
    @Override
    public void cancelFavoriteEmoji(Long emojiId, Long userId) {
        if (emojiId == null || emojiId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        // 注意: 这里直接走条件 更新, 同时校验所有权 + 软删, 简洁可靠
        int affected = userChatEmojiMapper.update(null, new LambdaUpdateWrapper<UserChatEmoji>()
                .eq(UserChatEmoji::getId, emojiId)
                .eq(UserChatEmoji::getUserId, userId)
                .ne(UserChatEmoji::getDeleteState, Constant.DELETE_STATE_TRUE)
                .set(UserChatEmoji::getDeleteState, Constant.DELETE_STATE_TRUE));
        if (affected <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_EMOJI_NOT_EXISTS));
        }
        invalidateEmojiCache(userId);
    }

    // 我的上传或聊天收藏表情分页列表，统一按创建时间升序
    @Override
    public PageResult<UserChatEmojiResponse> queryEmojiList(Long userId, String source,
                                                            Integer pageNum, Integer pageSize) {
        userService.queryUserByUserId(userId);
        String normalizedSource = source == null ? "" : source.trim().toLowerCase();
        if (!"uploaded".equals(normalizedSource) && !"favorite".equals(normalizedSource)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }

        int validPageNum = PageUtils.getValidPageNum(pageNum);
        int validPageSize = PageUtils.getValidPageSize(pageSize);
        LambdaQueryWrapper<UserChatEmoji> wrapper = new LambdaQueryWrapper<UserChatEmoji>()
                .eq(UserChatEmoji::getUserId, userId)
                .ne(UserChatEmoji::getDeleteState, Constant.DELETE_STATE_TRUE)
                // 新收藏/新上传的排最前。原来是升序，最早的占着第一页，
                // 前端只好在上传后手动跳到最后一页去找刚传的那张
                .orderByDesc(UserChatEmoji::getCreateTime)
                .orderByDesc(UserChatEmoji::getId);
        // 私信来源与群聊来源都算「收藏」；两者皆空且 URL 落在 emoji 子目录才是自上传
        if ("uploaded".equals(normalizedSource)) {
            wrapper.isNull(UserChatEmoji::getOriginMessageId)
                    .isNull(UserChatEmoji::getOriginGroupMessageId)
                    .like(UserChatEmoji::getMediaUrl, Constant.OSS_PATH_CHAT_EMOJI);
        } else {
            wrapper.and(query -> query
                    .isNotNull(UserChatEmoji::getOriginMessageId)
                    .or(group -> group.isNotNull(UserChatEmoji::getOriginGroupMessageId))
                    // 历史数据没有来源ID，只能靠 URL 目录反推
                    .or(legacy -> legacy
                            .isNull(UserChatEmoji::getOriginMessageId)
                            .isNull(UserChatEmoji::getOriginGroupMessageId)
                            .notLike(UserChatEmoji::getMediaUrl, Constant.OSS_PATH_CHAT_EMOJI)
                            .notLike(UserChatEmoji::getMediaUrl, Constant.OSS_PATH_EMOJI_SHOP)));
        }

        Page<UserChatEmoji> page = userChatEmojiMapper.selectPage(
                new Page<>(validPageNum, validPageSize), wrapper);
        List<UserChatEmojiResponse> records = page.getRecords().stream()
                .map(MessageConverter::toEmojiResponse)
                .collect(Collectors.toList());
        return new PageResult<>(records, page.getTotal(), validPageNum, validPageSize,
                page.getPages(), page.hasNext());
    }

    private void invalidateEmojiCache(Long userId) {
        stringRedisTemplate.delete(Constant.REDIS_KEY_USER_EMOJI_LIST + userId);
    }

    @Override
    public Message queryMessageByMessageId(Long messageId) {
        if (messageId == null || messageId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        Message msg = messageMapper.selectOne(new LambdaQueryWrapper<Message>()
                .eq(Message::getId, messageId).ne(Message::getDeleteState, Constant.DELETE_STATE_TRUE));
        if (msg == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
        return msg;
    }

    
    // 内部共用
    

    /**
     * 私信发送限频。
     *
     * <p>原来完全没有限制，而每条文本都会触发一次 AI 后审——刷消息就是刷 AI 额度，
     * 对方那边还会被通知刷屏。用 Redis 计数窗口挡住，超了直接拒绝。
     */
    private void assertSendRate(Long sendUserId) {
        if (sendUserId == null) {
            return;
        }
        String key = "forum:im:send-rate:" + sendUserId;
        try {
            Long count = stringRedisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                stringRedisTemplate.expire(key, SEND_RATE_WINDOW_SECONDS, TimeUnit.SECONDS);
            }
            if (count != null && count > SEND_RATE_MAX_IN_WINDOW) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED, "发送太频繁了，歇一会儿再说"));
            }
        } catch (ApplicationException e) {
            throw e;
        } catch (Exception e) {
            // Redis 不可用时不该连消息都发不出去
            log.debug("私信限频检查失败 sendUserId={}, error={}", sendUserId, e.getMessage());
        }
    }

    private void checkMessageSendGuard(MessageSendContext context) {
        assertSendRate(context == null ? null : context.getSenderUserId());
        MessageSendGuardResult result = messageSendGuardChain.check(context);
        if (!result.isPassed()) {
            throw new ApplicationException(result.getErrorResult());
        }
    }

    // 投递 MQ：私信通知；摘要最长 50 字防 substring 越界
    private void publishNotify(Long messageId, Long sendUserId, Long receiveUserId, String summary) {
        String safeSummary = summary == null ? "" : summary;
        if (safeSummary.length() > 50) {
            safeSummary = safeSummary.substring(0, 50);
        }
        UserInternalVO sender = userService.queryUserByUserId(sendUserId);
        String senderLabel = sender != null && StringUtils.hasLength(sender.getNickname())
                ? sender.getNickname() : (sender != null ? sender.getUsername() : "用户");
        String eventId = "msg:" + messageId;
        MessageNotifyMqVO vo = new MessageNotifyMqVO(eventId, messageId, sendUserId,
                senderLabel, receiveUserId, safeSummary, System.currentTimeMillis());
        // Outbox 与业务同事务落库；提交后再即时推送，避免进程在 afterCommit 前崩溃丢事件
        try {
            localMessageOutboxService.enqueue(Constant.ROUTING_KEY_QUEUE_2, eventId, vo);
        } catch (Exception e) {
            log.warn("私信 Outbox 写入失败 eventId={}", eventId, e);
            throw new ApplicationException(Result.fail(ResultCode.ERROR_SERVICES, "消息通知入队失败"));
        }
        TransactionHooks.afterCommit(() -> pushMessageNotify(vo));
    }

    // 在事务提交后通知接收者同步撤回状态
    private void pushMessageRecalled(Long messageId, Long sendUserId, Long receiveUserId) {
        TransactionHooks.afterCommit(() -> {
            try {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("type", "private_message_recalled");
                payload.put("messageId", messageId);
                payload.put("fromUserId", sendUserId);
                webSocketPushService.push(receiveUserId, objectMapper.writeValueAsString(payload));
            } catch (Exception e) {
                log.warn("私信撤回 WebSocket 推送失败 receiveUserId={} messageId={}", receiveUserId, messageId, e);
            }
        });
    }

    private void pushMessageNotify(MessageNotifyMqVO vo) {
        try {
            // 免打扰只掐掉实时提醒：消息已经入库，未读数也照常算，
            // 对方下次打开消息中心仍然看得到
            if (isMutedBy(vo.getReceiveUserId(), vo.getSendUserId())) {
                return;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "message");
            payload.put("dbMessageId", vo.getDbMessageId());
            payload.put("fromUserId", vo.getSendUserId());
            payload.put("fromUser", vo.getSendUsername());
            payload.put("senderNickname", vo.getSendUsername());
            payload.put("summary", vo.getContentSummary());
            webSocketPushService.push(vo.getReceiveUserId(), objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            log.warn("私信 WebSocket 推送失败 receiveUserId={} dbMessageId={}",
                    vo.getReceiveUserId(), vo.getDbMessageId(), e);
        }
    }

    // 计算会话/通知中展示的 最近一条消息 文案. 文本: 直接用 content 已撤回: 固定 消息已被撤回 商城表情: 固定 表情符号 ; 其他图片/GIF: 固定 图片
    private String sessionSummary(Message msg) {
        if (msg == null) {
            return "";
        }
        if (Constant.MESSAGE_STATE_RECALLED.equals(msg.getState())) {
            return "[消息已被撤回]";
        }
        if (Constant.MESSAGE_STATE_AUDIT_FAILED.equals(msg.getState())) {
            return "[消息未通过审核]";
        }
        if (Constant.MESSAGE_TYPE_IMAGE.equals(msg.getMessageType())
                || Constant.MESSAGE_TYPE_GIF.equals(msg.getMessageType())) {
            if (isChatMediaUnderPrefix(msg.getMediaUrl(), Constant.OSS_PATH_EMOJI_SHOP)) {
                return "[表情符号]";
            }
            String imageText = msg.getContent() == null ? "" : msg.getContent().trim();
            return imageText.isEmpty() ? "[图片]" : "[图片] " + imageText;
        }
        if (Constant.MESSAGE_TYPE_ALBUM.equals(msg.getMessageType())) {
            String albumText = msg.getContent() == null ? "" : msg.getContent().trim();
            return albumText.isEmpty() ? "[图集]" : "[图集] " + albumText;
        }
        String content = msg.getContent() == null ? "" : msg.getContent().trim();
        Matcher inviteMatcher = GROUP_INVITE_CARD_PATTERN.matcher(content);
        if (!inviteMatcher.matches()) {
            return content;
        }
        try {
            GroupChatJoinRequest request = groupChatJoinRequestMapper.selectById(Long.valueOf(inviteMatcher.group(1)));
            GroupChat group = request == null ? null : groupChatMapper.selectById(request.getGroupId());
            if (group != null && StringUtils.hasText(group.getName())) {
                return "进群邀请：（" + group.getName().trim() + "）";
            }
        } catch (Exception e) {
            log.warn("解析进群邀请会话摘要失败 messageId={}", msg.getId(), e);
        }
        return "进群邀请";
    }

    private boolean isChatMediaUnderPrefix(String mediaUrl, String allowedPathPrefix) {
        return ossConfig.matchesPublicObjectUrl(mediaUrl, allowedPathPrefix);
    }

    // 校验聊天用 mediaUrl 是否落在我们 OSS 的指定子目录下. 防止前端伪造外链或盗链 直接绕过本站上传接口的 AI 审核 .
    private void validateChatMediaUrl(String mediaUrl, String allowedPathPrefix) {
        if (!isChatMediaUnderPrefix(mediaUrl, allowedPathPrefix)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_INVALID_OSS_URL));
        }
    }

    // 任何会改变会话快照的写操作都要清掉双方的会话缓存
    private void invalidateSessionCache(Long userIdA, Long userIdB) {
        stringRedisTemplate.delete(Constant.REDIS_KEY_MESSAGE_SESSIONS + userIdA);
        stringRedisTemplate.delete(Constant.REDIS_KEY_MESSAGE_SESSIONS + userIdB);
    }

    private void incrementUnreadCount(Long userId, Long delta) {
        String cacheKey = Constant.REDIS_KEY_MESSAGE_UNREAD_COUNT + userId;
        Long count = stringRedisTemplate.opsForValue().increment(cacheKey, delta);
        if (count != null && count < 0) {
            stringRedisTemplate.opsForValue().set(cacheKey, "0", Constant.REDIS_TTL_MESSAGE_SESSIONS, TimeUnit.SECONDS);
            return;
        }
        stringRedisTemplate.expire(cacheKey, Constant.REDIS_TTL_MESSAGE_SESSIONS, TimeUnit.SECONDS);
    }

    private void decrementUnreadCount(Long userId, Long delta) {
        incrementUnreadCount(userId, -delta);
    }

    private void validateAlbumRequest(SendAlbumMessageRequest req) {
        if (req == null || req.getReceiveUserId() == null || req.getImages() == null
                || req.getImages().isEmpty() || req.getImages().size() > Constant.MESSAGE_ALBUM_MAX_IMAGES) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        if (req.getContent() != null && req.getContent().trim().length() > 500) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        for (AlbumImageRequest image : req.getImages()) {
            if (image == null || !StringUtils.hasText(image.getMediaUrl())
                    || !ossConfig.matchesPublicObjectUrl(image.getMediaUrl().trim(), Constant.OSS_PATH_CHAT_MESSAGE)) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_INVALID_OSS_URL));
            }
        }
    }

    private String normalizeAlbumContent(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        return content.trim();
    }

    private List<MessageAlbumImage> queryAlbumImages(Long messageId) {
        if (messageId == null) {
            return List.of();
        }
        return messageAlbumImageMapper.selectList(new LambdaQueryWrapper<MessageAlbumImage>()
                .eq(MessageAlbumImage::getMessageId, messageId)
                .ne(MessageAlbumImage::getDeleteState, Constant.DELETE_STATE_TRUE)
                .orderByAsc(MessageAlbumImage::getSortOrder));
    }

    private Map<Long, List<MessageAlbumImage>> queryAlbumImages(List<Message> messages) {
        List<Long> albumMessageIds = messages.stream()
                .filter(message -> Constant.MESSAGE_TYPE_ALBUM.equals(message.getMessageType()))
                .map(Message::getId)
                .filter(Objects::nonNull)
                .toList();
        if (albumMessageIds.isEmpty()) {
            return Map.of();
        }
        return messageAlbumImageMapper.selectList(new LambdaQueryWrapper<MessageAlbumImage>()
                        .in(MessageAlbumImage::getMessageId, albumMessageIds)
                        .ne(MessageAlbumImage::getDeleteState, Constant.DELETE_STATE_TRUE)
                        .orderByAsc(MessageAlbumImage::getMessageId)
                        .orderByAsc(MessageAlbumImage::getSortOrder))
                .stream()
                .collect(Collectors.groupingBy(MessageAlbumImage::getMessageId,
                        LinkedHashMap::new, Collectors.toList()));
    }

    private void invalidateUnreadCache(Long userId) {
        stringRedisTemplate.delete(Constant.REDIS_KEY_MESSAGE_UNREAD_COUNT + userId);
    }

    // 构造完整的会话列表 按对方聚合，最新的会话在前 优先读 Redis 5 分钟缓存，未命中则扫描全部相关消息一次性聚合并回写
    private List<MessageSessionResponse> buildAllSessions(Long userId) {
        userService.queryUserByUserId(userId);
        String cacheKey = Constant.REDIS_KEY_MESSAGE_SESSIONS + userId;
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, new TypeReference<>() {});
            } catch (Exception e) {
                log.error("反序列化私信会话列表失败, userId: {}", userId, e);
            }
        }
        List<Message> allMessages = messageMapper.selectList(new LambdaQueryWrapper<Message>()
                .and(w -> w.eq(Message::getPostUserId, userId)
                        .or(j -> j.eq(Message::getReceiveUserId, userId)))
                .ne(Message::getDeleteState, Constant.DELETE_STATE_TRUE).orderByDesc(Message::getId));
        if (allMessages.isEmpty()) {
            return new ArrayList<>();
        }
        // 按对方 ID 聚合：相同对方只保留最新一条作为会话；同时累加未读数
        List<MessageSessionResponse> resultList = getMessageSessionResponses(userId, allMessages);
        try {
            stringRedisTemplate.opsForValue().set(cacheKey,
                    objectMapper.writeValueAsString(resultList),
                    Constant.REDIS_TTL_MESSAGE_SESSIONS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("序列化私信会话列表缓存失败, userId: {}", userId, e);
        }
        return resultList;
    }

    private void validateSessionPeer(Long userId, Long peerUserId) {
        if (userId == null || peerUserId == null || peerUserId <= 0 || Objects.equals(userId, peerUserId)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        userService.queryUserByUserId(peerUserId);
    }

    private MessageSessionVisibility querySessionVisibility(Long userId, Long peerUserId) {
        return messageSessionVisibilityMapper.selectOne(new LambdaQueryWrapper<MessageSessionVisibility>()
                .eq(MessageSessionVisibility::getUserId, userId)
                .eq(MessageSessionVisibility::getPeerUserId, peerUserId));
    }

    private void updateSessionHiddenState(Long userId, Long peerUserId, Byte hiddenState) {
        messageSessionVisibilityMapper.update(null, new LambdaUpdateWrapper<MessageSessionVisibility>()
                .eq(MessageSessionVisibility::getUserId, userId)
                .eq(MessageSessionVisibility::getPeerUserId, peerUserId)
                .set(MessageSessionVisibility::getHiddenState, hiddenState)
                .set(MessageSessionVisibility::getDeleteState, Constant.DELETE_STATE_FALSE));
    }

    /**
     * 开关免打扰。
     *
     * <p>只影响实时提醒：消息照常入库、未读数照常累计，只是不再推送给对方的客户端。
     * 与群聊的 notifyMode=NONE 是同一个语义。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void muteMessageSession(Long userId, Long peerUserId, boolean muted) {
        validateSessionPeer(userId, peerUserId);
        byte target = muted ? (byte) 1 : (byte) 0;
        MessageSessionVisibility current = querySessionVisibility(userId, peerUserId);
        if (current == null) {
            MessageSessionVisibility visibility = new MessageSessionVisibility();
            visibility.setUserId(userId);
            visibility.setPeerUserId(peerUserId);
            visibility.setHiddenState((byte) 0);
            visibility.setMutedState(target);
            try {
                messageSessionVisibilityMapper.insert(visibility);
            } catch (DuplicateKeyException exception) {
                updateSessionMutedState(userId, peerUserId, target);
            }
            return;
        }
        updateSessionMutedState(userId, peerUserId, target);
    }

    private void updateSessionMutedState(Long userId, Long peerUserId, byte mutedState) {
        messageSessionVisibilityMapper.update(null, new LambdaUpdateWrapper<MessageSessionVisibility>()
                .eq(MessageSessionVisibility::getUserId, userId)
                .eq(MessageSessionVisibility::getPeerUserId, peerUserId)
                .set(MessageSessionVisibility::getMutedState, mutedState)
                .set(MessageSessionVisibility::getDeleteState, Constant.DELETE_STATE_FALSE));
    }

    private Set<Long> queryMutedPeerIds(Long userId) {
        Set<Long> result = new HashSet<>();
        for (MessageSessionVisibility record : messageSessionVisibilityMapper.selectList(
                new LambdaQueryWrapper<MessageSessionVisibility>()
                        .eq(MessageSessionVisibility::getUserId, userId)
                        .eq(MessageSessionVisibility::getMutedState, (byte) 1)
                        .ne(MessageSessionVisibility::getDeleteState, Constant.DELETE_STATE_TRUE))) {
            result.add(record.getPeerUserId());
        }
        return result;
    }

    // 接收方是否对这个发送方开了免打扰
    private boolean isMutedBy(Long receiveUserId, Long peerUserId) {
        MessageSessionVisibility record = querySessionVisibility(receiveUserId, peerUserId);
        return record != null
                && Objects.equals(record.getMutedState(), (byte) 1)
                && !Objects.equals(record.getDeleteState(), Constant.DELETE_STATE_TRUE);
    }

    // 置顶的会话排在最前，按置顶时刻由近及远；未置顶的保持原有的时间顺序
    private Comparator<MessageSessionResponse> pinnedFirst() {
        return (left, right) -> {
            Date leftPinned = left.getPinnedAt();
            Date rightPinned = right.getPinnedAt();
            if (leftPinned != null && rightPinned != null) {
                return rightPinned.compareTo(leftPinned);
            }
            if (leftPinned != null) {
                return -1;
            }
            if (rightPinned != null) {
                return 1;
            }
            return 0;
        };
    }

    private Map<Long, Date> queryPinnedAtByPeer(Long userId) {
        Map<Long, Date> result = new HashMap<>();
        for (MessageSessionVisibility record : messageSessionVisibilityMapper.selectList(
                new LambdaQueryWrapper<MessageSessionVisibility>()
                        .eq(MessageSessionVisibility::getUserId, userId)
                        .isNotNull(MessageSessionVisibility::getPinnedAt)
                        .ne(MessageSessionVisibility::getDeleteState, Constant.DELETE_STATE_TRUE))) {
            result.put(record.getPeerUserId(), record.getPinnedAt());
        }
        return result;
    }

    private long countPinnedSessions(Long userId) {
        Long count = messageSessionVisibilityMapper.selectCount(
                new LambdaQueryWrapper<MessageSessionVisibility>()
                        .eq(MessageSessionVisibility::getUserId, userId)
                        .isNotNull(MessageSessionVisibility::getPinnedAt)
                        .ne(MessageSessionVisibility::getDeleteState, Constant.DELETE_STATE_TRUE));
        return count == null ? 0L : count;
    }

    private void updateSessionPinnedAt(Long userId, Long peerUserId, Date pinnedAt) {
        messageSessionVisibilityMapper.update(null, new LambdaUpdateWrapper<MessageSessionVisibility>()
                .eq(MessageSessionVisibility::getUserId, userId)
                .eq(MessageSessionVisibility::getPeerUserId, peerUserId)
                .set(MessageSessionVisibility::getPinnedAt, pinnedAt)
                .set(MessageSessionVisibility::getDeleteState, Constant.DELETE_STATE_FALSE));
    }

    private void clearSessionPin(Long userId, Long peerUserId) {
        messageSessionVisibilityMapper.update(null, new LambdaUpdateWrapper<MessageSessionVisibility>()
                .eq(MessageSessionVisibility::getUserId, userId)
                .eq(MessageSessionVisibility::getPeerUserId, peerUserId)
                .set(MessageSessionVisibility::getPinnedAt, null));
    }

    private Set<Long> queryHiddenPeerIds(Long userId) {
        List<MessageSessionVisibility> records = messageSessionVisibilityMapper.selectList(
                new LambdaQueryWrapper<MessageSessionVisibility>()
                        .eq(MessageSessionVisibility::getUserId, userId)
                        .eq(MessageSessionVisibility::getHiddenState, (byte) 1)
                        .ne(MessageSessionVisibility::getDeleteState, Constant.DELETE_STATE_TRUE));
        Set<Long> result = new HashSet<>();
        for (MessageSessionVisibility record : records) {
            result.add(record.getPeerUserId());
        }
        return result;
    }

    private Set<Long> restoreHiddenPair(Long userIdA, Long userIdB) {
        Set<Long> restoredUsers = new HashSet<>();
        if (restoreHiddenSession(userIdA, userIdB) > 0) {
            restoredUsers.add(userIdA);
        }
        if (restoreHiddenSession(userIdB, userIdA) > 0) {
            restoredUsers.add(userIdB);
        }
        return restoredUsers;
    }

    private int restoreHiddenSession(Long userId, Long peerUserId) {
        return messageSessionVisibilityMapper.update(null, new LambdaUpdateWrapper<MessageSessionVisibility>()
                .eq(MessageSessionVisibility::getUserId, userId)
                .eq(MessageSessionVisibility::getPeerUserId, peerUserId)
                .eq(MessageSessionVisibility::getHiddenState, (byte) 1)
                .ne(MessageSessionVisibility::getDeleteState, Constant.DELETE_STATE_TRUE)
                .set(MessageSessionVisibility::getHiddenState, (byte) 0));
    }

    private <T> PageResult<T> pageList(List<T> all, int pageNum, int pageSize) {
        long total = all.size();
        long pages = (total + pageSize - 1) / pageSize;
        int startIndex = (pageNum - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, all.size());
        List<T> records = startIndex >= all.size() ? new ArrayList<>() : all.subList(startIndex, endIndex);
        return new PageResult<>(records, total, pageNum, pageSize, pages, pageNum < pages);
    }

    // 提取共同方法
    private @NonNull List<MessageSessionResponse> getMessageSessionResponses(Long userId, List<Message> allMessages) {
        Map<Long, MessageSessionResponse> sessionMap = new LinkedHashMap<>();
        for (Message msg : allMessages) {
            // 审核失败的消息双方都不可见，也不参与会话列表的「最后一条」
            if (Constant.MESSAGE_STATE_AUDIT_FAILED.equals(msg.getState())) {
                continue;
            }
            Long otherId = msg.getPostUserId().equals(userId) ? msg.getReceiveUserId() : msg.getPostUserId();
            sessionMap.computeIfAbsent(otherId, id -> {
                UserInternalVO other = userService.queryUserByUserId(id);
                MessageSessionResponse session = new MessageSessionResponse();
                session.setUnReadMessage(0L);
                session.setUser(org.pluchon.forum.converter.ImUserBriefConverter.toBrief(other));
                session.setLastMessage(sessionSummary(msg));
                session.setLastMessageTime(msg.getUpdateTime());
                return session;
            });
            // 对方发给我 + 状态未读 > 累加未读数
            if (msg.getReceiveUserId().equals(userId)
                    && Objects.equals(msg.getState(), Constant.MESSAGE_STATE_UNREAD)) {
                MessageSessionResponse session = sessionMap.get(otherId);
                session.setUnReadMessage(session.getUnReadMessage() + 1);
            }
        }
        return new ArrayList<>(sessionMap.values());
    }
}
