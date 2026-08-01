package org.example.forumdemo.service.impl.game.guard;

import org.example.forumdemo.service.impl.game.GobangRuleEngine;
import org.springframework.stereotype.Component;

@Component
public class MoveCoordinateGuard implements GobangActionGuard {

    private final GobangRuleEngine gobangRuleEngine;

    public MoveCoordinateGuard(GobangRuleEngine gobangRuleEngine) {
        this.gobangRuleEngine = gobangRuleEngine;
    }

    @Override
    public boolean supports(GobangActionType actionType) {
        return actionType == GobangActionType.MOVE;
    }

    @Override
    public int order() {
        return 50;
    }

    @Override
    public GobangGuardResult check(GobangActionContext context) {
        if (gobangRuleEngine.inBoard(context.getRow(), context.getCol())) {
            return GobangGuardResult.pass();
        }
        return GobangGuardResult.fail("落子坐标不合法");
    }
}
