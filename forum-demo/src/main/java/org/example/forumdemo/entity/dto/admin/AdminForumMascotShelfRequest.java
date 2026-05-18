package org.example.forumdemo.entity.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "看板娘模型上下架")
public class AdminForumMascotShelfRequest {

    @NotNull
    private Long id;

    /** 0草稿 1上架 2下架 */
    @NotNull
    private Integer shelfStatus;
}
