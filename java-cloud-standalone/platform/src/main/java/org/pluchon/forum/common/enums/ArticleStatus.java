package org.pluchon.forum.common.enums;

import lombok.Getter;

// 帖子状态
@Getter
public enum ArticleStatus {
    DRAFT((byte) 0, "草稿"),
    PENDING_AUDIT((byte) 1, "审核中"),
    APPROVED((byte) 2, "审核通过"),
    REJECTED((byte) 3, "审核未通过"),
    AUDIT_ERROR((byte) 4, "审核异常"),
    PUBLISHED((byte) 5, "已发布");

    private final byte code;
    private final String message;

    ArticleStatus(byte code, String message) {
        this.code = code;
        this.message = message;
    }

    public static boolean isPublished(Byte status) {
        return status != null && status == PUBLISHED.code;
    }

    /** 可发起"提交审核"的状态: 草稿 / 审核未通过 / 审核异常 / 已发布(回审) */
    public static boolean canSubmitForAudit(Byte status) {
        if (status == null){
            return false;
        }
        return status == DRAFT.code || status == REJECTED.code || status == AUDIT_ERROR.code || status == PUBLISHED.code;
    }

    /** 不允许在以下状态下修改帖子主体内容(标题/正文/封面/相册) */
    public static boolean isEditingLocked(Byte status) {
        return status != null && status == PENDING_AUDIT.code;
    }
}
