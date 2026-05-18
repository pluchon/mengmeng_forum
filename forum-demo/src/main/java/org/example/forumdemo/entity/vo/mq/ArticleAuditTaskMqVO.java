package org.example.forumdemo.entity.vo.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 帖子异步审核任务 MQ VO.
 * Java 投递到 forum.audit.article -> Python LangGraph 消费.
 *
 * Python 侧消费成功后, 将结果以 ArticleAuditResultMqVO 形式发回 forum.audit.result.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArticleAuditTaskMqVO {
    /** 审核任务唯一 ID(UUID), 用于跨服务幂等; 同时落库到 article.audit_task_id */
    private String taskId;
    /** 关联帖子 ID */
    private Long articleId;
    /** 帖子作者 ID, 通知系统消息时需要 */
    private Long userId;
    /** 帖子标题(用于通知文案 + 文本审核入参) */
    private String title;
    /** 帖子正文(已包含 HTML 标签, Python 侧 clean_html 后审) */
    private String content;
    /** 封面 URL, 可为空; 不为空则参与图片审核 */
    private String coverUrl;
    /** 相册图 URL 数组; 与封面合并送 vl 模型审核 */
    private List<String> imageUrls;
    /** 提交时间戳(ms); 用于 Python 侧观测 + 兜底超时丢弃 */
    private Long submittedAt;
}
