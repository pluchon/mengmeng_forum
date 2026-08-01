package org.pluchon.forum.common.enums;

import lombok.Getter;

// 群消息类型
@Getter
public enum GroupChatMessageType {
    TEXT((byte) 0),
    EMOJI((byte) 1),
    IMAGE((byte) 2),
    VOICE((byte) 3),
    SYSTEM((byte) 9);

    private final Byte code;

    GroupChatMessageType(Byte code) {
        this.code = code;
    }

    public static GroupChatMessageType fromCode(Byte code) {
        if (code == null) {
            return null;
        }
        for (GroupChatMessageType value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}
