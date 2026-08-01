package org.pluchon.forum.entity.vo.mascot;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

@Data
@Schema(description = "陪伴助手会话摘要")
public class CompanionSessionVO {

    private Long id;
    private String skill;
    private String title;
    private Date updateTime;
}
