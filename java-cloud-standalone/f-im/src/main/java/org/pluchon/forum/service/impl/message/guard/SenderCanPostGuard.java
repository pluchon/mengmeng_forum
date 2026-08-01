package org.pluchon.forum.service.impl.message.guard;

import org.pluchon.forum.common.utils.UserMuteGuard;
import org.pluchon.forum.entity.db.User;
import org.pluchon.forum.service.interfaces.user.UserService;
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
