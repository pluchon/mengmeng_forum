package org.pluchon.forum.common.enums;

import lombok.Getter;

// 弹幕展示模式
@Getter
public enum DanmakuMode {
    SCROLL((byte) 0),
    TOP((byte) 1),
    BOTTOM((byte) 2);

    private final byte code;

    DanmakuMode(byte code) {
        this.code = code;
    }

    public static boolean isValid(Byte code) {
        if (code == null) {
            return false;
        }
        for (DanmakuMode item : values()) {
            if (item.code == code) {
                return true;
            }
        }
        return false;
    }
}
