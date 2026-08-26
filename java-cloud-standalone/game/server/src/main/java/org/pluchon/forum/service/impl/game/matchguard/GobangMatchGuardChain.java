package org.pluchon.forum.service.impl.game.matchguard;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class GobangMatchGuardChain {

    private final List<GobangMatchGuard> guards;

    public GobangMatchGuardChain(List<GobangMatchGuard> guards) {
        this.guards = guards.stream()
                .sorted(Comparator.comparingInt(GobangMatchGuard::order))
                .toList();
    }

    public static GobangMatchGuardChain defaultChain() {
        return new GobangMatchGuardChain(List.of(
                new MatchUserExistsGuard(),
                new MatchNotPlayingGuard(),
                new MatchNotAlreadyQueuedGuard()
        ));
    }

    public GobangMatchGuardResult check(GobangMatchContext context) {
        for (GobangMatchGuard guard : guards) {
            GobangMatchGuardResult result = guard.check(context);
            if (!result.isPass()) {
                return result;
            }
        }
        return GobangMatchGuardResult.pass();
    }
}
