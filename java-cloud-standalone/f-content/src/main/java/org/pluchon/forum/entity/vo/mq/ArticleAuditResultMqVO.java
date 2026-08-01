package org.pluchon.forum.entity.vo.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 帖子审核结果 MQ VO.
 * Python 端 LangGraph 跑完后回传到 forum.audit.result -> Java ForumConsumer 消费.
 *
 * finalStatus 枚举(字符串, 跨语言友好):
 *   APPROVED   - 通过, Java 侧扭转为 PUBLISHED + 系统消息 + 可选邮件
 *   REJECTED   - 不通过, 状态 REJECTED, 系统消息附拒绝原因
 *   AUDIT_ERROR - 服务异常 / 超时, 状态 AUDIT_ERROR, 系统消息提示重试
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArticleAuditResultMqVO {
    /** 与 task 一致的 UUID, 用于 Java 侧幂等去重 */
    private String taskId;
    /** 关联帖子 ID */
    private Long articleId;
    /** 帖子作者 ID, 通知收件人 */
    private Long userId;
    /** 最终状态: APPROVED / REJECTED / AUDIT_ERROR (字符串) */
    private String finalStatus;
    /** 结论文本(通过原因 / 拒绝理由 / 错误描述), 用户可见 */
    private String finalReason;
    /** 帖子标题快照, 用于通知文案 */
    private String title;
    /** 摘要(可选, 通过时才会生成), 可由 Java 写入 article_summary 缓存 */
    private String summary;
    /** 完成时间戳(ms) */
    private Long finishedAt;
}
