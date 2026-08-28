package org.pluchon.forum.entity.dto.user;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

// 已登录用户凭当前密码修改密码
@Data
public class ChangePasswordRequest {

    @NotBlank(message = "请输入当前密码")
    private String currentPassword;

    @NotBlank(message = "请输入新密码")
    @Length(min = 8, max = 20, message = "密码长度为 8 到 20 位")
    private String newPassword;
}
