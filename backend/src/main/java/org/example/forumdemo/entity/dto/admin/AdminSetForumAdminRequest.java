package org.example.forumdemo.entity.dto.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminSetForumAdminRequest {

    @NotNull
    private Long id;

    /** 1=论坛管理员（可登录管理后台） 0=普通会员 */
    @NotNull
    @Min(0)
    @Max(1)
    private Integer isAdmin;
}
