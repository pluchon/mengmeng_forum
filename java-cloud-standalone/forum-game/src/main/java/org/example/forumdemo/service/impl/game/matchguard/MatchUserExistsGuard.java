package org.example.forumdemo.service.impl.game.matchguard;

import org.springframework.stereotype.Component;

@Component
public class MatchUserExistsGuard implements GobangMatchGuard {

    @Override
    public int order() {
        return 10;
    }

    @Override
    public GobangMatchGuardResult check(GobangMatchContext context) {
        if (context.getUserId() != null && context.getUser() != null) {
            return GobangMatchGuardResult.pass();
        }
        return GobangMatchGuardResult.fail("用户不存在，无法开始匹配");
    }
}
