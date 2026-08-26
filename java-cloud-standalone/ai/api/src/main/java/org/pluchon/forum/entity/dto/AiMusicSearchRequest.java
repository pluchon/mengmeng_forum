package org.pluchon.forum.entity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

// 曲库 AI 搜索请求
@Data
public class AiMusicSearchRequest {

    private Long userId;

    private String clientRequestId;

    @NotBlank
    private String query;

    // all | title | artist | album
    private String scope;

    @NotEmpty
    private List<AiMusicCandidateDTO> candidates;
}
