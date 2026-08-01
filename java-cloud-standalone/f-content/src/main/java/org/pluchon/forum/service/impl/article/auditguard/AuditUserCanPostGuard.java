package org.pluchon.forum.service.impl.article.auditguard;

import org.pluchon.forum.common.utils.UserMuteGuard;
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
