package org.pluchon.forum.entity.dto.groupchat;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

// 禁言群成员请求
@Data
public class GroupMuteMemberRequest {

    // 被禁言用户 ID
    @NotNull
    private Long targetUserId;

    // 禁言分钟数，0 表示解除禁言
    @NotNull
    private Integer minutes;
}
