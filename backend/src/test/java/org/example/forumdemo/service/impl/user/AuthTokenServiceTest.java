package org.example.forumdemo.service.impl.user;

import io.jsonwebtoken.Claims;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.utils.JWTUtils;
import org.example.forumdemo.entity.db.User;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

class AuthTokenServiceTest {

    private AuthTokenService authTokenService;

    private JwtTokenVersionService jwtTokenVersionService;

    @BeforeEach
    void setUp() {
        new JWTUtils().setSecretString("local_dev_jwt_secret_min_32_chars_change_me_123");
        authTokenService = new AuthTokenService();
        jwtTokenVersionService = Mockito.mock(JwtTokenVersionService.class);
        ReflectionTestUtils.setField(authTokenService, "jwtTokenVersionService", jwtTokenVersionService);
    }

    @Test
    void shouldUseNextVersionWhenIssuingLoginToken() {
        User user = buildUser();
        Mockito.when(jwtTokenVersionService.nextVersion(10L)).thenReturn(3L);

        String token = authTokenService.issueLoginToken(user);

        Claims claims = JWTUtils.parseJWT(token);
        Assertions.assertEquals(3L, JWTUtils.readTokenVersion(claims));
        Assertions.assertEquals(10L, Long.valueOf(claims.get(Constant.JWT_USER_ID).toString()));
        Mockito.verify(jwtTokenVersionService).nextVersion(10L);
        Mockito.verify(jwtTokenVersionService, Mockito.never()).currentVersion(Mockito.anyLong());
    }

    @Test
    void shouldKeepCurrentVersionWhenIssuingNormalToken() {
        User user = buildUser();
        Mockito.when(jwtTokenVersionService.currentVersion(10L)).thenReturn(2L);

        String token = authTokenService.issueToken(user);

        Claims claims = JWTUtils.parseJWT(token);
        Assertions.assertEquals(2L, JWTUtils.readTokenVersion(claims));
        Mockito.verify(jwtTokenVersionService).currentVersion(10L);
        Mockito.verify(jwtTokenVersionService, Mockito.never()).nextVersion(Mockito.anyLong());
    }

    private User buildUser() {
        User user = new User();
        user.setId(10L);
        user.setUsername("pluchon");
        user.setState((byte) 0);
        return user;
    }
}
