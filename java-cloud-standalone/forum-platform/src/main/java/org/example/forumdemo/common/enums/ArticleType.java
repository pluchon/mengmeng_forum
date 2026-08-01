package org.example.forumdemo.common.enums;

import lombok.Getter;

// 帖子业务类型
@Getter
public enum ArticleType {

    NORMAL((byte) 0, "普通帖"),
    QUESTION((byte) 1, "问答帖");

    // 稳定存储编码
    private final byte code;

    // 中文展示名称
    private final String message;

    ArticleType(byte code, String message) {
        this.code = code;
        this.message = message;
    }

    public static ArticleType fromCode(Byte code) {
        if (code == null) {
            return NORMAL;
        }
        for (ArticleType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return null;
    }

    public static boolean isQuestion(Byte code) {
        return code != null && code == QUESTION.code;
    }
}
