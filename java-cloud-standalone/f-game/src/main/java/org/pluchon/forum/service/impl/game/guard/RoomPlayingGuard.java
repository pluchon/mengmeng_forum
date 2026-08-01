package org.pluchon.forum.service.impl.game.guard;

import org.pluchon.forum.service.impl.game.GameConstants;
import org.springframework.stereotype.Component;

@Component
public class RoomPlayingGuard implements GobangActionGuard {

    @Override
    public boolean supports(GobangActionType actionType) {
        return true;
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public GobangGuardResult check(GobangActionContext context) {
        if (GameConstants.ROOM_PLAYING.equals(context.getRoom().getRoomStatus())) {
            return GobangGuardResult.pass();
        }
        if (context.getActionType() == GobangActionType.CHAT) {
            return GobangGuardResult.fail("当前对战已经结束，不能发送消息或表情包");
        }
        if (context.getActionType() == GobangActionType.SURRENDER) {
            return GobangGuardResult.fail("当前对战已经结束，不能认输");
        }
        return GobangGuardResult.fail("当前对战已经结束，不能继续落子");
    }
}
