package org.example.forumdemo.service.impl.message.guard;

import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.result.Result;
import org.springframework.stereotype.Component;

@Component
public class NotSendToSelfGuard implements MessageSendGuard {

    @Override
    public boolean supports(MessageSendType sendType) {
        return true;
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public MessageSendGuardResult check(MessageSendContext context) {
        if (context.getSenderUserId() != null && context.getSenderUserId().equals(context.getReceiverUserId())) {
            return MessageSendGuardResult.fail(Result.fail(ResultCode.FAILED_SEND_MESSAGE_BY_MYSELF));
        }
        return MessageSendGuardResult.pass();
    }
}
