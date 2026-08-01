package org.example.forumdemo.entity.dto.ai;

import lombok.Data;

import java.util.List;

// RAG 帖子向量索引请求
@Data
public class RagArticleIndexDTO {

    private Long articleId;
    private String title;
    private String content;
    private Integer mediaType;
    private String videoUrl;
    private String coverUrl;
    private String summary;
    private String authorNickname;
    private List<String> tagNames;
}
