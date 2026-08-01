package org.example.forumdemo.service.impl.article.auditguard;

import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.result.Result;
import org.springframework.stereotype.Component;

@Component
public class AuditRetryLimitGuard implements ArticleAuditSubmitGuard {

    @Override
    public int order() {
        return 50;
    }

    @Override
    public ArticleAuditSubmitGuardResult check(ArticleAuditSubmitContext context) {
        int retry = context.getArticle().getAuditRetryCount() == null
                ? 0
                : context.getArticle().getAuditRetryCount();
        if (retry >= Constant.ARTICLE_AUDIT_MAX_RETRY) {
            return ArticleAuditSubmitGuardResult.fail(Result.fail(ResultCode.FAILED_AUDIT_RETRY_LIMIT));
        }
        return ArticleAuditSubmitGuardResult.pass();
    }
}
