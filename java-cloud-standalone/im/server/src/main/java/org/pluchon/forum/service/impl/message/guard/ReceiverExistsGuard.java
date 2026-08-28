package org.pluchon.forum.service.impl.message.guard;

import org.pluchon.forum.api.UserInternalVO;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.result.Result;
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

    // 用户不存在时下游 Feign 会抛 FAILED_USER_NOT_EXISTS；id 为 null 或非正数时本地直接返回 null，需在此显式拦下
    @Override
    public MessageSendGuardResult check(MessageSendContext context) {
        UserInternalVO receiver = userLookupService.queryUserByUserId(context.getReceiverUserId());
        if (receiver == null) {
            return MessageSendGuardResult.fail(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
        }
        return MessageSendGuardResult.pass();
    }
}
