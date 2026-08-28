package org.pluchon.forum.common.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.pluchon.forum.common.enums.ResultCode;

// 定义统一前端返回结果
@Data
@NoArgsConstructor
public class Result<T> {

    // 状态码
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private int code;
    // 状态信息，只放用户可直接阅读的提示，排障编号走 X-Trace-Id 响应头
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private String message;
    // 数据
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private T data;

    public Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

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
        return new Result<>(ResultCode.FAILED.getCode(),ResultCode.FAILED.getMessage());
    }

    public static <T> Result<T> fail(String message){
        return new Result<>(ResultCode.FAILED.getCode(),message);
    }

    public static <T> Result<T> fail(ResultCode resultCode){
        return new Result<>(resultCode.getCode(),resultCode.getMessage());
    }

    public static <T> Result<T> fail(ResultCode resultCode, String detailMessage){
        String message = (detailMessage == null || detailMessage.isBlank())
                ? resultCode.getMessage()
                : detailMessage.trim();
        return new Result<>(resultCode.getCode(), message);
    }
}
