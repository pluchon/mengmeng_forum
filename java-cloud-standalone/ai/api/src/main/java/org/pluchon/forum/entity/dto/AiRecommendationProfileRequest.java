package org.pluchon.forum.entity.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

// 用户推荐画像请求，仅包含脱敏聚合信号
@Data
public class AiRecommendationProfileRequest {

    private List<String> explicitBoards;

    private List<Map<String, Object>> recent7;

    private List<Map<String, Object>> recent14;

    private List<Map<String, Object>> negativeRecent7;

    private List<Map<String, Object>> negativeRecent14;
}
