package org.example.forumdemo.service.impl.game.guard;

import org.springframework.stereotype.Component;

@Component
public class EmptyCellGuard implements GobangActionGuard {

    @Override
    public boolean supports(GobangActionType actionType) {
        return actionType == GobangActionType.MOVE;
    }

    @Override
    public int order() {
        return 60;
    }

    @Override
    public GobangGuardResult check(GobangActionContext context) {
        if (context.getRoom().getBoard()[context.getRow()][context.getCol()] == 0) {
            return GobangGuardResult.pass();
        }
        return GobangGuardResult.fail("该位置已经有棋子");
    }
}
