package org.pluchon.forum.entity.dto.message;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

// 发送图片消息请求。可附带说明文字，与图集一致——文字会走同一套后审。
// 流程: 前端先调 /file/uploadChatImage 拿到 OSS URL, 再用此请求体调 /message/sendImage
@Data
@Schema(description = "发送图片/GIF 消息请求")
public class SendImageMessageRequest {

    @Schema(description = "接收者用户ID", example = "20")
    private Long receiveUserId;

    @Schema(description = "消息类型: 1图片 2GIF", example = "1")
    private Byte messageType;

    // 可选的图片说明文字。图集一直支持附带文字，单图不支持，于是「一张图 + 一句话」
    // 只能被迫当成图集发出去；补上之后两者才对得齐
    @Size(max = 500)
    @Schema(description = "可选的图片说明文字")
    private String content;

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

    @Schema(description = "商城表情包ID；发送商城表情时必填")
    private Long emojiShopId;
}
