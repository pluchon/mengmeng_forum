package org.example.forumdemo.common.enums;

import lombok.Getter;

// 漂流瓶举报目标类型
@Getter
public enum DriftBottleReportTargetType {

    // 瓶子
    BOTTLE((byte) 0),

    // 评论
    COMMENT((byte) 1);

    private final Byte code;

    DriftBottleReportTargetType(Byte code) {
        this.code = code;
    }
}
