package org.example.forumdemo.common.utils;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

public final class HttpRequestUtils {

    private HttpRequestUtils() {
    }

    public static String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String[] headers = {
                "X-Forwarded-For",
                "X-Real-IP",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP"
        };
        for (String header : headers) {
            String value = request.getHeader(header);
            if (StringUtils.hasText(value) && !"unknown".equalsIgnoreCase(value)) {
                int comma = value.indexOf(',');
                return comma > 0 ? value.substring(0, comma).trim() : value.trim();
            }
        }
        return request.getRemoteAddr();
    }

    public static String resolveUserAgent(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String ua = request.getHeader("User-Agent");
        if (!StringUtils.hasText(ua)) {
            return null;
        }
        return ua.length() > 512 ? ua.substring(0, 512) : ua;
    }
}
