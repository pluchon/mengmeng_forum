package org.pluchon.forum.common.enums;

import lombok.Getter;

/**
 * AI 调用预记录状态：调用前写入，结算时更新。
 */
@Getter
public enum AiCallState {

    PENDING(0, "待调用"),
    SUCCESS(1, "成功"),
    FAILED(2, "失败"),
    TIMEOUT(3, "超时"),
    STOPPED(4, "用户停止"),
    DISCONNECTED(5, "连接断开");

    private final int code;
    private final String label;

    AiCallState(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public static AiCallState fromCode(int code) {
        for (AiCallState state : values()) {
            if (state.code == code) {
                return state;
            }
        }
        throw new IllegalArgumentException("未知 AI 调用状态: " + code);
    }
}
