package org.pluchon.forum.entity.dto.message;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 收藏表情请求.
 * 两种来源:
 * 1) 自上传: 先调 /file/uploadChatEmoji 拿 URL, 再调 /message/emoji/favorite, originMessageId 留空;
 * 2) 收藏他人聊天图片: 直接传消息中的 mediaUrl + originMessageId；URL 可为会话临时图目录或表情库目录（与对方发送方式一致）.
 */
@Data
@Schema(description = "收藏表情请求")
public class FavoriteEmojiRequest {

    @Schema(description = "表情图URL, 必须是本站 OSS 上的 URL")
    private String mediaUrl;

    @Schema(description = "类型: 0静态图 1GIF", example = "0")
    private Byte mediaType;

    @Schema(description = "MIME, 例如 image/jpeg")
    private String mediaMime;

    @Schema(description = "字节大小, 可选")
    private Long mediaSize;

    @Schema(description = "来源消息ID, 自上传则不传")
    private Long originMessageId;
}
