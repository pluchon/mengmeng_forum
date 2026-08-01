package org.pluchon.forum.common.enums;

import lombok.Getter;

// 群聊成员提醒模式
@Getter
public enum GroupChatNotifyMode {
    NORMAL((byte) 0),
    MENTION_ONLY((byte) 1),
    NONE((byte) 2);

    private final Byte code;

    GroupChatNotifyMode(Byte code) {
        this.code = code;
    }

    public static GroupChatNotifyMode fromCode(Byte code) {
        for (GroupChatNotifyMode mode : values()) {
            if (mode.code.equals(code)) {
                return mode;
            }
        }
        return null;
    }
}
