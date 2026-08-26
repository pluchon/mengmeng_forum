package org.pluchon.forum.entity.dto.article;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

// 采纳问答回答请求 一级回答 / 楼中楼二选一，可多条采纳
@Data
public class AcceptQuestionAnswerRequest {

    @NotNull
    private Long articleId;

    // 一级回答 ID 与 subReplyId 二选一
    private Long replyId;

    // 楼中楼 ID 与 replyId 二选一
    private Long subReplyId;
}
