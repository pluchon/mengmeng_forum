package org.pluchon.forum.service.impl.message.guard;

import org.pluchon.forum.api.auth.UserInternalVO;
import org.pluchon.forum.service.impl.remote.ImUserLookupService;
import org.pluchon.forum.service.impl.remote.ImUserMuteGuard;
import org.springframework.stereotype.Component;

@Component
public class SenderCanPostGuard implements MessageSendGuard {

    private final ImUserLookupService userLookupService;

    public SenderCanPostGuard(ImUserLookupService userLookupService) {
        this.userLookupService = userLookupService;
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
        UserInternalVO sender = userLookupService.queryUserByUserId(context.getSenderUserId());
        ImUserMuteGuard.assertCanPost(sender);
        return MessageSendGuardResult.pass();
    }
}
