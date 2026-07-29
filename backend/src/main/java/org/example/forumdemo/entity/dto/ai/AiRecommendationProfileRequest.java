package org.example.forumdemo.entity.dto.ai;

import lombok.Data;

import java.util.List;
import java.util.Map;

// 用户推荐画像请求，仅包含脱敏聚合信号
@Data
public class AiRecommendationProfileRequest {

    // 显式兴趣板块名称
    private List<String> explicitBoards;

    // 近七天板块聚合信号
    private List<Map<String, Object>> recent7;

    // 第八至十四天板块聚合信号
    private List<Map<String, Object>> recent14;

    // 近七天不感兴趣板块聚合信号
    private List<Map<String, Object>> negativeRecent7;

    // 第八至十四天不感兴趣板块聚合信号
    private List<Map<String, Object>> negativeRecent14;
}
