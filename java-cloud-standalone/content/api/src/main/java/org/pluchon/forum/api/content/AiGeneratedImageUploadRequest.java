package org.pluchon.forum.api.content;

import lombok.Getter;
import lombok.Setter;

// AI 生图转存 OSS 的内部请求
@Setter
@Getter
public class AiGeneratedImageUploadRequest {

    private Long userId;
    private String sourceUrl;
    private String ossPath;
    private String baseName;

    public AiGeneratedImageUploadRequest() {
    }

    public AiGeneratedImageUploadRequest(Long userId, String sourceUrl, String ossPath, String baseName) {
        this.userId = userId;
        this.sourceUrl = sourceUrl;
        this.ossPath = ossPath;
        this.baseName = baseName;
    }

}
