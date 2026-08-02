package org.pluchon.forum.entity.vo.ai;

import lombok.Data;

import java.util.List;
import java.util.Map;

// 用户推荐画像结果
@Data
public class AiRecommendationProfileResultVO {

    private String featureVersion;

    private List<Map<String, Object>> topics;

    private String summary;

    private List<Map<String, Object>> avoidTopics;

    private String generatedBy;
}
