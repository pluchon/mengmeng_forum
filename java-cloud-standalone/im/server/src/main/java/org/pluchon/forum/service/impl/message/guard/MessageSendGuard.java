package org.pluchon.forum.service.impl.message.guard;

public interface MessageSendGuard {

    boolean supports(MessageSendType sendType);

    int order();

    MessageSendGuardResult check(MessageSendContext context);
}
