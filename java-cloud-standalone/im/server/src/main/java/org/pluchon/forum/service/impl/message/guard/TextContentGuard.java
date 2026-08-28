package org.pluchon.forum.service.impl.message.guard;

import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.result.Result;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

// 文本消息改为先发后审：发送链路只做空内容校验，AI 审核由 OutboundMessageTextAuditService 异步完成
@Component
public class TextContentGuard implements MessageSendGuard {

    @Override
    public boolean supports(MessageSendType sendType) {
        return sendType == MessageSendType.TEXT
                || sendType == MessageSendType.REPLY;
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public MessageSendGuardResult check(MessageSendContext context) {
        if (context.getSendType() == MessageSendType.REPLY && !StringUtils.hasLength(context.getContent())) {
            return MessageSendGuardResult.fail(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        if (context.getSendType() == MessageSendType.TEXT && !StringUtils.hasText(context.getContent())) {
            return MessageSendGuardResult.fail(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        return MessageSendGuardResult.pass();
    }
}
