package org.example.forumdemo.entity.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "管理端保存看板娘模型")
public class AdminForumMascotModelSaveRequest {

    private Long id;

    @NotBlank
    @Size(max = 64)
    private String code;

    @NotBlank
    @Size(max = 128)
    private String name;

    @NotBlank
    @Size(max = 512)
    private String modelRelPath;

    private BigDecimal modelScale;

    private Integer posX;

    private Integer posY;

    private Integer stageWidth;

    private Integer stageHeight;

    /** 0草稿 1上架 2下架 */
    private Integer shelfStatus;

    private Integer sortOrder;
}
