package org.example.forumdemo.service.interfaces.article;

import org.example.forumdemo.entity.vo.article.AuditStatusResponse;
import org.example.forumdemo.entity.vo.mq.ArticleAuditResultMqVO;

// 帖子异步审核提交、回执与兜底扫描
public interface ArticleAuditService {

    String submitForAudit(Long articleId, Long loginUserId, Boolean notifyEmail);

    void applyAuditResult(ArticleAuditResultMqVO result);

    AuditStatusResponse getAuditStatus(Long articleId, Long loginUserId);

    int sweepStuckAuditTasks();
}
