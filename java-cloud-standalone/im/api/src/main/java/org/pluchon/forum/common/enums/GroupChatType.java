package org.pluchon.forum.common.enums;

import lombok.Getter;

// 群聊类型
@Getter
public enum GroupChatType {
    PUBLIC((byte) 0),
    PRIVATE((byte) 1);

    private final Byte code;

    GroupChatType(Byte code) {
        this.code = code;
    }

    public static GroupChatType fromCode(Byte code) {
        if (code == null) {
            return null;
        }
        for (GroupChatType value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}
