package org.pluchon.forum.entity.enums;

// 会员订单类型：三种的发货规则完全不同，不能合并
public enum VipOrderKind {

    // 当前无有效会员，从今天起算 30 天
    NEW("new", "新购"),

    // 同档续费，从原到期日往后接，不是从今天算
    RENEW("renew", "续费"),

    // PRO 升 MAX，到期日不变，只补差价
    UPGRADE("upgrade", "升级");

    private final String code;
    private final String label;

    VipOrderKind(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static VipOrderKind fromCode(String code) {
        if (code == null) {
            return NEW;
        }
        for (VipOrderKind kind : values()) {
            if (kind.code.equals(code)) {
                return kind;
            }
        }
        return NEW;
    }
}
