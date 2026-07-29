package org.example.forumdemo.entity.dto.recommendation;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

// 提交“不想看这篇”反馈请求
@Data
public class NotInterestedArticleRequest {

    // 帖子ID
    @NotNull(message = "帖子ID不能为空")
    @Positive(message = "帖子ID必须为正数")
    private Long articleId;

    // 用户选择的反馈原因
    @NotBlank(message = "不感兴趣原因不能为空")
    private String reasonCode;

    // 用户补充的其他原因
    @Size(max = 200, message = "其他原因不能超过200字")
    private String reasonDetail;
}
