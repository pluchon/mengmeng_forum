package org.pluchon.forum.service.impl.game.matchguard;

import org.springframework.stereotype.Component;

@Component
public class MatchNotAlreadyQueuedGuard implements GobangMatchGuard {

    @Override
    public int order() {
        return 40;
    }

    @Override
    public GobangMatchGuardResult check(GobangMatchContext context) {
        if (context.isAlreadyQueued()) {
            return GobangMatchGuardResult.ok();
        }
        return GobangMatchGuardResult.pass();
    }
}
