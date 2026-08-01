package org.pluchon.forum.converter;

import org.pluchon.forum.entity.db.Message;
import org.pluchon.forum.entity.db.UserChatEmoji;
import org.pluchon.forum.entity.vo.message.MessageVO;
import org.pluchon.forum.entity.vo.message.UserChatEmojiResponse;

// 私信与表情收藏转换
public final class MessageConverter {

    private MessageConverter() {
    }

    public static MessageVO toMessageVO(Message message) {
        if (message == null) {
            return null;
        }
        MessageVO vo = new MessageVO();
        vo.setId(message.getId());
        vo.setPostUserId(message.getPostUserId());
        vo.setReceiveUserId(message.getReceiveUserId());
        vo.setMessageType(message.getMessageType());
        vo.setContent(message.getContent());
        vo.setMediaUrl(message.getMediaUrl());
        vo.setMediaMime(message.getMediaMime());
        vo.setMediaSize(message.getMediaSize());
        vo.setMediaWidth(message.getMediaWidth());
        vo.setMediaHeight(message.getMediaHeight());
        vo.setState(message.getState());
        vo.setCreateTime(message.getCreateTime());
        vo.setUpdateTime(message.getUpdateTime());
        return vo;
    }

    public static UserChatEmojiResponse toEmojiResponse(UserChatEmoji emoji) {
        return emoji == null ? null : new UserChatEmojiResponse(emoji);
    }
}
