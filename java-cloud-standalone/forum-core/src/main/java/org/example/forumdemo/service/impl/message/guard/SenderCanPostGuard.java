package org.example.forumdemo.service.impl.message.guard;

import org.example.forumdemo.common.utils.UserMuteGuard;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.service.interfaces.user.UserService;
import org.springframework.stereotype.Component;

@Component
public class SenderCanPostGuard implements MessageSendGuard {

    private final UserService userService;

    public SenderCanPostGuard(UserService userService) {
        this.userService = userService;
    }

    @Override
    public boolean supports(MessageSendType sendType) {
        return true;
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public MessageSendGuardResult check(MessageSendContext context) {
        User sender = userService.queryUserByUserId(context.getSenderUserId());
        UserMuteGuard.assertCanPost(sender);
        return MessageSendGuardResult.pass();
    }
}
