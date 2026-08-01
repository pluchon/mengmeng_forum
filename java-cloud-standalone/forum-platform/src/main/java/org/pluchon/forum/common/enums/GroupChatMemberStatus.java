package org.pluchon.forum.common.enums;

import lombok.Getter;

// 群成员状态
@Getter
public enum GroupChatMemberStatus {
    ACTIVE((byte) 0),
    LEFT((byte) 1),
    REMOVED((byte) 2);

    private final Byte code;

    GroupChatMemberStatus(Byte code) {
        this.code = code;
    }
}
