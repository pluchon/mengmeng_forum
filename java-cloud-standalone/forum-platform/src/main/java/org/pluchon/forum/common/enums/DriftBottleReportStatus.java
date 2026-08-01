package org.pluchon.forum.common.enums;

import lombok.Getter;

// 漂流瓶举报状态
@Getter
public enum DriftBottleReportStatus {

    // 待处理
    PENDING((byte) 0),

    // 已处理
    RESOLVED((byte) 1),

    // 已驳回
    REJECTED((byte) 2);

    private final Byte code;

    DriftBottleReportStatus(Byte code) {
        this.code = code;
    }
}
