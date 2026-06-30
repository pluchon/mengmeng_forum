package org.example.forumdemo.common.enums;

import lombok.Getter;

// 群成员角色
@Getter
public enum GroupChatMemberRole {
    OWNER((byte) 0),
    MEMBER((byte) 1);

    private final Byte code;

    GroupChatMemberRole(Byte code) {
        this.code = code;
    }
}
