package org.example.forumdemo.service.impl.lottery.guard;

import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.db.User;
import org.springframework.stereotype.Component;

@Component
public class DrawUserAvailableGuard implements LotteryDrawGuard {

    @Override
    public int order() {
        return 40;
    }

    @Override
    public LotteryDrawGuardResult check(LotteryDrawContext context) {
        if (!context.isResourcesResolved()) {
            return LotteryDrawGuardResult.pass();
        }
        User user = context.getLockedUser();
        if (user != null && (user.getDeleteState() == null || user.getDeleteState() == 0)) {
            return LotteryDrawGuardResult.pass();
        }
        return LotteryDrawGuardResult.fail(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
    }
}
