package org.example.forumdemo.service.impl.article.auditguard;

import org.example.forumdemo.common.enums.ArticleStatus;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.result.Result;
import org.springframework.stereotype.Component;

@Component
public class AuditStatusAllowedGuard implements ArticleAuditSubmitGuard {

    @Override
    public int order() {
        return 40;
    }

    @Override
    public ArticleAuditSubmitGuardResult check(ArticleAuditSubmitContext context) {
        if (!ArticleStatus.canSubmitForAudit(context.getArticle().getStatus())) {
            return ArticleAuditSubmitGuardResult.fail(Result.fail(ResultCode.FAILED_AUDIT_STATUS_INVALID));
        }
        return ArticleAuditSubmitGuardResult.pass();
    }
}
