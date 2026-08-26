package org.pluchon.forum.entity.dto.article;

import lombok.Data;

// 帖子配乐 AI 推荐入参
@Data
public class MusicRecommendRequest {

    private String title;

    private String content;

    private String editorMode;

    private String clientRequestId;
}
