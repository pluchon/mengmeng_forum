package org.pluchon.forum.service.impl.article.auditguard;

public interface ArticleAuditSubmitGuard {

    int order();

    ArticleAuditSubmitGuardResult check(ArticleAuditSubmitContext context);
}
