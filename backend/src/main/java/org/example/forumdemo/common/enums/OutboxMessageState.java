package org.example.forumdemo.common.enums;

/**
 * MQ 本地消息表状态。
 */
public enum OutboxMessageState {

    PENDING(0, "待投递"),
    SENT(1, "已投递"),
    CONSUMED(2, "已消费"),
    FAILED(3, "投递失败"),
    DEAD(4, "死信");

    private final int code;
    private final String label;

    OutboxMessageState(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static OutboxMessageState fromCode(int code) {
        for (OutboxMessageState state : values()) {
            if (state.code == code) {
                return state;
            }
        }
        throw new IllegalArgumentException("未知 Outbox 状态: " + code);
    }
}
