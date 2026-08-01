package org.pluchon.forum.entity.dto.groupchat;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

// 修改群成员角色请求
@Data
public class UpdateGroupMemberRoleRequest {

    // 目标用户 ID
    @NotNull
    private Long targetUserId;

    // 目标角色: 1成员 2管理员
    @NotNull
    private Byte role;
}
