package org.pluchon.forum.entity.dto.message;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

// 开关某个私信会话的免打扰
@Data
@Schema(description = "私信会话免打扰请求")
public class MessageSessionMuteRequest {

    @NotNull
    @Schema(description = "对方用户 ID")
    private Long peerUserId;

    @NotNull
    @Schema(description = "true 开启免打扰，false 关闭")
    private Boolean muted;
}
