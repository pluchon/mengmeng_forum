package org.example.forumdemo.entity.dto.article;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

// 关闭问答帖请求
@Data
public class CloseQuestionRequest {

    // 问答帖 ID
    @NotNull
    private Long articleId;
}
