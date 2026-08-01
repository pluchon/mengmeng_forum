package org.example.forumdemo.common.enums;

import lombok.Getter;

// 群加入请求类型
@Getter
public enum GroupChatJoinRequestType {
    APPLY((byte) 0),
    INVITE((byte) 1);

    private final Byte code;

    GroupChatJoinRequestType(Byte code) {
        this.code = code;
    }
}
