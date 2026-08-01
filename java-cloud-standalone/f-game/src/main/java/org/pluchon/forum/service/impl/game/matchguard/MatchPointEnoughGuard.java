package org.pluchon.forum.service.impl.game.matchguard;

import org.pluchon.forum.service.impl.game.GameConstants;
import org.springframework.stereotype.Component;

@Component
public class MatchPointEnoughGuard implements GobangMatchGuard {

    @Override
    public int order() {
        return 20;
    }

    @Override
    public GobangMatchGuardResult check(GobangMatchContext context) {
        if (context.getPoints() >= GameConstants.SCORE_DELTA) {
            return GobangMatchGuardResult.pass();
        }
        return GobangMatchGuardResult.fail(
                "论坛积分不足，至少需要 " + GameConstants.SCORE_DELTA + " 积分才能开始匹配"
        );
    }
}
