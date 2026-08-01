package org.example.forumdemo.service.impl.game.guard;

import org.springframework.stereotype.Component;

@Component
public class RoomExistsGuard implements GobangActionGuard {

    @Override
    public boolean supports(GobangActionType actionType) {
        return true;
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public GobangGuardResult check(GobangActionContext context) {
        if (context.getRoom() != null) {
            return GobangGuardResult.pass();
        }
        return GobangGuardResult.fail(switch (context.getActionType()) {
            case CHAT -> "当前对战已经结束，不能发送消息或表情包";
            case SURRENDER -> "当前对战已经结束，不能认输";
            case MOVE -> "当前对战已经结束，不能继续落子";
        });
    }
}
