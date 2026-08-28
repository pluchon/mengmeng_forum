package org.pluchon.forum.common.enums;

import lombok.Getter;

// 群聊状态
@Getter
public enum GroupChatStatus {
    NORMAL((byte) 0),
    FULL((byte) 1),
    OVER_LIMIT_LOCKED((byte) 2),
    DISSOLVED((byte) 3),
    BANNED((byte) 4);

    private final Byte code;

    GroupChatStatus(Byte code) {
        this.code = code;
    }
}
