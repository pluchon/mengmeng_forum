package org.example.forumdemo.entity.dto.recommendation;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

// 提交“不想看这篇”反馈请求
@Data
public class NotInterestedArticleRequest {

    // 帖子ID
    @NotNull(message = "帖子ID不能为空")
    @Positive(message = "帖子ID必须为正数")
    private Long articleId;
}
