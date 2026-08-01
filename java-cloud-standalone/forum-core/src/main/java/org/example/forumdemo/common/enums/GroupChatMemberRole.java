package org.example.forumdemo.common.enums;

import lombok.Getter;

// 群成员角色
@Getter
public enum GroupChatMemberRole {
    OWNER((byte) 0),
    MEMBER((byte) 1),
    ADMIN((byte) 2);

    private final Byte code;

    GroupChatMemberRole(Byte code) {
        this.code = code;
    }

    public static GroupChatMemberRole fromCode(Byte code) {
        if (code == null) {
            return null;
        }
        for (GroupChatMemberRole value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}
