package org.pluchon.forum.service.impl.message.guard;

import org.pluchon.forum.service.impl.remote.ImUserLookupService;
import org.springframework.stereotype.Component;

@Component
public class ReceiverExistsGuard implements MessageSendGuard {

    private final ImUserLookupService userLookupService;

    public ReceiverExistsGuard(ImUserLookupService userLookupService) {
        this.userLookupService = userLookupService;
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
        userLookupService.queryUserByUserId(context.getReceiverUserId());
        return MessageSendGuardResult.pass();
    }
}
