package org.pluchon.forum.service.impl.game.guard;

import org.springframework.stereotype.Component;

@Component
public class PlayerOnlyGuard implements GobangActionGuard {

    @Override
    public boolean supports(GobangActionType actionType) {
        return actionType == GobangActionType.MOVE || actionType == GobangActionType.SURRENDER;
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public GobangGuardResult check(GobangActionContext context) {
        if (context.getRoom().contains(context.getUserId())) {
            return GobangGuardResult.pass();
        }
        if (context.getActionType() == GobangActionType.SURRENDER) {
            return GobangGuardResult.fail("观战玩家不能认输");
        }
        return GobangGuardResult.fail("观战玩家不能落子");
    }
}
