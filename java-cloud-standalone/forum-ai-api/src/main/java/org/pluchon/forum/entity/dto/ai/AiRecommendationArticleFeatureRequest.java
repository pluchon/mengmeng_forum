package org.pluchon.forum.entity.dto.ai;

import lombok.Data;

// 公开帖子推荐特征请求
@Data
public class AiRecommendationArticleFeatureRequest {

    // 帖子ID
    private Long articleId;

    // 标题
    private String title;

    // 正文
    private String content;

    // 所属板块名称
    private String boardName;
}
