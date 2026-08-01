package org.pluchon.forum.api.content;

// AI 生图转存 OSS 的内部请求
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

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public String getOssPath() {
        return ossPath;
    }

    public void setOssPath(String ossPath) {
        this.ossPath = ossPath;
    }

    public String getBaseName() {
        return baseName;
    }

    public void setBaseName(String baseName) {
        this.baseName = baseName;
    }
}
