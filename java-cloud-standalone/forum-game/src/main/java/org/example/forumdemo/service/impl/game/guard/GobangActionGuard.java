package org.example.forumdemo.service.impl.game.guard;

public interface GobangActionGuard {

    boolean supports(GobangActionType actionType);

    int order();

    GobangGuardResult check(GobangActionContext context);
}
