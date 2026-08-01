package org.example.forumdemo.entity.vo.ai;

import lombok.Data;

// RAG 向量召回单条命中（用户）
@Data
public class RagUserVectorHitVO {

    private Long userId;
    private Double score;
}
