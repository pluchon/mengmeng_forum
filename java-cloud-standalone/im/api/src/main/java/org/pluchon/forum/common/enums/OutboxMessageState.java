package org.pluchon.forum.common.enums;

// MQ 本地消息表状态
public enum OutboxMessageState {

    PENDING(0),
    SENT(1),
    DEAD(4);

    private final int code;

    OutboxMessageState(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

}
