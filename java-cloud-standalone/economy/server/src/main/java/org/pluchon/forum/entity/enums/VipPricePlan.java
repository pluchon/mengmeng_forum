package org.pluchon.forum.entity.enums;

// 定价体系：首购价一辈子只有一次，但升级差价要沿用当初买 PRO 时的那一套
public enum VipPricePlan {

    FIRST_PURCHASE("first_purchase", "首购价"),
    NORMAL("normal", "原价");

    private final String code;
    private final String label;

    VipPricePlan(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static VipPricePlan fromCode(String code) {
        return FIRST_PURCHASE.code.equals(code) ? FIRST_PURCHASE : NORMAL;
    }
}
