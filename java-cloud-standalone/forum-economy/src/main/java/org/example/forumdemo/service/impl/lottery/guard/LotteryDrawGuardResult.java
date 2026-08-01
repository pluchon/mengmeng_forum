package org.example.forumdemo.service.impl.lottery.guard;

import org.example.forumdemo.common.result.Result;

public class LotteryDrawGuardResult {

    private static final LotteryDrawGuardResult PASSED = new LotteryDrawGuardResult(true, null);

    private final boolean passed;

    private final Result<?> errorResult;

    private LotteryDrawGuardResult(boolean passed, Result<?> errorResult) {
        this.passed = passed;
        this.errorResult = errorResult;
    }

    public static LotteryDrawGuardResult pass() {
        return PASSED;
    }

    public static LotteryDrawGuardResult fail(Result<?> errorResult) {
        return new LotteryDrawGuardResult(false, errorResult);
    }

    public boolean isPassed() {
        return passed;
    }

    public Result<?> getErrorResult() {
        return errorResult;
    }
}
