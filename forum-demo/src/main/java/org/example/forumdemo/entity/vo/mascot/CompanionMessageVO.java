package org.example.forumdemo.entity.vo.mascot;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

@Data
@Schema(description = "陪伴助手消息")
public class CompanionMessageVO {

    private String role;
    private String content;
    private String type;
    private String url;
    private Date at;
}
