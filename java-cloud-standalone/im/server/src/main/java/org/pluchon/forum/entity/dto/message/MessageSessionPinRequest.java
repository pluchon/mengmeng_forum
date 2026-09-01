package org.pluchon.forum.entity.dto.message;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

// 置顶/取消置顶私信会话的请求
@Data
@Schema(description = "私信会话置顶请求")
public class MessageSessionPinRequest {

    @NotNull
    @Schema(description = "对方用户 ID")
    private Long peerUserId;

    @NotNull
    @Schema(description = "true 置顶，false 取消置顶")
    private Boolean pinned;
}
