package org.pluchon.forum.entity.vo.ai;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class AiRecommendationFeatureResultVO {

    private Long articleId;

    private String featureVersion;

    private List<Map<String, Object>> topics;

    private String summary;

    private String contentFingerprint;

    private String generatedBy;
}
