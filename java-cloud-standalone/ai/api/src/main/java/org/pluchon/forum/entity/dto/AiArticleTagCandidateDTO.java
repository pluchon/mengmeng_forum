package org.pluchon.forum.entity.dto;

import lombok.Data;

// 帖子标签 AI 候选项
@Data
public class AiArticleTagCandidateDTO {

    // 标签主键
    private Long id;

    // 标签名称
    private String name;
}
