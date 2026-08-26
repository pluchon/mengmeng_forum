package org.pluchon.forum.service.impl.message.guard;

import lombok.Getter;
import org.pluchon.forum.entity.dto.message.MessageReplyRequest;
import org.pluchon.forum.entity.dto.message.SendAlbumMessageRequest;
import org.pluchon.forum.entity.dto.message.SendImageMessageRequest;
import org.pluchon.forum.entity.dto.message.SendMessageRequest;

@Getter
public class MessageSendContext {

    private final MessageSendType sendType;

    private final Long senderUserId;

    private final Long receiverUserId;

    private final String content;

    private final Byte messageType;

    private final String mediaUrl;

    private MessageSendContext(
            MessageSendType sendType,
            Long senderUserId,
            Long receiverUserId,
            String content,
            Byte messageType,
            String mediaUrl
    ) {
        this.sendType = sendType;
        this.senderUserId = senderUserId;
        this.receiverUserId = receiverUserId;
        this.content = content;
        this.messageType = messageType;
        this.mediaUrl = mediaUrl;
    }

    public static MessageSendContext text(SendMessageRequest request, Long senderUserId) {
        return new MessageSendContext(
                MessageSendType.TEXT,
                senderUserId,
                request == null ? null : request.getReceiveUserId(),
                request == null ? null : request.getContent(),
                null,
                null
        );
    }

    public static MessageSendContext image(SendImageMessageRequest request, Long senderUserId) {
        return new MessageSendContext(
                MessageSendType.IMAGE,
                senderUserId,
                request == null ? null : request.getReceiveUserId(),
                null,
                request == null ? null : request.getMessageType(),
                request == null ? null : request.getMediaUrl()
        );
    }

    public static MessageSendContext album(SendAlbumMessageRequest request, Long senderUserId) {
        return new MessageSendContext(
                MessageSendType.ALBUM,
                senderUserId,
                request == null ? null : request.getReceiveUserId(),
                request == null ? null : request.getContent(),
                null,
                null
        );
    }

    public static MessageSendContext reply(MessageReplyRequest request, Long senderUserId) {
        return new MessageSendContext(
                MessageSendType.REPLY,
                senderUserId,
                request == null ? null : request.getReceiveId(),
                request == null ? null : request.getContent(),
                null,
                null
        );
    }

}
