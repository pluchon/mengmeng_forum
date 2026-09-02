package org.pluchon.forum.entity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MascotIntentCreateRequest {

    /** seek=想找人 offer=能帮人；非法值一律按 seek */
    private String kind;

    @NotBlank(message = "意愿内容不能为空")
    @Size(max = 200, message = "意愿描述不能超过 200 字")
    private String text;

    /** 来自哪个看板娘会话 */
    private Long sessionId;
}
