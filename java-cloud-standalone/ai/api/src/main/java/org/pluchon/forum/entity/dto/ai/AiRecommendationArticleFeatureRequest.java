package org.pluchon.forum.entity.dto.ai;

import lombok.Data;

// 公开帖子推荐特征请求
@Data
public class AiRecommendationArticleFeatureRequest {

    private Long articleId;

    private String title;

    private String content;

    private String boardName;
}
