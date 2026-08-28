package org.pluchon.forum.common.enums;

import lombok.Getter;

// 群加入申请状态
@Getter
public enum GroupChatJoinRequestStatus {
    PENDING((byte) 0),
    APPROVED((byte) 1),
    REJECTED((byte) 2),
    OBSOLETE((byte) 3);

    private final Byte code;

    GroupChatJoinRequestStatus(Byte code) {
        this.code = code;
    }
}
