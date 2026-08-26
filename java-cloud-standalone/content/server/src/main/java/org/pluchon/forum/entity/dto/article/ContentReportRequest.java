package org.pluchon.forum.entity.dto.article;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

// 帖子或评论举报请求
@Data
public class ContentReportRequest {

    @NotBlank
    private String targetType;

    @NotNull
    private Long targetId;

    @NotBlank
    @Size(min = 5, max = 200)
    private String reason;
}
