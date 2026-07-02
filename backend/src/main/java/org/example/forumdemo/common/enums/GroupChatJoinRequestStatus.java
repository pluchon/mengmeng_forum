package org.example.forumdemo.common.enums;

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

    public static GroupChatJoinRequestStatus fromCode(Byte code) {
        if (code == null) {
            return null;
        }
        for (GroupChatJoinRequestStatus value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}
