package org.pluchon.forum.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

@Schema(description = "看板娘牵线意愿")
@Data
public class MascotIntentVO {

    private Long id;

    @Schema(description = "seek=想找人 offer=能帮人")
    private String kind;

    private String text;

    private String state;

    private Date expireAt;

    private Date createTime;
}
