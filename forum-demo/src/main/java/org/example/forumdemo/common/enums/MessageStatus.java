package org.example.forumdemo.common.enums;

import lombok.Getter;

// 私信状态枚举，code 值与 Constant.MESSAGE_STATE_* 字节常量对齐
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

    /** 根据 code 反查枚举，找不到返回 null */
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
