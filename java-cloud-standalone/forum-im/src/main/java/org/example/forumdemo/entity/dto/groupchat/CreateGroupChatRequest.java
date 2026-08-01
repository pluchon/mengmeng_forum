package org.example.forumdemo.entity.dto.groupchat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

// 创建群聊请求
@Data
public class CreateGroupChatRequest {

    // 群名称
    @NotBlank
    private String name;

    // 群简介
    private String intro;

    // 群头像 URL
    private String avatarUrl;

    // 群类型: 0公开 1私有
    @NotNull
    private Byte groupType;
}
