package org.example.forumdemo.common.enums;

import lombok.Getter;

// 推荐卡片的可解释来源
@Getter
public enum RecommendationReasonType {
    INTEREST("interest", "因为你选择了这个板块"),
    AI_PROFILE("ai_profile", "与你近期的内容偏好相近"),
    INTERACTION("interaction", "因为你互动过同板块内容"),
    FOLLOWING("following", "来自你关注的作者"),
    FRESH("fresh", "刚刚发布，社区正在讨论"),
    HOT("hot", "正在热门讨论中"),
    COMMUNITY("community", "社区正在讨论");

    private final String code;
    private final String message;

    RecommendationReasonType(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
