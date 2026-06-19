package org.example.forumdemo.service.interfaces.message;

import org.example.forumdemo.entity.db.Message;
import org.example.forumdemo.entity.db.UserChatEmoji;
import org.example.forumdemo.entity.dto.message.FavoriteEmojiRequest;
import org.example.forumdemo.entity.dto.message.MessageReplyRequest;
import org.example.forumdemo.entity.dto.message.SendImageMessageRequest;
import org.example.forumdemo.entity.dto.message.SendMessageRequest;
import org.example.forumdemo.entity.vo.message.MessageDetailResponse;
import org.example.forumdemo.entity.vo.message.MessageListResponse;
import org.example.forumdemo.entity.vo.message.MessageSessionResponse;
import org.example.forumdemo.entity.vo.message.UserChatEmojiResponse;
import org.example.forumdemo.entity.vo.common.PageResult;

import java.util.List;

/**
 * 站内信
 */
public interface MessageService {

    Message send(SendMessageRequest req, Long sendUserId);

    // 发送图片/GIF 消息: 完全独立的接口, 不接受 content 字段
    Message sendImage(SendImageMessageRequest req, Long sendUserId);

    // 收藏表情(自上传或来自他人聊天图片), 返回落库实体
    UserChatEmoji favoriteEmoji(FavoriteEmojiRequest req, Long userId);

    // 取消收藏(软删, 仅作用于自己的)
    void cancelFavoriteEmoji(Long emojiId, Long userId);

    // 我的表情收藏列表 (按收藏时间倒序; 数量级有限, 不分页)
    List<UserChatEmojiResponse> queryEmojiList(Long userId);

    Long selectMessageUnRead(Long userId);

    // 我收到的所有站内信（分页）
    PageResult<MessageListResponse> selectMessageListByUserIdWithPage(Long userId, Integer pageNum, Integer pageSize);

    void updateMessageStatusByMessageId(Long messageId, Byte status, Long loginUserId);

    // 撤回私信：仅发送者，且必须在 2 分钟时间窗口内
    void recallMessage(Long messageId, Long loginUserId);

    void replyMessage(MessageReplyRequest req, Long sendUserId);

    // 我的会话列表（分页，按对方聚合，最新一条在前）
    PageResult<MessageSessionResponse> queryMessageSessionWithPage(Long userId, Integer pageNum, Integer pageSize);

    // 与特定对方的聊天记录（分页）
    PageResult<MessageDetailResponse> queryMessageDetailWithPage(Long userId, Long receiveId, Integer pageNum, Integer pageSize);

    // 按消息ID查询当前登录用户可见的单条消息（WebSocket通知后用于实时追加气泡）
    MessageDetailResponse queryMessageDetailById(Long messageId, Long loginUserId);

    // 进入会话时把对方发给我的所有未读批量改为已读
    void markAllMessageReadBySender(Long selfId, Long senderId);

    // 共用查询（被 service 内部及上层校验复用）
    Message queryMessageByMessageId(Long messageId);
}
