package org.pluchon.forum.common.enums;

import lombok.Getter;

// 弹幕字号档位
@Getter
public enum DanmakuFontSize {
    SMALL((byte) 0),
    STANDARD((byte) 1);

    private final byte code;

    DanmakuFontSize(byte code) {
        this.code = code;
    }

    public static boolean isValid(Byte code) {
        if (code == null) {
            return false;
        }
        for (DanmakuFontSize item : values()) {
            if (item.code == code) {
                return true;
            }
        }
        return false;
    }
}
