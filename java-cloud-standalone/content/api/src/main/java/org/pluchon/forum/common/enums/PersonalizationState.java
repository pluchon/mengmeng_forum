package org.pluchon.forum.common.enums;

import lombok.Getter;

// 个性化推荐开关状态
@Getter
public enum PersonalizationState {
    DISABLED((byte) 0),
    ENABLED((byte) 1);

    private final byte code;

    PersonalizationState(byte code) {
        this.code = code;
    }

    public static boolean isEnabled(Byte code) {
        return code != null && code == ENABLED.code;
    }
}
