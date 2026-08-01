package org.example.forumdemo.service.impl.message.guard;

public interface MessageSendGuard {

    boolean supports(MessageSendType sendType);

    int order();

    MessageSendGuardResult check(MessageSendContext context);
}
