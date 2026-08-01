package org.pluchon.forum.entity.dto.article;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

// 采纳问答帖最佳答案请求
@Data
public class AcceptQuestionAnswerRequest {

    // 问答帖 ID
    @NotNull
    private Long articleId;

    // 被采纳的一级回答 ID
    @NotNull
    private Long replyId;
}
