package org.example.forumdemo.common.enums;

import lombok.Getter;

// 问答帖解决状态
@Getter
public enum QuestionStatus {

    WAITING((byte) 0, "待解决"),
    RESOLVED((byte) 1, "已解决"),
    CLOSED((byte) 2, "已关闭");

    // 稳定存储编码
    private final byte code;

    // 中文展示名称
    private final String message;

    QuestionStatus(byte code, String message) {
        this.code = code;
        this.message = message;
    }

    public static QuestionStatus fromCode(Byte code) {
        if (code == null) {
            return null;
        }
        for (QuestionStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }
}
