package org.pluchon.forum.common.enums;

import lombok.Getter;

// 群聊状态
@Getter
public enum GroupChatStatus {
    NORMAL((byte) 0),
    FULL((byte) 1),
    OVER_LIMIT_LOCKED((byte) 2),
    DISSOLVED((byte) 3),
    BANNED((byte) 4),
    // 建群后先落库、再异步送审：审核期间不出现在群列表，也不能发言
    PENDING_AUDIT((byte) 5),
    AUDIT_FAILED((byte) 6);

    private final Byte code;

    GroupChatStatus(Byte code) {
        this.code = code;
    }
}
