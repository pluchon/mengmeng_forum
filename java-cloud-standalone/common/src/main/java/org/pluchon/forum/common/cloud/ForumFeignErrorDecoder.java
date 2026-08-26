package org.pluchon.forum.common.cloud;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.springframework.util.StreamUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

// OpenFeign 官方 ErrorDecoder：把下游 Result 转成 ApplicationException，避免一律 500
@Slf4j
public class ForumFeignErrorDecoder implements ErrorDecoder {

    private final ErrorDecoder defaultDecoder = new Default();
    private final ObjectMapper objectMapper;

    public ForumFeignErrorDecoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Exception decode(String methodKey, Response response) {
        int status = response.status();
        Result<?> parsed = tryParseResult(response);
        if (parsed != null && parsed.getCode() != ResultCode.SUCCESS.getCode()) {
            return new ApplicationException(parsed);
        }
        if (status == 408 || status == 504) {
            return new ApplicationException(Result.fail(ResultCode.FAILED_SERVICE_TIMEOUT));
        }
        if (status >= 500) {
            return new ApplicationException(Result.fail(ResultCode.FAILED_SERVICE_UNAVAILABLE));
        }
        if (status == 401) {
            return new ApplicationException(Result.fail(ResultCode.USER_UNLOGIN));
        }
        if (status == 403) {
            return new ApplicationException(Result.fail(ResultCode.FAILED_FORBIDDEN));
        }
        if (status == 404) {
            return new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
        return defaultDecoder.decode(methodKey, response);
    }

    private Result<?> tryParseResult(Response response) {
        if (response.body() == null) {
            return null;
        }
        try (InputStream in = response.body().asInputStream()) {
            String text = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
            if (text.isBlank()) {
                return null;
            }
            return objectMapper.readValue(text, Result.class);
        } catch (Exception ex) {
            log.debug("解析 Feign 错误体失败: {}", ex.getMessage());
            return null;
        }
    }
}
