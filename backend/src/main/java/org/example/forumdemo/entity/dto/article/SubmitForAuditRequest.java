package org.example.forumdemo.entity.dto.article;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 帖子提交异步审核入参.
 * 调用前需保证: 标题/正文/(可选)封面/(可选)相册图均已落库.
 * 仅 DRAFT / REJECTED / AUDIT_ERROR / PUBLISHED 状态允许提交.
 */
@Data
@Schema(description = "帖子提交审核请求")
public class SubmitForAuditRequest {

    @NotNull(message = "articleId 不能为空")
    @Schema(description = "帖子ID", example = "12")
    private Long articleId;
}
