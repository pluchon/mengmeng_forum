package org.pluchon.forum.service.impl.lottery.guard;

import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.entity.db.LotteryActivity;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class DrawActivityAvailableGuard implements LotteryDrawGuard {

    @Override
    public int order() {
        return 30;
    }

    @Override
    public LotteryDrawGuardResult check(LotteryDrawContext context) {
        if (!context.isResourcesResolved()) {
            return LotteryDrawGuardResult.pass();
        }
        LotteryActivity activity = context.getActivity();
        if (activity == null
                || (activity.getDeleteState() != null && activity.getDeleteState() != 0)
                || activity.getPhase() == null || activity.getPhase() != 1
                || activity.getStatus() == null || activity.getStatus() != 1) {
            return LotteryDrawGuardResult.fail(Result.fail(ResultCode.FAILED_LOTTERY_INACTIVE));
        }
        Date now = new Date();
        if (activity.getStartTime() != null && now.before(activity.getStartTime())) {
            return LotteryDrawGuardResult.fail(Result.fail(ResultCode.FAILED_LOTTERY_INACTIVE));
        }
        if (activity.getEndTime() != null && now.after(activity.getEndTime())) {
            return LotteryDrawGuardResult.fail(Result.fail(ResultCode.FAILED_LOTTERY_INACTIVE));
        }
        return LotteryDrawGuardResult.pass();
    }
}
