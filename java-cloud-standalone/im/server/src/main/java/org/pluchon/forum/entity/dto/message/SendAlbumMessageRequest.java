package org.pluchon.forum.entity.dto.message;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

// 发送私信图集请求
@Data
@Schema(description = "发送图集私信请求")
public class SendAlbumMessageRequest {

    @NotNull
    @Schema(description = "接收者用户 ID")
    private Long receiveUserId;

    @Size(max = 500)
    @Schema(description = "可选的图集说明文字")
    private String content;

    @Valid
    @Size(min = 1, max = 10)
    @Schema(description = "按展示顺序排列的图片，最多十张")
    private List<AlbumImageRequest> images;
}
