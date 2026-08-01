package org.example.forumdemo.service.impl.game.guard;

import org.springframework.stereotype.Component;

@Component
public class ChatPayloadGuard implements GobangActionGuard {

    @Override
    public boolean supports(GobangActionType actionType) {
        return actionType == GobangActionType.CHAT;
    }

    @Override
    public int order() {
        return 70;
    }

    @Override
    public GobangGuardResult check(GobangActionContext context) {
        String type = context.chatMessageType();
        String content = context.chatContent();
        if ("TEXT".equals(type) && (content.isBlank() || content.length() > 200)) {
            return GobangGuardResult.fail("聊天内容不能为空且不能超过 200 字");
        }
        if (!"TEXT".equals(type) && !"EMOJI".equals(type)) {
            return GobangGuardResult.fail("不支持的聊天类型");
        }
        return GobangGuardResult.pass();
    }
}
