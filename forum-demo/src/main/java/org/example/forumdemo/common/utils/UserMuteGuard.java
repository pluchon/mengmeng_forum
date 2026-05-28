package org.example.forumdemo.common.utils;

import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.db.User;

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
