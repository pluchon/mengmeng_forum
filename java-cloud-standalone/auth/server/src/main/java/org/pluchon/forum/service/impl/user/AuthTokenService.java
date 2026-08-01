package org.pluchon.forum.service.impl.user;

import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.security.JwtTokenVersionService;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.utils.JWTUtils;
import org.pluchon.forum.entity.db.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

// 统一签发 JWT，并校验账号是否可用
@Service
public class AuthTokenService {

    @Autowired
    private JwtTokenVersionService jwtTokenVersionService;

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

    public String issueLoginToken(User user) {
        assertCanAuthenticate(user);
        long tv = jwtTokenVersionService.nextVersion(user.getId());
        return JWTUtils.genJwtForUser(user.getId(), user.getUsername(), tv);
    }
}
