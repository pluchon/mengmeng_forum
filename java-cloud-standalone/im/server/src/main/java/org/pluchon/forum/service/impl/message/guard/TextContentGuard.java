package org.pluchon.forum.service.impl.message.guard;

import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.service.remote.ImAiGatewayService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class TextContentGuard implements MessageSendGuard {

    @Autowired
    private ImAiGatewayService imAiGatewayService;

    @Override
    public boolean supports(MessageSendType sendType) {
        return sendType == MessageSendType.TEXT || sendType == MessageSendType.REPLY;
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public MessageSendGuardResult check(MessageSendContext context) {
        String violation = imAiGatewayService.validateText(context.getContent());
        if (violation != null) {
            return MessageSendGuardResult.fail(Result.fail(ResultCode.FAILED_CONTENT_VIOLATION, violation));
        }
        if (context.getSendType() == MessageSendType.REPLY && !StringUtils.hasLength(context.getContent())) {
            return MessageSendGuardResult.fail(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        return MessageSendGuardResult.pass();
    }
}
