package org.pluchon.forum.entity.dto.groupchat;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

// 邀请群成员请求
@Data
public class GroupInviteMemberRequest {

    // 被邀请用户 ID
    @NotNull
    private Long inviteeUserId;
}
