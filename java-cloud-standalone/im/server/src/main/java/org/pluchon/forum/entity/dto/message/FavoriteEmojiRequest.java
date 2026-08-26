package org.pluchon.forum.entity.dto.message;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

// 收藏表情请求
@Data
@Schema(description = "收藏表情请求")
public class FavoriteEmojiRequest {

    // 表情图片链接
    @Schema(description = "表情图片链接")
    private String mediaUrl;

    // 媒体类型
    @Schema(description = "类型，0静态图 1GIF", example = "0")
    private Byte mediaType;

    // 媒体 MIME 类型
    @Schema(description = "媒体类型，例如 image/jpeg")
    private String mediaMime;

    // 字节大小
    @Schema(description = "字节大小")
    private Long mediaSize;

    // 来源私信消息ID
    @Schema(description = "来源私信消息ID")
    private Long originMessageId;

    // 来源群聊消息ID
    @Schema(description = "来源群聊消息ID")
    private Long originGroupMessageId;
}
