package org.pluchon.forum.entity.dto.user;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

// 用户登录请求
@Data
public class UserLoginRequest {
    // 兼容前端字段名 {@code username} Arco / gi demo 常用
    @JsonAlias("username")
    @NotNull
    @Length(min = 4, max = 64, message = "用户名长度不合法")
    private String userName;

    @NotNull
    @Length(min = 8, max = 20, message = "密码长度为 8 到 20 位")
    private String password;
}
