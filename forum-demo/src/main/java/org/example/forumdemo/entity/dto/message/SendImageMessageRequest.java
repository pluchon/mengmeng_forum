package org.example.forumdemo.entity.dto.message;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 发送图片消息请求.
 * 注意: 图片消息不允许同时携带文字; 服务端会强制把 content 置空.
 * 流程: 前端先调 /file/uploadChatImage 拿到 OSS URL, 再用此请求体调 /message/sendImage
 */
@Data
@Schema(description = "发送图片/GIF 消息请求")
public class SendImageMessageRequest {

    @Schema(description = "接收者用户ID", example = "20")
    private Long receiveUserId;

    @Schema(description = "消息类型: 1图片 2GIF", example = "1")
    private Byte messageType;

    @Schema(description = "媒体URL, 必须是 /file/uploadChatImage 返回的本站 URL")
    private String mediaUrl;

    @Schema(description = "媒体MIME, 例如 image/jpeg")
    private String mediaMime;

    @Schema(description = "媒体字节大小, 仅做记录")
    private Long mediaSize;

    @Schema(description = "媒体像素宽 (前端在 onload 后回填; 缺省允许)")
    private Integer mediaWidth;

    @Schema(description = "媒体像素高 (前端在 onload 后回填; 缺省允许)")
    private Integer mediaHeight;
}
