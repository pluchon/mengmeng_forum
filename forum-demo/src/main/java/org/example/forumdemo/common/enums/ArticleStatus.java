package org.example.forumdemo.common.enums;

import lombok.Getter;

/**
 * 帖子发布状态机.
 *
 * 异步审核 + 自动发布版本:
 *  - 用户保存草稿                  -> DRAFT
 *  - 用户点"提交审核"                -> PENDING_AUDIT (投递 MQ 给 Python LangGraph)
 *  - LangGraph 返回结果:
 *      * 通过                       -> APPROVED -> 立即扭转为 PUBLISHED (auto-publish)
 *      * 不通过                      -> REJECTED  (用户可修改后重新提交, 上限 3 次)
 *      * 服务异常 / 超时              -> AUDIT_ERROR (用户可重新提交, 计入 retry)
 *  - 已发布 (PUBLISHED) 后再编辑       -> PENDING_AUDIT, 走回审 (有语义缓存命中可秒过)
 *
 * 删除状态: delete_state=1, 与 status 正交.
 *
 * APPROVED 在自动发布模式下只是 result 消费瞬间的中间态, 不会停留在 DB 里;
 * 但仍保留此枚举值, 便于未来切手动发布模式时零 DDL.
 */
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
        if (status == null) return false;
        return status == DRAFT.code
                || status == REJECTED.code
                || status == AUDIT_ERROR.code
                || status == PUBLISHED.code;
    }

    /** 不允许在以下状态下修改帖子主体内容(标题/正文/封面/相册) */
    public static boolean isEditingLocked(Byte status) {
        return status != null && status == PENDING_AUDIT.code;
    }
}
