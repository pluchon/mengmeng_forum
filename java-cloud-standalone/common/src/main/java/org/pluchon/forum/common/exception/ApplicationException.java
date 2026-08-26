package org.pluchon.forum.common.exception;

import lombok.Getter;
import org.pluchon.forum.common.result.Result;

// 定义自定义异常
@Getter
public class ApplicationException extends RuntimeException {
    // 自定义错误，拿取结果信息
    protected Result errorResult;

    // 自定义构造方法
    public ApplicationException(Result errorResult){
        super(errorResult.getMessage());
        this.errorResult = errorResult;
    }

    // 必须显示的调用父类方法，才能够得到父类的各种异常传递排查

    public ApplicationException(String message) {
        super(message);
    }

    public ApplicationException() {
        super();
    }

    public ApplicationException(String message, Throwable cause) {
        super(message, cause);
    }

    public ApplicationException(Throwable cause) {
        super(cause);
    }
}
