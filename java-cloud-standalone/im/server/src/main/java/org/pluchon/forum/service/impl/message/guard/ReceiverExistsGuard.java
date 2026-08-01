package org.pluchon.forum.service.impl.message.guard;

import org.pluchon.forum.service.interfaces.user.UserService;
import org.springframework.stereotype.Component;

@Component
public class ReceiverExistsGuard implements MessageSendGuard {

    private final UserService userService;

    public ReceiverExistsGuard(UserService userService) {
        this.userService = userService;
    }

    @Override
    public boolean supports(MessageSendType sendType) {
        return true;
    }

    @Override
    public int order() {
        return 40;
    }

    @Override
    public MessageSendGuardResult check(MessageSendContext context) {
        userService.queryUserByUserId(context.getReceiverUserId());
        return MessageSendGuardResult.pass();
    }
}
