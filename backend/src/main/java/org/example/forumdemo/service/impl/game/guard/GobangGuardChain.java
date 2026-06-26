package org.example.forumdemo.service.impl.game.guard;

import org.example.forumdemo.service.impl.game.GobangRuleEngine;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class GobangGuardChain {

    private final List<GobangActionGuard> guards;

    public GobangGuardChain(List<GobangActionGuard> guards) {
        this.guards = guards.stream()
                .sorted(Comparator.comparingInt(GobangActionGuard::order))
                .toList();
    }

    public static GobangGuardChain defaultChain() {
        GobangRuleEngine ruleEngine = new GobangRuleEngine();
        return new GobangGuardChain(List.of(
                new RoomExistsGuard(),
                new RoomPlayingGuard(),
                new PlayerOnlyGuard(),
                new TurnGuard(),
                new MoveCoordinateGuard(ruleEngine),
                new EmptyCellGuard(),
                new ChatPayloadGuard()
        ));
    }

    public GobangGuardResult check(GobangActionContext context) {
        for (GobangActionGuard guard : guards) {
            if (!guard.supports(context.getActionType())) {
                continue;
            }
            GobangGuardResult result = guard.check(context);
            if (!result.isPassed()) {
                return result;
            }
        }
        return GobangGuardResult.pass();
    }
}
