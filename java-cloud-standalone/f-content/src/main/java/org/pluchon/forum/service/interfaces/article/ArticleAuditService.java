package org.pluchon.forum.service.interfaces.article;

import org.pluchon.forum.entity.vo.article.AuditStatusResponse;
import org.pluchon.forum.entity.vo.mq.ArticleAuditResultMqVO;

// 帖子异步审核提交、回执与兜底扫描
public interface ArticleAuditService {

    String submitForAudit(Long articleId, Long loginUserId);

    void applyAuditResult(ArticleAuditResultMqVO result);

    AuditStatusResponse getAuditStatus(Long articleId, Long loginUserId);

    int sweepStuckAuditTasks();
}
