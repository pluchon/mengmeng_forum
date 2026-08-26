package org.pluchon.forum.entity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

// 新标签 AI 高相似性确认请求
@Data
public class AiArticleTagSimilarityRequest {

    // 待新增标签名
    @NotBlank
    private String proposedName;

    // 语义初筛后的已有标签
    @NotEmpty
    private List<AiArticleTagCandidateDTO> candidates;
}
