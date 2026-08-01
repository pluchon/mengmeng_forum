package org.example.forumdemo.common.enums;

import lombok.Getter;

// 用户不感兴趣反馈原因
@Getter
public enum RecommendationFeedbackReason {
    UNRELATED("UNRELATED"),
    TOPIC("TOPIC"),
    AUTHOR("AUTHOR"),
    DUPLICATE("DUPLICATE"),
    LOW_QUALITY("LOW_QUALITY"),
    OTHER("OTHER");

    private final String code;

    RecommendationFeedbackReason(String code) {
        this.code = code;
    }

    public static boolean isSupported(String code) {
        if (code == null) {
            return false;
        }
        for (RecommendationFeedbackReason value : values()) {
            if (value.code.equals(code)) {
                return true;
            }
        }
        return false;
    }
}
