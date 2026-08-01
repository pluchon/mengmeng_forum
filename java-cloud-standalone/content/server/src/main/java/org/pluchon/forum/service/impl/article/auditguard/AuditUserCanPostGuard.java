package org.pluchon.forum.service.impl.article.auditguard;

import org.pluchon.forum.service.impl.remote.ContentUserMuteGuard;
import org.springframework.stereotype.Component;

@Component
public class AuditUserCanPostGuard implements ArticleAuditSubmitGuard {

    @Override
    public int order() {
        return 10;
    }

    @Override
    public ArticleAuditSubmitGuardResult check(ArticleAuditSubmitContext context) {
        ContentUserMuteGuard.assertCanPost(context.getAuthor());
        return ArticleAuditSubmitGuardResult.pass();
    }
}
