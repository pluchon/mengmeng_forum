package org.example.forumdemo.service.impl.user;

import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.common.utils.JWTUtils;
import org.example.forumdemo.entity.db.User;
import org.springframework.stereotype.Service;

/**
 * 统一签发 JWT，并校验账号是否可用。
 */
@Service
public class AuthTokenService {

    private final JwtTokenVersionService jwtTokenVersionService;

    public AuthTokenService(JwtTokenVersionService jwtTokenVersionService) {
        this.jwtTokenVersionService = jwtTokenVersionService;
    }

    public void assertCanAuthenticate(User user) {
        if (user == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
        }
        if (user.getState() != null && user.getState().equals(Constant.STATE_BANNED)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_BANNED));
        }
    }

    public String issueToken(User user) {
        assertCanAuthenticate(user);
        long tv = jwtTokenVersionService.currentVersion(user.getId());
        return JWTUtils.genJwtForUser(user.getId(), user.getUsername(), tv);
    }
}
