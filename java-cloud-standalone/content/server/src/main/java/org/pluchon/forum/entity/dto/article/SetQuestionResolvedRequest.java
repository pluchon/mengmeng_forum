package org.pluchon.forum.entity.dto.article;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

// 作者切换问答解决状态：仅允许待解决 / 已解决
@Data
public class SetQuestionResolvedRequest {

    @NotNull
    private Long articleId;

    // true 已解决，false 未解决 待解决
    @NotNull
    private Boolean resolved;
}
