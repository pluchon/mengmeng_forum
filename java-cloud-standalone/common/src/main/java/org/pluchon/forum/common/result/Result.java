package org.pluchon.forum.common.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.pluchon.forum.common.enums.ResultCode;
import org.slf4j.MDC;

// 定义统一前端返回结果
@Data
@NoArgsConstructor
public class Result<T> {
    // 状态码
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private int code;
    // 状态信息，可以自定义
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private String message;
    // 数据
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private T data;
    // 链路编号，仅失败时返回给前端用于问题定位
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String traceId;

    public Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    // 自己再补充构造函数
    public Result(String message){
        this.message = message;
    }

    public Result(int code){
        this.code = code;
    }

    public Result(int code,String message){
        this.code = code;
        this.message = message;
    }

    // 定义成功方法
    public static <T> Result<T> success(){
        return new Result<>(ResultCode.SUCCESS.getCode(),ResultCode.SUCCESS.getMessage());
    }

    public static <T> Result<T> success(String message){
        return new Result<>(ResultCode.SUCCESS.getCode(),message);
    }

    public static <T> Result<T> success(T data){
        return new Result<>(ResultCode.SUCCESS.getCode(),ResultCode.SUCCESS.getMessage(),data);
    }

    public static <T> Result<T> successData(T data){
        return new Result(ResultCode.SUCCESS.getCode(),ResultCode.SUCCESS.getMessage(),data);
    }

    public static <T> Result<T> success(ResultCode resultCode){
        return new Result<>(resultCode.getCode(),resultCode.getMessage());
    }

    // 定义失败方法
    public static <T> Result<T> fail(){
        return withCurrentTrace(new Result<>(ResultCode.FAILED.getCode(),ResultCode.FAILED.getMessage()));
    }

    public static <T> Result<T> fail(String message){
        return withCurrentTrace(new Result<>(ResultCode.FAILED.getCode(),message));
    }

    public static <T> Result<T> fail(ResultCode resultCode){
        return withCurrentTrace(new Result<>(resultCode.getCode(),resultCode.getMessage()));
    }

    public static <T> Result<T> fail(ResultCode resultCode, String extraMessage){
        return withCurrentTrace(new Result<>(resultCode.getCode(), resultCode.getMessage()
                + (extraMessage == null || extraMessage.isEmpty() ? "" : ": " + extraMessage)));
    }

    // 异常处理阶段补齐当前请求的链路编号
    public Result<T> attachCurrentTrace() {
        if (traceId == null || traceId.isBlank()) {
            traceId = MDC.get("traceId");
        }
        return this;
    }

    private static <T> Result<T> withCurrentTrace(Result<T> result) {
        return result.attachCurrentTrace();
    }
}
