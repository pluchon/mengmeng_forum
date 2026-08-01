package org.example.forumdemo.entity.vo.ai;

import lombok.Data;

import java.util.List;
import java.util.Map;

// 帖子推荐特征结果
@Data
public class AiRecommendationFeatureResultVO {

    // 帖子ID
    private Long articleId;

    // 协议版本
    private String featureVersion;

    // 主题及权重
    private List<Map<String, Object>> topics;

    // 摘要
    private String summary;

    // 内容指纹
    private String contentFingerprint;

    // 生成来源
    private String generatedBy;
}
