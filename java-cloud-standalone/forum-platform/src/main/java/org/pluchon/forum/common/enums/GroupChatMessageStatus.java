package org.pluchon.forum.common.enums;

import lombok.Getter;

// 群消息状态
@Getter
public enum GroupChatMessageStatus {
    NORMAL((byte) 0),
    REPORTED_HIDDEN((byte) 1),
    DELETED((byte) 2);

    private final Byte code;

    GroupChatMessageStatus(Byte code) {
        this.code = code;
    }
}
