package org.pluchon.forum.service.impl.message.guard;

import org.pluchon.forum.common.config.OssConfig;
import org.pluchon.forum.service.impl.remote.ImUserLookupService;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class MessageSendGuardChain {

    private final List<MessageSendGuard> guards;

    public MessageSendGuardChain(List<MessageSendGuard> guards) {
        this.guards = guards.stream()
                .sorted(Comparator.comparingInt(MessageSendGuard::order))
                .toList();
    }

    public static MessageSendGuardChain defaultChain(ImUserLookupService userLookupService, OssConfig ossConfig) {
        return new MessageSendGuardChain(List.of(
                new TextContentGuard(),
                new ImagePayloadGuard(),
                new NotSendToSelfGuard(),
                new SenderCanPostGuard(userLookupService),
                new ReceiverExistsGuard(userLookupService),
                new ChatMediaUrlGuard(ossConfig)
        ));
    }

    public MessageSendGuardResult check(MessageSendContext context) {
        for (MessageSendGuard guard : guards) {
            if (!guard.supports(context.getSendType())) {
                continue;
            }
            MessageSendGuardResult result = guard.check(context);
            if (!result.isPassed()) {
                return result;
            }
        }
        return MessageSendGuardResult.pass();
    }
}
