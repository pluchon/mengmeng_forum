package org.pluchon.forum.entity.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

// 帖子配乐 AI 推荐请求
@Data
public class AiMusicRecommendRequest {

    private Long userId;

    private String clientRequestId;

    private String title;

    private String content;

    private String editorMode;

    @NotEmpty
    private List<AiMusicCandidateDTO> candidates;
}
