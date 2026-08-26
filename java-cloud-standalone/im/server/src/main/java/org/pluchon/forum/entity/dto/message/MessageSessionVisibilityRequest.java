package org.pluchon.forum.entity.dto.message;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

// 私信会话显示状态请求
@Data
public class MessageSessionVisibilityRequest {

    // 私信对方用户 ID
    @NotNull
    private Long peerUserId;
}
