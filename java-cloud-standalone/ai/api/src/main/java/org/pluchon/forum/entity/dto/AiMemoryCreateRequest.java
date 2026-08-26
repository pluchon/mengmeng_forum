package org.pluchon.forum.entity.dto;

import lombok.Data;

// 新增会员长期偏好记忆请求
@Data
public class AiMemoryCreateRequest {

    private Long sourceSessionId;

    private String memoryType;

    private String content;
}
