package org.pluchon.forum.common.advice;

import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

// 全局的异常拦截器
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 参数校验异常 (@Valid 触发)
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result handleValidationException(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String errMsg = (fieldError != null) ? fieldError.getDefaultMessage() : "参数校验失败";
        log.warn("参数校验非法: {}", errMsg);
        return Result.fail(errMsg);
    }

    /**
     * 自定义业务异常
     */
    @ExceptionHandler(ApplicationException.class)
    public Result handleAppException(ApplicationException e) {
        if (e.getErrorResult() != null) {
            log.warn("业务请求失败: code={}, message={}", e.getErrorResult().getCode(), e.getErrorResult().getMessage());
            return e.getErrorResult();
        }
        log.error("未包装的业务异常", e);
        return Result.fail(ResultCode.ERROR_SERVICES);
    }

    /** 请求体格式错误 */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class})
    public Result handleBadRequest(Exception e) {
        log.warn("请求格式错误: type={}", e.getClass().getSimpleName());
        return Result.fail(ResultCode.FAILED_PARAMS_VALIDATE);
    }

    /**
     * 返回统一的资源不存在响应，减少无效 404 堆栈干扰
     */
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(NoResourceFoundException.class)
    public Result handleNotFoundException(NoResourceFoundException e) {
        log.warn("资源未找到 (404): path={}", e.getResourcePath());
        return Result.fail(ResultCode.FAILED_NOT_EXISTS);
    }

    /**
     * 全局兜底异常
     */
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class)
    public Result handleGlobalException(Exception e) {
        log.error("未知系统错误", e);
        return Result.fail(ResultCode.ERROR_SERVICES);
    }
}
