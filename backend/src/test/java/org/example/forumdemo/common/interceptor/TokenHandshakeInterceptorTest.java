package org.example.forumdemo.common.interceptor;

import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.utils.JWTUtils;
import org.example.forumdemo.service.impl.user.JwtTokenVersionService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.WebSocketHandler;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

class TokenHandshakeInterceptorTest {

    private TokenHandshakeInterceptor interceptor;

    private JwtTokenVersionService jwtTokenVersionService;

    @BeforeEach
    void setUp() {
        new JWTUtils().setSecretString("local_dev_jwt_secret_min_32_chars_change_me_123");
        interceptor = new TokenHandshakeInterceptor();
        jwtTokenVersionService = Mockito.mock(JwtTokenVersionService.class);
        ReflectionTestUtils.setField(interceptor, "jwtTokenVersionService", jwtTokenVersionService);
    }

    @Test
    void shouldRejectExpiredTokenVersion() {
        String token = JWTUtils.genJwtForUser(10L, "pluchon", 2L);
        Mockito.when(jwtTokenVersionService.isValid(10L, 2L)).thenReturn(false);
        ServerHttpRequest request = requestWithToken(token);
        ServerHttpResponse response = Mockito.mock(ServerHttpResponse.class);

        boolean accepted = interceptor.beforeHandshake(
                request,
                response,
                Mockito.mock(WebSocketHandler.class),
                new HashMap<>()
        );

        Assertions.assertFalse(accepted);
        Mockito.verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldAcceptCurrentTokenVersion() {
        String token = JWTUtils.genJwtForUser(10L, "pluchon", 3L);
        Mockito.when(jwtTokenVersionService.isValid(10L, 3L)).thenReturn(true);
        ServerHttpRequest request = requestWithToken(token);
        ServerHttpResponse response = Mockito.mock(ServerHttpResponse.class);
        Map<String, Object> attributes = new HashMap<>();

        boolean accepted = interceptor.beforeHandshake(
                request,
                response,
                Mockito.mock(WebSocketHandler.class),
                attributes
        );

        Assertions.assertTrue(accepted);
        Assertions.assertEquals(10L, attributes.get(Constant.JWT_USER_ID));
        Assertions.assertEquals("pluchon", attributes.get(Constant.JWT_USER_NAME));
    }

    private ServerHttpRequest requestWithToken(String token) {
        ServerHttpRequest request = Mockito.mock(ServerHttpRequest.class);
        Mockito.when(request.getURI()).thenReturn(URI.create("ws://localhost/ws/notify?token=" + token));
        return request;
    }
}
