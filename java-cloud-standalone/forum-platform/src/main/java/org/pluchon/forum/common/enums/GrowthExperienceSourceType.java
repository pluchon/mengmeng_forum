package org.pluchon.forum.common.enums;

// 成长经验来源类型
public enum GrowthExperienceSourceType {
    CHALLENGE("成长挑战"),
    CHECKIN("每日签到"),
    ACTIVITY("活动奖励");

    private final String label;

    GrowthExperienceSourceType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
