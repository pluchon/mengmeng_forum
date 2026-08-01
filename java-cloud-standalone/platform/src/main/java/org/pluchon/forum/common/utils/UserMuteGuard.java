package org.pluchon.forum.common.utils;

import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.entity.db.User;

// 用户禁言
public final class UserMuteGuard {

    private static final byte MUTED = 1;

    private UserMuteGuard() {
    }

    public static void assertCanPost(User user) {
        if (user != null && user.getState() != null && user.getState() == MUTED) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_BANNED));
        }
    }
}
