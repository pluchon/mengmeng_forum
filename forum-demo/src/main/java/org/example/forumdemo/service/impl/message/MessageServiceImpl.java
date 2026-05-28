package org.example.forumdemo.service.impl.message;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.common.config.OssConfig;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.enums.MessageStatus;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.mq.ForumProducer;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.common.utils.AiAuditUtils;
import org.example.forumdemo.common.utils.UserMuteGuard;
import org.example.forumdemo.service.impl.websocket.WebSocketPushService;
import org.example.forumdemo.common.utils.PageUtils;
import org.example.forumdemo.entity.db.Message;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.db.UserChatEmoji;
import org.example.forumdemo.entity.dto.message.FavoriteEmojiRequest;
import org.example.forumdemo.entity.dto.message.MessageReplyRequest;
import org.example.forumdemo.entity.dto.message.SendImageMessageRequest;
import org.example.forumdemo.entity.dto.message.SendMessageRequest;
import org.example.forumdemo.entity.vo.common.PageResult;
import org.example.forumdemo.entity.vo.message.MessageDetailResponse;
import org.example.forumdemo.entity.vo.message.MessageListResponse;
import org.example.forumdemo.entity.vo.message.MessageSessionResponse;
import org.example.forumdemo.entity.vo.message.UserChatEmojiResponse;
import org.example.forumdemo.entity.vo.mq.MessageNotifyMqVO;
import org.example.forumdemo.entity.vo.user.UserBriefVO;
import org.example.forumdemo.mapper.MessageMapper;
import org.example.forumdemo.mapper.UserChatEmojiMapper;
import org.example.forumdemo.service.interfaces.message.MessageService;
import org.example.forumdemo.service.interfaces.user.UserService;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class MessageServiceImpl implements MessageService {

    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private UserChatEmojiMapper userChatEmojiMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private ForumProducer forumProducer;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OssConfig ossConfig;

    @Autowired
    private WebSocketPushService webSocketPushService;

    // ============================================================
    // 发送 / 撤回 / 回复
    // ============================================================

    /** 对方批量已读了我发给 TA 的消息时，通知我（发送方）以便前端刷新「已读」 */
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
    public Message send(SendMessageRequest req, Long sendUserId) {
        validateContent(req.getContent());
        User sender = userService.queryUserByUserId(sendUserId);
        UserMuteGuard.assertCanPost(sender);
        Long receiveUserId = req.getReceiveUserId();
        userService.queryUserByUserId(receiveUserId);
        if (sendUserId.equals(receiveUserId)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_SEND_MESSAGE_BY_MYSELF));
        }
        Message newMessage = new Message();
        newMessage.setPostUserId(sendUserId);
        newMessage.setReceiveUserId(receiveUserId);
        newMessage.setMessageType(Constant.MESSAGE_TYPE_TEXT);
        newMessage.setContent(req.getContent());
        newMessage.setState(Constant.MESSAGE_STATE_UNREAD);
        if (messageMapper.insert(newMessage) <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_CREATE));
        }
        publishNotify(newMessage.getId(), sendUserId, receiveUserId, sessionSummary(newMessage));
        incrementUnreadCount(receiveUserId, 1L);
        invalidateSessionCache(sendUserId, receiveUserId);
        return queryMessageByMessageId(newMessage.getId());
    }

    /**
     * 发送图片/GIF 消息.
     * 与 send 完全独立: 不接受 content, 由 mediaUrl 表达图片地址.
     * mediaUrl 须为本站 OSS 下 {@link Constant#OSS_PATH_CHAT_MESSAGE}（聊天临时上传）或
     * {@link Constant#OSS_PATH_CHAT_EMOJI}（表情收藏库）之一，二者均经上传接口 + AI 审核写入.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Message sendImage(SendImageMessageRequest req, Long sendUserId) {
        if (req == null || req.getReceiveUserId() == null
                || !StringUtils.hasLength(req.getMediaUrl())
                || req.getMessageType() == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_MESSAGE_IMAGE_INVALID));
        }
        if (!Constant.MESSAGE_TYPE_IMAGE.equals(req.getMessageType())
                && !Constant.MESSAGE_TYPE_GIF.equals(req.getMessageType())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_MESSAGE_IMAGE_INVALID));
        }
        Long receiveUserId = req.getReceiveUserId();
        if (sendUserId.equals(receiveUserId)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_SEND_MESSAGE_BY_MYSELF));
        }
        User sender = userService.queryUserByUserId(sendUserId);
        UserMuteGuard.assertCanPost(sender);
        userService.queryUserByUserId(receiveUserId);
        validateSendImageMediaUrl(req.getMediaUrl());

        Message newMessage = new Message();
        newMessage.setPostUserId(sendUserId);
        newMessage.setReceiveUserId(receiveUserId);
        newMessage.setMessageType(req.getMessageType());
        newMessage.setContent(null);
        newMessage.setMediaUrl(req.getMediaUrl());
        newMessage.setMediaMime(req.getMediaMime());
        newMessage.setMediaSize(req.getMediaSize());
        newMessage.setMediaWidth(req.getMediaWidth());
        newMessage.setMediaHeight(req.getMediaHeight());
        newMessage.setState(Constant.MESSAGE_STATE_UNREAD);
        if (messageMapper.insert(newMessage) <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_CREATE));
        }
        publishNotify(newMessage.getId(), sendUserId, receiveUserId, sessionSummary(newMessage));
        incrementUnreadCount(receiveUserId, 1L);
        invalidateSessionCache(sendUserId, receiveUserId);
        return queryMessageByMessageId(newMessage.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replyMessage(MessageReplyRequest req, Long sendUserId) {
        validateContent(req.getContent());
        User sender = userService.queryUserByUserId(sendUserId);
        UserMuteGuard.assertCanPost(sender);
        Long receiveId = req.getReceiveId();
        // 校验接收者存在
        userService.queryUserByUserId(receiveId);
        if (!StringUtils.hasLength(req.getContent())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        Message reply = new Message();
        reply.setPostUserId(sendUserId);
        reply.setReceiveUserId(receiveId);
        reply.setMessageType(Constant.MESSAGE_TYPE_TEXT);
        reply.setContent(req.getContent());
        reply.setState(Constant.MESSAGE_STATE_UNREAD);
        if (messageMapper.insert(reply) <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED));
        }
        publishNotify(reply.getId(), sendUserId, receiveId, sessionSummary(reply));
        incrementUnreadCount(receiveId, 1L);
        invalidateSessionCache(sendUserId, receiveId);
    }

    /**
     * 撤回私信
     * 规则：
     *   1) 仅发送者本人可以撤回
     *   2) 必须在创建后的 RECALL_WINDOW 秒内
     *   3) 物理保留原 content（审计用），仅把 state 置为 RECALLED；前端按 state=2 展示占位语
     */
    @Override
    public void recallMessage(Long messageId, Long loginUserId) {
        Message msg = queryMessageByMessageId(messageId);
        if (!msg.getPostUserId().equals(loginUserId)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_UNAUTHORIZED));
        }
        long secondsElapsed = (System.currentTimeMillis() - msg.getCreateTime().getTime()) / 1000;
        if (secondsElapsed > Constant.MESSAGE_RECALL_WINDOW_SECONDS) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED, "超过撤回时间窗口（2分钟），无法撤回"));
        }
        int result = messageMapper.update(null, new UpdateWrapper<Message>().lambda()
                .eq(Message::getId, messageId).eq(Message::getPostUserId, loginUserId)
                .ne(Message::getDeleteState, Constant.DELETE_STATE_TRUE).set(Message::getState, Constant.MESSAGE_STATE_RECALLED));
        if (result <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED, "撤回失败，可能已超时或无权限"));
        }
        if (Objects.equals(msg.getState(), Constant.MESSAGE_STATE_UNREAD)) {
            decrementUnreadCount(msg.getReceiveUserId(), 1L);
        }
        invalidateSessionCache(loginUserId, msg.getReceiveUserId());
        log.info("用户 {} 撤回了私信 {}", loginUserId, messageId);
    }

    // ============================================================
    // 状态修改 / 未读数
    // ============================================================
    @Override
    public Long selectMessageUnRead(Long userId) {
        userService.queryUserByUserId(userId);
        String cacheKey = Constant.REDIS_KEY_MESSAGE_UNREAD_COUNT + userId;
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return Long.valueOf(cached);
        }
        Long count = messageMapper.selectCount(new QueryWrapper<Message>().lambda().eq(Message::getReceiveUserId, userId)
                .eq(Message::getState, Constant.MESSAGE_STATE_UNREAD).ne(Message::getDeleteState, Constant.DELETE_STATE_TRUE));
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
        int result = messageMapper.update(null, new UpdateWrapper<Message>().lambda().eq(Message::getId, messageId)
                .eq(Message::getReceiveUserId, loginUserId)
                .eq(Message::getState, Constant.MESSAGE_STATE_UNREAD)
                .ne(Message::getDeleteState, Constant.DELETE_STATE_TRUE).set(Message::getState, status));
        if (result <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED));
        }
        // 已读状态变更要刷新接收者的会话缓存（未读数会变）
        if (Objects.equals(status, Constant.MESSAGE_STATE_READ)) {
            if (Objects.equals(msg.getState(), Constant.MESSAGE_STATE_UNREAD)) {
                decrementUnreadCount(msg.getReceiveUserId(), 1L);
            }
            invalidateSessionCache(msg.getPostUserId(), msg.getReceiveUserId());
            // 通知发送方：接收者已读（用于私信「已读」展示）
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
        int result = messageMapper.update(null, new UpdateWrapper<Message>().lambda().eq(Message::getPostUserId, senderId)
                .eq(Message::getReceiveUserId, selfId).eq(Message::getState, Constant.MESSAGE_STATE_UNREAD)
                .ne(Message::getDeleteState, Constant.DELETE_STATE_TRUE).set(Message::getState, Constant.MESSAGE_STATE_READ));
        if (result > 0) {
            decrementUnreadCount(selfId, (long) result);
            invalidateSessionCache(selfId, senderId);
            // selfId 已读 senderId 发来的消息 → 通知 senderId（发件人）刷新对我方会话的已读角标
            pushPeerReadAll(senderId, selfId);
        }
    }

    // ============================================================
    // 私信列表 / 会话列表 / 聊天详情：全部分页
    // ============================================================
    @Override
    public PageResult<MessageListResponse> selectMessageListByUserIdWithPage(Long userId, Integer pageNum, Integer pageSize) {
        int validPageNum = PageUtils.getValidPageNum(pageNum);
        int validPageSize = PageUtils.getValidPageSize(pageSize);
        Page<Message> page = PageUtils.getPage(validPageNum, validPageSize);
        Page<Message> result = messageMapper.selectPage(page, new QueryWrapper<Message>().lambda().eq(Message::getReceiveUserId, userId)
                .ne(Message::getDeleteState, Constant.DELETE_STATE_TRUE).orderByDesc(Message::getCreateTime));
        List<MessageListResponse> records = result.getRecords().stream().map(msg -> {
            User user = userService.queryUserByUserId(msg.getPostUserId());
            return new MessageListResponse(new UserBriefVO(user), msg);
        }).collect(Collectors.toList());
        return new PageResult<>(records, result.getTotal(), validPageNum, validPageSize,
                result.getPages(), result.hasNext());
    }

    /**
     * 我的会话列表（按对方聚合）
     * 会话计算需要扫描全部消息，因此先通过 buildAllSessions 取出完整聚合结果（自带 5 分钟缓存），
     * 再在内存中切页返回，避免高频用户每次都扫表
     */
    @Override
    public PageResult<MessageSessionResponse> queryMessageSessionWithPage(Long userId, Integer pageNum, Integer pageSize) {
        int validPageNum = PageUtils.getValidPageNum(pageNum);
        int validPageSize = PageUtils.getValidPageSize(pageSize);
        List<MessageSessionResponse> all = buildAllSessions(userId);
        long total = all.size();
        long pages = (total + validPageSize - 1) / validPageSize;
        int startIndex = (validPageNum - 1) * validPageSize;
        int endIndex = Math.min(startIndex + validPageSize, all.size());
        List<MessageSessionResponse> records = startIndex >= all.size() ? new ArrayList<>() : all.subList(startIndex, endIndex);
        return new PageResult<>(records, total, validPageNum, validPageSize, pages, validPageNum < pages);
    }

    @Override
    public PageResult<MessageDetailResponse> queryMessageDetailWithPage(Long userId, Long receiveId,
                                                                        Integer pageNum, Integer pageSize) {
        int validPageNum = PageUtils.getValidPageNum(pageNum);
        int validPageSize = PageUtils.getValidPageSize(pageSize);
        User selfUser = userService.queryUserByUserId(userId);
        User otherUser = userService.queryUserByUserId(receiveId);
        Page<Message> page = PageUtils.getPage(validPageNum, validPageSize);
        // MyBatis-Plus 注意：.or().and(...) 中 or() 会被吞掉，必须使用 or(lambda) 写法
        Page<Message> result = messageMapper.selectPage(page, new QueryWrapper<Message>().lambda().and(w -> w
                        .and(i -> i.eq(Message::getPostUserId, userId).eq(Message::getReceiveUserId, receiveId))
                        .or(j -> j.eq(Message::getPostUserId, receiveId).eq(Message::getReceiveUserId, userId)))
                .ne(Message::getDeleteState, Constant.DELETE_STATE_TRUE)
                .orderByAsc(Message::getCreateTime));
        List<MessageDetailResponse> records = result.getRecords().stream().map(msg -> {
            boolean isSelf = Objects.equals(msg.getPostUserId(), userId);
            User fromUser = isSelf ? selfUser : otherUser;
            return new MessageDetailResponse(new UserBriefVO(fromUser), msg, isSelf);
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
        User fromUser = userService.queryUserByUserId(msg.getPostUserId());
        return new MessageDetailResponse(new UserBriefVO(fromUser), msg, isSelf);
    }

    // ============================================================
    // 表情收藏 (favorite / cancel / list)
    // ============================================================

    /**
     * 收藏表情. 两种来源:
     * 1) 自上传: originMessageId 为空, mediaUrl 必须指向 OSS_PATH_CHAT_EMOJI;
     * 2) 收藏他人聊天图: originMessageId 必填且必须可见(参与会话的双方之一是当前用户),
     *    mediaUrl 须与原消息 mediaUrl 一致，且落在 OSS_PATH_CHAT_MESSAGE 或 OSS_PATH_CHAT_EMOJI（对方自上传/表情库）;
     *    表情商城目录 OSS_PATH_EMOJI_SHOP 仅可发送不可被他人收藏.
     * 单用户最多 EMOJI_MAX_PER_USER 张; 同一 URL 唯一键兜底防重复.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserChatEmoji favoriteEmoji(FavoriteEmojiRequest req, Long userId) {
        if (req == null || req.getMediaUrl() == null || !StringUtils.hasLength(req.getMediaUrl().trim())
                || req.getMediaType() == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        if (!Constant.EMOJI_MEDIA_TYPE_IMAGE.equals(req.getMediaType())
                && !Constant.EMOJI_MEDIA_TYPE_GIF.equals(req.getMediaType())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        userService.queryUserByUserId(userId);

        String mediaUrl = req.getMediaUrl().trim();
        Long rawOriginId = req.getOriginMessageId();
        // 仅当传入「有效的正消息 ID」时才视为引用会话图片；0 / 负数与 null 一律按自上传处理
        boolean quoteFromChat = rawOriginId != null && rawOriginId > 0;

        if (!quoteFromChat) {
            // 自上传 -> 必须落在 emoji 子目录
            validateChatMediaUrl(mediaUrl, Constant.OSS_PATH_CHAT_EMOJI);
        } else {
            // 收藏会话中的图/GIF：仅允许对方自上传(message/)或聊天表情库(emoji/)，禁止商城图(emoji_shop/)
            if (isChatMediaUnderPrefix(mediaUrl, Constant.OSS_PATH_EMOJI_SHOP)) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_EMOJI_SHOP_NOT_FAVORITABLE));
            }
            boolean okMsg = isChatMediaUnderPrefix(mediaUrl, Constant.OSS_PATH_CHAT_MESSAGE);
            boolean okEmoji = isChatMediaUnderPrefix(mediaUrl, Constant.OSS_PATH_CHAT_EMOJI);
            if (!okMsg && !okEmoji) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_INVALID_OSS_URL));
            }
            Message origin = queryMessageByMessageId(rawOriginId);
            if (!userId.equals(origin.getPostUserId()) && !userId.equals(origin.getReceiveUserId())) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_UNAUTHORIZED));
            }
            if (!mediaUrl.equals(origin.getMediaUrl())) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_INVALID_OSS_URL));
            }
        }

        // 数量上限: 业务侧二选一, 简单 count 足够
        Long current = userChatEmojiMapper.selectCount(new QueryWrapper<UserChatEmoji>().lambda()
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
        emoji.setOriginMessageId(quoteFromChat ? rawOriginId : null);
        try {
            if (userChatEmojiMapper.insert(emoji) <= 0) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_CREATE));
            }
        } catch (DuplicateKeyException e) {
            // uix_user_url 命中: 已收藏过同一 URL
            throw new ApplicationException(Result.fail(ResultCode.FAILED_EMOJI_DUPLICATE));
        }
        invalidateEmojiCache(userId);
        return emoji;
    }

    /** 取消收藏 (软删, 只能删自己的) */
    @Override
    public void cancelFavoriteEmoji(Long emojiId, Long userId) {
        if (emojiId == null || emojiId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        // 注意: 这里直接走条件 update, 同时校验所有权 + 软删, 简洁可靠
        int affected = userChatEmojiMapper.update(null, new UpdateWrapper<UserChatEmoji>().lambda()
                .eq(UserChatEmoji::getId, emojiId)
                .eq(UserChatEmoji::getUserId, userId)
                .ne(UserChatEmoji::getDeleteState, Constant.DELETE_STATE_TRUE)
                .set(UserChatEmoji::getDeleteState, Constant.DELETE_STATE_TRUE));
        if (affected <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_EMOJI_NOT_EXISTS));
        }
        invalidateEmojiCache(userId);
    }

    /** 我的表情收藏列表; Redis 命中即返回, 未命中查 DB 后回写 */
    @Override
    public List<UserChatEmojiResponse> queryEmojiList(Long userId) {
        userService.queryUserByUserId(userId);
        String cacheKey = Constant.REDIS_KEY_USER_EMOJI_LIST + userId;
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, new TypeReference<>() {});
            } catch (Exception e) {
                log.error("反序列化表情收藏列表失败, userId: {}", userId, e);
            }
        }
        List<UserChatEmoji> list = userChatEmojiMapper.selectList(new QueryWrapper<UserChatEmoji>().lambda()
                .eq(UserChatEmoji::getUserId, userId)
                .ne(UserChatEmoji::getDeleteState, Constant.DELETE_STATE_TRUE)
                .orderByDesc(UserChatEmoji::getCreateTime));
        List<UserChatEmojiResponse> result = list.stream().map(UserChatEmojiResponse::new).collect(Collectors.toList());
        try {
            stringRedisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(result),
                    Constant.REDIS_TTL_USER_EMOJI_LIST, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("序列化表情收藏列表缓存失败, userId: {}", userId, e);
        }
        return result;
    }

    private void invalidateEmojiCache(Long userId) {
        stringRedisTemplate.delete(Constant.REDIS_KEY_USER_EMOJI_LIST + userId);
    }

    @Override
    public Message queryMessageByMessageId(Long messageId) {
        if (messageId == null || messageId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        Message msg = messageMapper.selectOne(new QueryWrapper<Message>().lambda()
                .eq(Message::getId, messageId).ne(Message::getDeleteState, Constant.DELETE_STATE_TRUE));
        if (msg == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
        return msg;
    }

    // ============================================================
    // 内部共用
    // ============================================================

    /** AI 文本审核，违规直接抛业务异常 */
    private void validateContent(String content) {
        String violation = AiAuditUtils.isTextAllowed(content);
        if (violation != null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_CONTENT_VIOLATION, violation));
        }
    }

    /** 投递 MQ：私信通知；摘要最长 50 字防 substring 越界 */
    private void publishNotify(Long messageId, Long sendUserId, Long receiveUserId, String summary) {
        String safeSummary = summary == null ? "" : summary;
        if (safeSummary.length() > 50) {
            safeSummary = safeSummary.substring(0, 50);
        }
        User sender = userService.getUserInfoById(sendUserId);
        String senderLabel = sender != null && StringUtils.hasLength(sender.getNickname())
                ? sender.getNickname() : (sender != null ? sender.getUsername() : "用户");
        forumProducer.sendMessageNotify(new MessageNotifyMqVO("", messageId, sendUserId,
                senderLabel, receiveUserId, safeSummary, System.currentTimeMillis()));
    }

    /**
     * 计算会话/通知中展示的"最近一条消息"文案.
     * - 文本: 直接用 content
     * - 已撤回: 由前端按 state=2 渲染占位语, 这里仍返回原 content (审计)
     * - 图片: 固定 "[图片]"; GIF: 固定 "[GIF]"
     */
    private String sessionSummary(Message msg) {
        if (msg == null) {
            return "";
        }
        if (Constant.MESSAGE_TYPE_IMAGE.equals(msg.getMessageType())) {
            return "[图片]";
        }
        if (Constant.MESSAGE_TYPE_GIF.equals(msg.getMessageType())) {
            return "[GIF]";
        }
        return msg.getContent() == null ? "" : msg.getContent();
    }

    /**
     * 会话气泡中的图片/GIF URL：允许聊天临时目录、聊天表情库目录或表情商城目录（均经本站上传审核）.
     */
    private void validateChatBubbleMediaUrl(String mediaUrl) {
        boolean okMsg = isChatMediaUnderPrefix(mediaUrl, Constant.OSS_PATH_CHAT_MESSAGE);
        boolean okEmoji = isChatMediaUnderPrefix(mediaUrl, Constant.OSS_PATH_CHAT_EMOJI);
        boolean okShop = isChatMediaUnderPrefix(mediaUrl, Constant.OSS_PATH_EMOJI_SHOP);
        if (!okMsg && !okEmoji && !okShop) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_INVALID_OSS_URL));
        }
    }

    private void validateSendImageMediaUrl(String mediaUrl) {
        validateChatBubbleMediaUrl(mediaUrl);
    }

    private boolean isChatMediaUnderPrefix(String mediaUrl, String allowedPathPrefix) {
        return ossConfig.matchesPublicObjectUrl(mediaUrl, allowedPathPrefix);
    }

    /**
     * 校验聊天用 mediaUrl 是否落在我们 OSS 的指定子目录下.
     * 防止前端伪造外链或盗链(直接绕过本站上传接口的 AI 审核).
     */
    private void validateChatMediaUrl(String mediaUrl, String allowedPathPrefix) {
        if (!isChatMediaUnderPrefix(mediaUrl, allowedPathPrefix)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_INVALID_OSS_URL));
        }
    }

    /** 任何会改变会话快照的写操作都要清掉双方的会话缓存 */
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

    /**
     * 构造完整的会话列表（按对方聚合，最新的会话在前）
     * 优先读 Redis 5 分钟缓存，未命中则扫描全部相关消息一次性聚合并回写
     */
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
        List<Message> allMessages = messageMapper.selectList(new QueryWrapper<Message>().lambda()
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

    // 提取共同方法
    private @NonNull List<MessageSessionResponse> getMessageSessionResponses(Long userId, List<Message> allMessages) {
        Map<Long, MessageSessionResponse> sessionMap = new LinkedHashMap<>();
        for (Message msg : allMessages) {
            Long otherId = msg.getPostUserId().equals(userId) ? msg.getReceiveUserId() : msg.getPostUserId();
            sessionMap.computeIfAbsent(otherId, id -> {
                User other = userService.queryUserByUserId(id);
                MessageSessionResponse session = new MessageSessionResponse();
                session.setUnReadMessage(0L);
                session.setUser(new UserBriefVO(other));
                session.setLastMessage(sessionSummary(msg));
                session.setLastMessageTime(msg.getUpdateTime());
                return session;
            });
            // 对方发给我 + 状态未读 -> 累加未读数
            if (msg.getReceiveUserId().equals(userId)
                    && Objects.equals(msg.getState(), Constant.MESSAGE_STATE_UNREAD)) {
                MessageSessionResponse session = sessionMap.get(otherId);
                session.setUnReadMessage(session.getUnReadMessage() + 1);
            }
        }
        return new ArrayList<>(sessionMap.values());
    }
}
