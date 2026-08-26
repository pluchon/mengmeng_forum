package org.pluchon.forum.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "用户端看板娘模型（上架）")
public class MascotModelPublicVO {

    private Long id;

    private String code;

    private String name;

    private String modelRelPath;

    private BigDecimal modelScale;

    private Integer posX;

    private Integer posY;

    private Integer stageWidth;

    private Integer stageHeight;
}
