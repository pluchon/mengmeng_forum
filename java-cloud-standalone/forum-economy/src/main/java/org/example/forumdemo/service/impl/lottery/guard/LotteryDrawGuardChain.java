package org.example.forumdemo.service.impl.lottery.guard;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class LotteryDrawGuardChain {

    private final List<LotteryDrawGuard> guards;

    public LotteryDrawGuardChain(List<LotteryDrawGuard> guards) {
        this.guards = guards.stream()
                .sorted(Comparator.comparingInt(LotteryDrawGuard::order))
                .toList();
    }

    public static LotteryDrawGuardChain defaultChain() {
        return new LotteryDrawGuardChain(List.of(
                new DrawUserIdGuard(),
                new DrawTimesGuard(),
                new DrawActivityAvailableGuard(),
                new DrawUserAvailableGuard()
        ));
    }

    public LotteryDrawGuardResult check(LotteryDrawContext context) {
        for (LotteryDrawGuard guard : guards) {
            LotteryDrawGuardResult result = guard.check(context);
            if (!result.isPassed()) {
                return result;
            }
        }
        return LotteryDrawGuardResult.pass();
    }
}
