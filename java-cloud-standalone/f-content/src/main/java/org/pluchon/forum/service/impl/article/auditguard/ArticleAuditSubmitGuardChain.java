package org.pluchon.forum.service.impl.article.auditguard;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class ArticleAuditSubmitGuardChain {

    private final List<ArticleAuditSubmitGuard> guards;

    public ArticleAuditSubmitGuardChain(List<ArticleAuditSubmitGuard> guards) {
        this.guards = guards.stream()
                .sorted(Comparator.comparingInt(ArticleAuditSubmitGuard::order))
                .toList();
    }

    public static ArticleAuditSubmitGuardChain defaultChain() {
        return new ArticleAuditSubmitGuardChain(List.of(
                new AuditUserCanPostGuard(),
                new AuditArticleVisibleGuard(),
                new AuditAuthorGuard(),
                new AuditStatusAllowedGuard(),
                new AuditRetryLimitGuard()
        ));
    }

    public ArticleAuditSubmitGuardResult check(ArticleAuditSubmitContext context) {
        for (ArticleAuditSubmitGuard guard : guards) {
            ArticleAuditSubmitGuardResult result = guard.check(context);
            if (!result.isPassed()) {
                return result;
            }
        }
        return ArticleAuditSubmitGuardResult.pass();
    }
}
