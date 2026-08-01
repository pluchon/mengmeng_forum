package org.example.forumdemo.service.impl.message.guard;

import org.example.forumdemo.common.result.Result;

public class MessageSendGuardResult {

    private static final MessageSendGuardResult PASSED = new MessageSendGuardResult(true, null);

    private final boolean passed;

    private final Result<?> errorResult;

    private MessageSendGuardResult(boolean passed, Result<?> errorResult) {
        this.passed = passed;
        this.errorResult = errorResult;
    }

    public static MessageSendGuardResult pass() {
        return PASSED;
    }

    public static MessageSendGuardResult fail(Result<?> errorResult) {
        return new MessageSendGuardResult(false, errorResult);
    }

    public boolean isPassed() {
        return passed;
    }

    public Result<?> getErrorResult() {
        return errorResult;
    }
}
