package org.pluchon.forum.entity.dto.mascot;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "看板娘对话历史一轮")
public class MascotHistoryTurn {

    @Schema(description = "user 或 assistant", example = "user")
    private String role;

    @Schema(description = "文本内容")
    private String content;
}
