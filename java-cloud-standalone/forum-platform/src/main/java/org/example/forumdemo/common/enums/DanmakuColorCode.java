package org.example.forumdemo.common.enums;

import lombok.Getter;

// 弹幕预设颜色编码
@Getter
public enum DanmakuColorCode {
    WHITE((byte) 0),
    RED((byte) 1),
    YELLOW((byte) 2),
    GREEN((byte) 3),
    BLUE((byte) 4),
    PINK((byte) 5),
    ORANGE((byte) 6),
    PURPLE((byte) 7),
    CYAN((byte) 8);

    private final byte code;

    DanmakuColorCode(byte code) {
        this.code = code;
    }

    public static boolean isValid(Byte code) {
        if (code == null) {
            return false;
        }
        for (DanmakuColorCode item : values()) {
            if (item.code == code) {
                return true;
            }
        }
        return false;
    }
}
