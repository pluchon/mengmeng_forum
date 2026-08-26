package org.pluchon.forum.service.impl.lottery.guard;

import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.api.UserInternalVO;
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
        UserInternalVO user = context.getLockedUser();
        if (user != null) {
            return LotteryDrawGuardResult.pass();
        }
        return LotteryDrawGuardResult.fail(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
    }
}
