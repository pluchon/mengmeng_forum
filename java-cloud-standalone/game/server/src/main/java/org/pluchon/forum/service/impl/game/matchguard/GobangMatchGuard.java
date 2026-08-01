package org.pluchon.forum.service.impl.game.matchguard;

public interface GobangMatchGuard {

    int order();

    GobangMatchGuardResult check(GobangMatchContext context);
}
