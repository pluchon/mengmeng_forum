package org.example.forumdemo.service.impl.article.auditguard;

import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.result.Result;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class AuditAuthorGuard implements ArticleAuditSubmitGuard {

    @Override
    public int order() {
        return 30;
    }

    @Override
    public ArticleAuditSubmitGuardResult check(ArticleAuditSubmitContext context) {
        if (!Objects.equals(context.getArticle().getUserId(), context.getLoginUserId())) {
            return ArticleAuditSubmitGuardResult.fail(Result.fail(ResultCode.FAILED_AUDIT_NOT_AUTHOR));
        }
        return ArticleAuditSubmitGuardResult.pass();
    }
}
