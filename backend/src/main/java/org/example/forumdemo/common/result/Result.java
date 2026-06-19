package org.example.forumdemo.common.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.forumdemo.common.enums.ResultCode;

//定义统一前端返回结果
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {
    //状态码
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private int code;
    //状态信息，可以自定义
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private String message;
    //数据
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private T data;

    //自己再补充构造函数
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

    //定义成功方法
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

    //定义失败方法
    public static <T> Result<T> fail(){
        return new Result<>(ResultCode.FAILED.getCode(),ResultCode.FAILED.getMessage());
    }

    public static <T> Result<T> fail(String message){
        return new Result<>(ResultCode.FAILED.getCode(),message);
    }

    public static <T> Result<T> fail(ResultCode resultCode){
        return new Result<>(resultCode.getCode(),resultCode.getMessage());
    }

    public static <T> Result<T> fail(ResultCode resultCode, String extraMessage){
        return new Result<>(resultCode.getCode(), resultCode.getMessage()
                + (extraMessage == null || extraMessage.isEmpty() ? "" : ": " + extraMessage));
    }
}
