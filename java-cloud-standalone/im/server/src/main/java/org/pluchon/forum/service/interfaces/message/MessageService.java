package org.pluchon.forum.service.interfaces.message;

import org.pluchon.forum.entity.db.Message;
import org.pluchon.forum.entity.dto.message.FavoriteEmojiRequest;
import org.pluchon.forum.entity.dto.message.MessageReplyRequest;
import org.pluchon.forum.entity.dto.message.SendAlbumMessageRequest;
import org.pluchon.forum.entity.dto.message.SendImageMessageRequest;
import org.pluchon.forum.entity.dto.message.SendMessageRequest;
import org.pluchon.forum.entity.vo.message.MessageDetailResponse;
import org.pluchon.forum.entity.vo.message.MessageListResponse;
import org.pluchon.forum.entity.vo.message.MessageSessionResponse;
import org.pluchon.forum.entity.vo.message.MessageSessionSearchResponse;
import org.pluchon.forum.entity.vo.message.MessageVO;
import org.pluchon.forum.entity.vo.message.UserChatEmojiResponse;
import org.pluchon.forum.entity.vo.common.PageResult;

// 站内信
public interface MessageService {

    MessageVO send(SendMessageRequest req, Long sendUserId);

    MessageVO sendImage(SendImageMessageRequest req, Long sendUserId);

    MessageVO sendAlbum(SendAlbumMessageRequest req, Long sendUserId);

    UserChatEmojiResponse favoriteEmoji(FavoriteEmojiRequest req, Long userId);

    // 取消收藏 软删, 仅作用于自己的
    void cancelFavoriteEmoji(Long emojiId, Long userId);

    // 我的上传或聊天收藏表情分页列表
    PageResult<UserChatEmojiResponse> queryEmojiList(Long userId, String source, Integer pageNum, Integer pageSize);

    Long selectMessageUnRead(Long userId);

    // 我收到的所有站内信 分页
    PageResult<MessageListResponse> selectMessageListByUserIdWithPage(Long userId, Integer pageNum, Integer pageSize);

    void updateMessageStatusByMessageId(Long messageId, Byte status, Long loginUserId);

    // 撤回私信：仅发送者，且必须在 2 分钟时间窗口内
    void recallMessage(Long messageId, Long loginUserId);

    void replyMessage(MessageReplyRequest req, Long sendUserId);

    // 我的会话列表 分页，按对方聚合，最新一条在前
    PageResult<MessageSessionResponse> queryMessageSessionWithPage(Long userId, Integer pageNum, Integer pageSize);

    // 在当前用户可见的私信文本中搜索逻辑会话
    PageResult<MessageSessionSearchResponse> searchMessageSessions(Long userId, String keyword,
                                                                    Integer pageNum, Integer pageSize);

    // 隐藏当前用户视角下的私信会话
    void hideMessageSession(Long userId, Long peerUserId);

    // 恢复当前用户视角下的私信会话
    void restoreMessageSession(Long userId, Long peerUserId);

    // 查询当前用户主动隐藏的私信会话
    PageResult<MessageSessionResponse> queryHiddenMessageSessions(Long userId, Integer pageNum, Integer pageSize);

    // 与特定对方的聊天记录 分页
    PageResult<MessageDetailResponse> queryMessageDetailWithPage(Long userId, Long receiveId, Integer pageNum, Integer pageSize);

    // 按消息ID查询当前登录用户可见的单条消息 WebSocket通知后用于实时追加气泡
    MessageDetailResponse queryMessageDetailById(Long messageId, Long loginUserId);

    // 进入会话时把对方发给我的所有未读批量改为已读
    void markAllMessageReadBySender(Long selfId, Long senderId);

    // 共用查询 被 service 内部及上层校验复用
    Message queryMessageByMessageId(Long messageId);
}
