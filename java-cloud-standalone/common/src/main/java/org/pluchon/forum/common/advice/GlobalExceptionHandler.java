package org.pluchon.forum.common.advice;

import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.validation.FieldError;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.validation.ConstraintViolationException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;

// 全局的异常拦截器
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // 参数校验异常 @Valid 触发
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<?>> handleValidationException(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String errMsg = (fieldError != null && fieldError.getDefaultMessage() != null)
                ? fieldError.getDefaultMessage()
                : ResultCode.FAILED_PARAMS_VALIDATE.getMessage();
        log.warn("参数校验非法: {}", errMsg);
        return response(HttpStatus.BAD_REQUEST, Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, errMsg));
    }

    // 自定义业务异常
    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<Result<?>> handleAppException(ApplicationException e) {
        if (e.getErrorResult() != null) {
            log.warn("业务请求失败: code={}, message={}", e.getErrorResult().getCode(), e.getErrorResult().getMessage());
            return response(resolveStatus(e.getErrorResult().getCode()), e.getErrorResult());
        }
        log.error("未包装的业务异常", e);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, Result.fail(ResultCode.ERROR_SERVICES));
    }

    // 请求体格式错误
    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            ConstraintViolationException.class, BindException.class, TypeMismatchException.class,
            MethodArgumentTypeMismatchException.class, HandlerMethodValidationException.class})
    public ResponseEntity<Result<?>> handleBadRequest(Exception e) {
        log.warn("请求格式错误: type={}", e.getClass().getSimpleName());
        return response(HttpStatus.BAD_REQUEST, Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
    }

    /**
     * 请求方法不对（例如对 POST-only 端点发 GET）。
     *
     * <p>不接住的话会落到兜底的 Exception 处理器，变成 500「服务开小差了」——
     * 客户端用错方法却记成服务端故障，5xx 告警就失去区分度了。
     * 不新增业务码：要修的是 HTTP 状态码，业务码没人消费。
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<?>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("请求方法不支持 (405): method={} supported={}", e.getMethod(), e.getSupportedHttpMethods());
        return response(HttpStatus.METHOD_NOT_ALLOWED, Result.fail(ResultCode.FAILED));
    }

    // 返回统一的资源不存在响应，减少无效 404 堆栈干扰
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<?>> handleNotFoundException(NoResourceFoundException e) {
        log.warn("资源未找到 (404): path={}", e.getResourcePath());
        return response(HttpStatus.NOT_FOUND, Result.fail(ResultCode.FAILED_NOT_EXISTS));
    }

    // 上传超过服务器限制时返回明确提示
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<?>> handleUploadTooLarge(MaxUploadSizeExceededException e) {
        log.warn("上传文件超过限制: {}", e.getMessage());
        return response(HttpStatus.PAYLOAD_TOO_LARGE, Result.fail(ResultCode.FAILED_UPLOAD_TOO_LARGE));
    }

    // 下游或网络超时统一转为可重试提示
    @ExceptionHandler({SocketTimeoutException.class, TimeoutException.class, ResourceAccessException.class})
    public ResponseEntity<Result<?>> handleTimeout(Exception e) {
        log.warn("下游调用超时: type={}", e.getClass().getSimpleName());
        return response(HttpStatus.GATEWAY_TIMEOUT, Result.fail(ResultCode.FAILED_SERVICE_TIMEOUT));
    }

    // 数据库异常只记录服务端细节，不向用户泄漏 SQL 和表结构
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Result<?>> handleDataAccessException(DataAccessException e) {
        log.error("数据库访问失败", e);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, Result.fail(ResultCode.ERROR_SERVICES));
    }

    // 全局兜底异常
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<?>> handleGlobalException(Exception e) {
        // Feign 等包装超时若未单独声明，避免误报成内部错误
        if (containsTimeoutCause(e)) {
            log.warn("下游调用超时: type={}", e.getClass().getSimpleName());
            return response(HttpStatus.GATEWAY_TIMEOUT, Result.fail(ResultCode.FAILED_SERVICE_TIMEOUT));
        }
        log.error("未知系统错误", e);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, Result.fail(ResultCode.ERROR_SERVICES));
    }

    private static boolean containsTimeoutCause(Throwable throwable) {
        Throwable current = throwable;
        for (int depth = 0; current != null && depth < 12; depth++) {
            if (current instanceof SocketTimeoutException
                    || current instanceof TimeoutException
                    || current instanceof ResourceAccessException) {
                return true;
            }
            // common 不依赖 feign；按类名识别读超时包装
            if ("feign.RetryableException".equals(current.getClass().getName())) {
                String message = String.valueOf(current.getMessage()).toLowerCase();
                if (message.contains("timed out") || message.contains("timeout") || message.contains("read timed")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private HttpStatus resolveStatus(int code) {
        if (code == ResultCode.FAILED_UNAUTHORIZED.getCode() || code == ResultCode.USER_UNLOGIN.getCode()) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (code == ResultCode.FAILED_FORBIDDEN.getCode() || code == ResultCode.FAILED_USER_BANNED.getCode()) {
            return HttpStatus.FORBIDDEN;
        }
        if (code == ResultCode.FAILED_NOT_EXISTS.getCode()) {
            return HttpStatus.NOT_FOUND;
        }
        if (code == ResultCode.FAILED_RATE_LIMITED.getCode()) {
            return HttpStatus.TOO_MANY_REQUESTS;
        }
        if (code == ResultCode.FAILED_UPLOAD_TOO_LARGE.getCode()) {
            return HttpStatus.PAYLOAD_TOO_LARGE;
        }
        if (code == ResultCode.FAILED_SERVICE_TIMEOUT.getCode()) {
            return HttpStatus.GATEWAY_TIMEOUT;
        }
        if (code == ResultCode.FAILED_SERVICE_UNAVAILABLE.getCode()
                || code == ResultCode.FAILED_AI_ENGINE.getCode()
                || code == ResultCode.FAILED_MASCOT_AI.getCode()) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        if (code >= ResultCode.ERROR_SERVICES.getCode()) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return HttpStatus.BAD_REQUEST;
    }

    private ResponseEntity<Result<?>> response(HttpStatus status, Result<?> result) {
        return ResponseEntity.status(status).body(result);
    }
}
