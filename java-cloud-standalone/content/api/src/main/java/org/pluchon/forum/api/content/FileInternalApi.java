package org.pluchon.forum.api.content;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

// 文件域内部契约 纯 API，无 @FeignClient；AI 生图转存 OSS 收口到 content
public interface FileInternalApi {

    @PostMapping("/file/internal/upload-ai-generated")
    String uploadAiGeneratedImage(@RequestBody AiGeneratedImageUploadRequest request);
}
