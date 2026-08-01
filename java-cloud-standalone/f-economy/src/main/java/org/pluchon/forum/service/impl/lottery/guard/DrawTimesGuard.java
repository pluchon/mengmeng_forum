package org.pluchon.forum.service.impl.lottery.guard;

import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.result.Result;
import org.springframework.stereotype.Component;

@Component
public class DrawTimesGuard implements LotteryDrawGuard {

    @Override
    public int order() {
        return 20;
    }

    @Override
    public LotteryDrawGuardResult check(LotteryDrawContext context) {
        int times = context.times();
        if (times == 1 || times == 10) {
            return LotteryDrawGuardResult.pass();
        }
        return LotteryDrawGuardResult.fail(Result.fail(ResultCode.FAILED_LOTTERY_TIMES_INVALID));
    }
}
