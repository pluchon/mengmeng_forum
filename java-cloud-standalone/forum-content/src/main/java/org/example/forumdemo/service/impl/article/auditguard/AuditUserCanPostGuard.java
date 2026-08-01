package org.example.forumdemo.service.impl.article.auditguard;

import org.example.forumdemo.common.utils.UserMuteGuard;
import org.springframework.stereotype.Component;

@Component
public class AuditUserCanPostGuard implements ArticleAuditSubmitGuard {

    @Override
    public int order() {
        return 10;
    }

    @Override
    public ArticleAuditSubmitGuardResult check(ArticleAuditSubmitContext context) {
        UserMuteGuard.assertCanPost(context.getAuthor());
        return ArticleAuditSubmitGuardResult.pass();
    }
}
