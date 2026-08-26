package org.pluchon.forum.entity.dto.groupchat;

import jakarta.validation.constraints.Size;
import lombok.Data;

// 修改群资料请求
@Data
public class UpdateGroupChatRequest {

    // 群名称
    @Size(max = 10)
    private String name;

    // 群简介
    private String intro;

    // 群头像 URL
    private String avatarUrl;

    // 群类型: 0公开 1私有
    private Byte groupType;
}
