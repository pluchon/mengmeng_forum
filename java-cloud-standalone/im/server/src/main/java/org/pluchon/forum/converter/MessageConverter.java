package org.pluchon.forum.converter;

import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.entity.db.Message;
import org.pluchon.forum.entity.db.MessageAlbumImage;
import org.pluchon.forum.entity.db.UserChatEmoji;
import org.pluchon.forum.entity.vo.message.MessageAlbumImageVO;
import org.pluchon.forum.entity.vo.message.MessageVO;
import org.pluchon.forum.entity.vo.message.UserChatEmojiResponse;

import java.util.List;

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
        vo.setAuditFailed(Constant.MESSAGE_STATE_AUDIT_FAILED.equals(message.getState()));
        if (Boolean.TRUE.equals(vo.getAuditFailed())) {
            vo.setContent("");
        }
        vo.setCreateTime(message.getCreateTime());
        if (message.getCreateTime() != null) {
            long recallWindowMillis = Constant.MESSAGE_RECALL_WINDOW_SECONDS * 1000L;
            vo.setRecallDeadline(new java.util.Date(message.getCreateTime().getTime() + recallWindowMillis));
        }
        vo.setUpdateTime(message.getUpdateTime());
        return vo;
    }

    public static MessageVO toMessageVO(Message message, List<MessageAlbumImage> albumImages) {
        MessageVO vo = toMessageVO(message);
        if (vo != null && albumImages != null && !albumImages.isEmpty()) {
            vo.setAlbumImages(albumImages.stream().map(MessageConverter::toAlbumImageVO).toList());
        }
        return vo;
    }

    private static MessageAlbumImageVO toAlbumImageVO(MessageAlbumImage image) {
        MessageAlbumImageVO vo = new MessageAlbumImageVO();
        vo.setId(image.getId());
        vo.setMediaUrl(image.getMediaUrl());
        vo.setMediaMime(image.getMediaMime());
        vo.setMediaSize(image.getMediaSize());
        vo.setMediaWidth(image.getMediaWidth());
        vo.setMediaHeight(image.getMediaHeight());
        vo.setSortOrder(image.getSortOrder());
        return vo;
    }

    public static UserChatEmojiResponse toEmojiResponse(UserChatEmoji emoji) {
        return emoji == null ? null : new UserChatEmojiResponse(emoji);
    }
}
