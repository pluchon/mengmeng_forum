package org.pluchon.forum.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.pluchon.forum.common.constant.InternalApiConstants;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.result.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

// 校验 /internal/** 的 X-Internal-Key，防止绕过网关后的裸奔访问
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class InternalApiAuthFilter extends OncePerRequestFilter {

    @Value("${forum.service.internal-key:}")
    private String serviceInternalKey;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !InternalApiConstants.isInternalPath(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!StringUtils.hasText(serviceInternalKey)) {
            reject(response);
            return;
        }
        String provided = request.getHeader(InternalApiConstants.INTERNAL_KEY_HEADER);
        if (!keyMatches(serviceInternalKey, provided)) {
            reject(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), Result.fail(ResultCode.FAILED_FORBIDDEN));
    }

    private static boolean keyMatches(String expected, String provided) {
        if (!StringUtils.hasText(provided)) {
            return false;
        }
        byte[] left = expected.trim().getBytes(StandardCharsets.UTF_8);
        byte[] right = provided.trim().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(left, right);
    }
}
