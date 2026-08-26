package org.pluchon.forum.entity.vo.ai;

import lombok.Data;

// AI 新标签高相似性确认结果
@Data
public class AiHubArticleTagSimilarityResultVO {

    // 高度相似的已有标签主键
    private Long similarTagId;

    // 严格判重原因
    private String reason;
}
