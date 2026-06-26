package org.example.forumdemo.service.impl.lottery.guard;

public interface LotteryDrawGuard {

    int order();

    LotteryDrawGuardResult check(LotteryDrawContext context);
}
