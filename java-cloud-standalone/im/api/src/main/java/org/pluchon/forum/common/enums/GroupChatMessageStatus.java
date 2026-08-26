package org.pluchon.forum.common.enums;

import lombok.Getter;

// 群消息状态
@Getter
public enum GroupChatMessageStatus {
    NORMAL((byte) 0),
    DELETED((byte) 2),
    RECALLED((byte) 3),
    // 文本内容审核未通过（仅发送方可见）
    AUDIT_FAILED((byte) 4);

    private final Byte code;

    GroupChatMessageStatus(Byte code) {
        this.code = code;
    }
}
