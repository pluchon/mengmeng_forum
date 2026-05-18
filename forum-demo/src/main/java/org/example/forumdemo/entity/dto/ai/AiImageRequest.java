package org.example.forumdemo.entity.dto.ai;

import lombok.Data;

@Data
public class AiImageRequest {
    private String prompt;
    /** normal | premium */
    private String quality;
    /** 陪伴助手画图会话 id（可选） */
    private String sessionId;
}
