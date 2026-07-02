package org.example.forumdemo.common.enums;

import lombok.Getter;

// 群加入申请查看状态
@Getter
public enum GroupChatJoinRequestReadState {
    UNREAD((byte) 0),
    READ((byte) 1);

    private final Byte code;

    GroupChatJoinRequestReadState(Byte code) {
        this.code = code;
    }
}
