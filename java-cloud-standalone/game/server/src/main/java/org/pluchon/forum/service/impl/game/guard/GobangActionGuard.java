package org.pluchon.forum.service.impl.game.guard;

public interface GobangActionGuard {

    boolean supports(GobangActionType actionType);

    int order();

    GobangGuardResult check(GobangActionContext context);
}
