package org.pluchon.forum.entity.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

// 前端注册请求
@Data
public class UserResigterRequest {
    @NotNull
    @Length(min = 4, max = 20, message = "用户名长度为 4 到 20 个字符")
    private String userName;

    @NotNull
    @Length(min = 8, max = 20, message = "密码长度为 8 到 20 位")
    private String password;

    @NotNull
    @Length(min = 2, max = 20, message = "昵称长度为 2 到 20 个字符")
    private String nickname;

    // 可选：手机号
    private String phoneNum;

    // 可选：邮箱
    private String email;

    // 行为验证码一次性票据 REGISTER
    @NotBlank(message = "请先完成人机验证")
    private String captchaTicket;
}
