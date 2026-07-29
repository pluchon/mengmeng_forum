package org.example.forumdemo.entity.vo.ai;

import lombok.Data;

import java.util.List;
import java.util.Map;

// 用户推荐画像结果
@Data
public class AiRecommendationProfileResultVO {

    // 协议版本
    private String featureVersion;

    // 主题及权重
    private List<Map<String, Object>> topics;

    // 画像摘要
    private String summary;

    // 生成来源
    private String generatedBy;
}
