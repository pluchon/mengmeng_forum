package org.example.forumdemo.common.enums;

import lombok.Getter;

// 私信状态
@Getter
public enum MessageStatus {
    UN_READ(0, "未读"),
    IS_READ(1, "已读"),
    RECALLED(2, "已撤回");

    final int code;
    final String message;

    MessageStatus(int code, String message) {
        this.code = code;
        this.message = message;
    }

    // 反查枚举
    public static MessageStatus fromCode(int code) {
        for (MessageStatus s : values()) {
            if (s.code == code){
                return s;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "MessageStatus:" + code + ",Message:" + message + ".\n";
    }
}
