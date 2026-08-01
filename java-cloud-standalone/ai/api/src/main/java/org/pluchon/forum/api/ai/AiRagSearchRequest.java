package org.pluchon.forum.api.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.Map;

// AI 向量检索内部请求
@Data
public class AiRagSearchRequest {

    @NotBlank
    private String query;

    private List<Map<String, Object>> candidates;
}
