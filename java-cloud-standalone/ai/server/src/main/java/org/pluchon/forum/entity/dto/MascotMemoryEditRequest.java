package org.pluchon.forum.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "看板娘长期记忆编辑请求")
public class MascotMemoryEditRequest {

    @NotBlank(message = "instruction 不能为空")
    @Size(max = 200, message = "修改说明不能超过 200 字")
    private String instruction;
}
