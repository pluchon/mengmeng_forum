package org.pluchon.forum.service.impl.message.guard;

import org.pluchon.forum.common.config.OssConfig;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.result.Result;
import org.springframework.stereotype.Component;

@Component
public class ChatMediaUrlGuard implements MessageSendGuard {

    private final OssConfig ossConfig;

    public ChatMediaUrlGuard(OssConfig ossConfig) {
        this.ossConfig = ossConfig;
    }

    @Override
    public boolean supports(MessageSendType sendType) {
        return sendType == MessageSendType.IMAGE;
    }

    @Override
    public int order() {
        return 50;
    }

    @Override
    public MessageSendGuardResult check(MessageSendContext context) {
        boolean okMsg = ossConfig.matchesPublicObjectUrl(context.getMediaUrl(), Constant.OSS_PATH_CHAT_MESSAGE);
        boolean okEmoji = ossConfig.matchesPublicObjectUrl(context.getMediaUrl(), Constant.OSS_PATH_CHAT_EMOJI);
        boolean okShop = ossConfig.matchesPublicObjectUrl(context.getMediaUrl(), Constant.OSS_PATH_EMOJI_SHOP);
        if (!okMsg && !okEmoji && !okShop) {
            return MessageSendGuardResult.fail(Result.fail(ResultCode.FAILED_INVALID_OSS_URL));
        }
        return MessageSendGuardResult.pass();
    }
}
