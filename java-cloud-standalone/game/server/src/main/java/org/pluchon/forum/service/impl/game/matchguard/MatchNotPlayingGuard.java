package org.pluchon.forum.service.impl.game.matchguard;

import org.pluchon.forum.service.impl.game.GameConstants;
import org.springframework.stereotype.Component;

@Component
public class MatchNotPlayingGuard implements GobangMatchGuard {

    @Override
    public int order() {
        return 30;
    }

    @Override
    public GobangMatchGuardResult check(GobangMatchContext context) {
        if (context.getProfile() != null
                && GameConstants.PROFILE_PLAYING.equals(context.getProfile().getCurrentStatus())) {
            return GobangMatchGuardResult.fail("你已经在对局中");
        }
        return GobangMatchGuardResult.pass();
    }
}
