package org.pluchon.forum.entity.vo.message;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.pluchon.forum.entity.db.UserChatEmoji;

import java.util.Date;

// 用户聊天表情收藏 VO, 隐藏 deleteState 等内部字段.
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "用户聊天表情收藏返回对象")
public class UserChatEmojiResponse {

    private Long id;
    private String mediaUrl;
    private Byte mediaType;
    private String mediaMime;
    private Long mediaSize;
    private Long originMessageId;
    private Long originGroupMessageId;
    private Date createTime;

    public UserChatEmojiResponse(UserChatEmoji emoji) {
        this.id = emoji.getId();
        this.mediaUrl = emoji.getMediaUrl();
        this.mediaType = emoji.getMediaType();
        this.mediaMime = emoji.getMediaMime();
        this.mediaSize = emoji.getMediaSize();
        this.originMessageId = emoji.getOriginMessageId();
        this.originGroupMessageId = emoji.getOriginGroupMessageId();
        this.createTime = emoji.getCreateTime();
    }
}
