package org.pluchon.forum.common.enums;

import lombok.Getter;

// 群聊举报处理状态
@Getter
public enum GroupChatReportStatus {

    // 待处理
    PENDING((byte) 0),

    // 已处理
    HANDLED((byte) 1),

    // 已驳回
    REJECTED((byte) 2);

    private final Byte code;

    GroupChatReportStatus(Byte code) {
        this.code = code;
    }
}
