package org.pluchon.forum.service.impl.remote;

import org.pluchon.forum.api.auth.UserInternalVO;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;

// IM 域发言状态校验
public final class ImUserMuteGuard {

    private static final byte MUTED = 1;

    private ImUserMuteGuard() {
    }

    public static void assertCanPost(UserInternalVO user) {
        if (user != null && user.getState() != null && user.getState() == MUTED) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_BANNED));
        }
    }
}
