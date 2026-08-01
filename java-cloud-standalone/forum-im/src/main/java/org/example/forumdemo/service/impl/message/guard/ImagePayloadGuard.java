package org.example.forumdemo.service.impl.message.guard;

import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.result.Result;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ImagePayloadGuard implements MessageSendGuard {

    @Override
    public boolean supports(MessageSendType sendType) {
        return sendType == MessageSendType.IMAGE;
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public MessageSendGuardResult check(MessageSendContext context) {
        if (context.getReceiverUserId() == null
                || !StringUtils.hasLength(context.getMediaUrl())
                || context.getMessageType() == null) {
            return MessageSendGuardResult.fail(Result.fail(ResultCode.FAILED_MESSAGE_IMAGE_INVALID));
        }
        if (!Constant.MESSAGE_TYPE_IMAGE.equals(context.getMessageType())
                && !Constant.MESSAGE_TYPE_GIF.equals(context.getMessageType())) {
            return MessageSendGuardResult.fail(Result.fail(ResultCode.FAILED_MESSAGE_IMAGE_INVALID));
        }
        return MessageSendGuardResult.pass();
    }
}
