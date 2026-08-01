package org.pluchon.forum.entity.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

/**
 * @author pluchon
 * @create 2026-03-05-16:13
 * 作者代码水平一般，难免难看，请见谅
 */
//前端注册请求
@Data
public class UserResigterRequest {
    @NotNull
    @Length(min = 6,message = "用户名至少为6个字符")
    private String userName;
    @NotNull
    @Length(min = 6,message = "密码至少为6位")
    private String password;
    @NotNull
    @Length(min = 6,message = "昵称至少为6个字符")
    private String nickname;

    // 可选：手机号
    private String phoneNum;

    // 可选：邮箱
    private String email;

    /** 行为验证码一次性票据（REGISTER） */
    @NotBlank(message = "请先完成人机验证")
    private String captchaTicket;
}
