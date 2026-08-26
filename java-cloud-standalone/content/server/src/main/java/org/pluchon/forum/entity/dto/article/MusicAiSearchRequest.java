package org.pluchon.forum.entity.dto.article;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

// 曲库 AI 搜索入参
@Data
public class MusicAiSearchRequest {

    @NotBlank
    private String query;

    // all | title | artist | album，与曲库普通搜索 scope 一致
    private String scope;

    private String clientRequestId;
}
