package org.pluchon.forum.entity.enums;

// 会员支付状态
public enum VipPaymentState {

    PENDING((byte) 0, "待支付"),
    SUCCESS((byte) 1, "支付成功"),
    CLOSED((byte) 2, "已关闭");

    private final byte code;
    private final String label;

    VipPaymentState(byte code, String label) {
        this.code = code;
        this.label = label;
    }

    public byte getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static VipPaymentState fromCode(Byte code) {
        if (code == null) {
            return PENDING;
        }
        for (VipPaymentState state : values()) {
            if (state.code == code) {
                return state;
            }
        }
        return PENDING;
    }
}
