package org.pluchon.forum.entity.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

// 用户资料待审核变更请求
@Data
public class ProfileChangeRequest {

    @NotBlank(message = "资料字段不能为空")
    private String fieldType;

    @Size(max = 50, message = "提交内容不能超过50个字")
    private String content;
}
