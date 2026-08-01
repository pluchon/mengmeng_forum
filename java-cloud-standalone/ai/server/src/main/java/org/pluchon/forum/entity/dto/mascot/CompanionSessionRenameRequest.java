package org.pluchon.forum.entity.dto.mascot;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

// 看板娘会话改名请求
@Data
@Schema(description = "看板娘会话改名请求")
public class CompanionSessionRenameRequest {

    @NotBlank(message = "会话名称不能为空")
    @Length(max = 48, message = "会话名称最长 48 个字符")
    @Schema(description = "会话名称", example = "旅行灵感")
    private String title;
}
