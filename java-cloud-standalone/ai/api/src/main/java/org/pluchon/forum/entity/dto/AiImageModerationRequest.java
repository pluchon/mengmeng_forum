package org.pluchon.forum.entity.dto;

import lombok.Data;

// 图片 AI 审核 JSON 载荷（跨服务 Feign 契约，避免 Multipart Feign 编码问题）
@Data
public class AiImageModerationRequest {

    private String contentBase64;

    private String filename;

    private String contentType;
}
