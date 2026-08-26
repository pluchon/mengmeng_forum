package org.pluchon.forum.entity.dto.message;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

// 图集消息内的单张图片
@Data
@Schema(description = "图集图片请求")
public class AlbumImageRequest {

    @NotBlank
    @Schema(description = "聊天图片上传接口返回的本站 OSS URL")
    private String mediaUrl;

    @Schema(description = "媒体 MIME，例如 image/jpeg 或 image/gif")
    private String mediaMime;

    @Schema(description = "媒体字节大小")
    private Long mediaSize;

    @Schema(description = "媒体像素宽")
    private Integer mediaWidth;

    @Schema(description = "媒体像素高")
    private Integer mediaHeight;
}
