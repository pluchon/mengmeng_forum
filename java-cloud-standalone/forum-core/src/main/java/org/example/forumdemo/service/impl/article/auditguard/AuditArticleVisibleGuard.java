package org.example.forumdemo.service.impl.article.auditguard;

import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.result.Result;
import org.springframework.stereotype.Component;

@Component
public class AuditArticleVisibleGuard implements ArticleAuditSubmitGuard {

    private static final byte STATE_FORBIDDEN = 1;

    @Override
    public int order() {
        return 20;
    }

    @Override
    public ArticleAuditSubmitGuardResult check(ArticleAuditSubmitContext context) {
        if (context.getArticle() == null
                || (context.getArticle().getState() != null && context.getArticle().getState() == STATE_FORBIDDEN)) {
            return ArticleAuditSubmitGuardResult.fail(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
        return ArticleAuditSubmitGuardResult.pass();
    }
}
