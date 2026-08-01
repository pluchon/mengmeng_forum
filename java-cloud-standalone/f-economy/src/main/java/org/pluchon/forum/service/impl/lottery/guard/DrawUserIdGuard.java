package org.pluchon.forum.service.impl.lottery.guard;

import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.result.Result;
import org.springframework.stereotype.Component;

@Component
public class DrawUserIdGuard implements LotteryDrawGuard {

    @Override
    public int order() {
        return 10;
    }

    @Override
    public LotteryDrawGuardResult check(LotteryDrawContext context) {
        if (context.getUserId() != null && context.getUserId() > 0) {
            return LotteryDrawGuardResult.pass();
        }
        return LotteryDrawGuardResult.fail(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
    }
}
