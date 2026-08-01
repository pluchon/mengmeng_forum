package org.pluchon.forum.common.advice;

import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
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
        log.error("业务异常: {}", e.getMessage());
        if (e.getErrorResult() != null) {
            return e.getErrorResult();
        }
        String msg = (e.getMessage() == null || e.getMessage().isEmpty()) ? ResultCode.ERROR_SERVICES.getMessage() : e.getMessage();
        return Result.fail(msg);
    }

    /**
     * 屏蔽静态资源/开发工具 404 异常，减少日志堆栈干扰
     */
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(NoResourceFoundException.class)
    public void handleNotFoundException(NoResourceFoundException e) {
        log.info("资源未找到 (404): {}", e.getResourcePath());
    }

    /**
     * 全局兜底异常
     */
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class)
    public Result handleGlobalException(Exception e) {
        log.error("未知系统错误: {}", e.getMessage(), e);
        String msg = (e.getMessage() == null || e.getMessage().isEmpty()) ? ResultCode.ERROR_SERVICES.getMessage() : e.getMessage();
        return Result.fail(msg);
    }
}
