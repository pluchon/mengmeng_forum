package org.pluchon.forum.entity.dto;

import lombok.Data;

// 图片 AI 审核 URL 载荷（上传审图：传 OSS URL + objectKey，Python 优先 SDK 直读）
@Data
public class AiImageModerationUrlRequest {

    private String imageUrl;

    private String objectKey;
}
