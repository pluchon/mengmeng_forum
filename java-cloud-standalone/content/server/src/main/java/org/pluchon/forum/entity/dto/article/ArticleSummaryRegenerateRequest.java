package org.pluchon.forum.entity.dto.article;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

// 帖子AI总结重新生成请求
@Data
public class ArticleSummaryRegenerateRequest {

    @NotNull(message = "帖子ID不能为空")
    private Long articleId;
}
