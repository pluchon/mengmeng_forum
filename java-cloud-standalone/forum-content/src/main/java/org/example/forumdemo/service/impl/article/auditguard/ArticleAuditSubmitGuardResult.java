package org.example.forumdemo.service.impl.article.auditguard;

import org.example.forumdemo.common.result.Result;

public class ArticleAuditSubmitGuardResult {

    private static final ArticleAuditSubmitGuardResult PASSED = new ArticleAuditSubmitGuardResult(true, null);

    private final boolean passed;

    private final Result<?> errorResult;

    private ArticleAuditSubmitGuardResult(boolean passed, Result<?> errorResult) {
        this.passed = passed;
        this.errorResult = errorResult;
    }

    public static ArticleAuditSubmitGuardResult pass() {
        return PASSED;
    }

    public static ArticleAuditSubmitGuardResult fail(Result<?> errorResult) {
        return new ArticleAuditSubmitGuardResult(false, errorResult);
    }

    public boolean isPassed() {
        return passed;
    }

    public Result<?> getErrorResult() {
        return errorResult;
    }
}
