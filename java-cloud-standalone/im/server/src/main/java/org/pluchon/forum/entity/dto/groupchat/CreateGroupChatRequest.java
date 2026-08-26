package org.pluchon.forum.entity.dto.groupchat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

// 创建群聊请求
@Data
public class CreateGroupChatRequest {

    // 群名称
    @NotBlank
    @Size(max = 10)
    private String name;

    // 群简介
    private String intro;

    // 群头像 URL
    private String avatarUrl;

    // 群类型: 0公开 1私有
    @NotNull
    private Byte groupType;
}
