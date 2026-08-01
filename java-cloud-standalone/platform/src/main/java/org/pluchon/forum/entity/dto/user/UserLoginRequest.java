package org.pluchon.forum.entity.dto.user;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

/**
 * @author pluchon
 * @create 2026-03-06-17:35
 * 作者代码水平一般，难免难看，请见谅
 */
//用户登录请求
@Data
public class UserLoginRequest {
    /** 兼容前端字段名 {@code username}（Arco / gi-demo 常用） */
    @JsonAlias("username")
    @NotNull
    @Length(min = 4, message = "用户名至少为4个字符")
    private String userName;
    @NotNull
    @Length(min = 6,message = "密码至少为6位")
    private String password;
}
