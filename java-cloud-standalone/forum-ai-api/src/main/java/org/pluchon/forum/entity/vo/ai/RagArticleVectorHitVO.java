package org.pluchon.forum.entity.vo.ai;

import lombok.Data;

// RAG 向量召回单条命中（帖子）
@Data
public class RagArticleVectorHitVO {

    private Long articleId;
    private Double score;
}
