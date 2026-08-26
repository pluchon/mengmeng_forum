package org.pluchon.forum.entity.dto;

import lombok.Data;

// RAG 表情包向量索引请求
@Data
public class RagEmojiIndexDTO {

    private Long shopId;
    private String name;
    private String description;
    private String category;
    private String coverUrl;
}
