package org.example.forumdemo.service.impl.game.guard;

import org.springframework.stereotype.Component;

@Component
public class TurnGuard implements GobangActionGuard {

    @Override
    public boolean supports(GobangActionType actionType) {
        return actionType == GobangActionType.MOVE;
    }

    @Override
    public int order() {
        return 40;
    }

    @Override
    public GobangGuardResult check(GobangActionContext context) {
        if (context.getUserId().equals(context.getRoom().getCurrentTurnUserId())) {
            return GobangGuardResult.pass();
        }
        return GobangGuardResult.fail("还没轮到你落子");
    }
}
