package org.pluchon.forum.common.enums;

import lombok.Getter;

// 漂流瓶与评论状态
@Getter
public enum DriftBottleStatus {

    // 可见
    VISIBLE((byte) 0, "可见"),

    // 已隐藏
    HIDDEN((byte) 1, "已隐藏"),

    // 已删除
    DELETED((byte) 2, "已删除");

    private final Byte code;
    private final String text;

    DriftBottleStatus(Byte code, String text) {
        this.code = code;
        this.text = text;
    }

    public static String textOf(Byte code) {
        for (DriftBottleStatus status : values()) {
            if (status.code.equals(code)) {
                return status.text;
            }
        }
        return "";
    }
}
