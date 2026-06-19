package org.example.forumdemo.entity.vo.admin;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AdminForumMascotModelRowVO {

    private Long id;

    private String code;

    private String name;

    private String modelRelPath;

    private BigDecimal modelScale;

    private Integer posX;

    private Integer posY;

    private Integer stageWidth;

    private Integer stageHeight;

    private Integer shelfStatus;

    private Integer sortOrder;

    private Integer deleteState;

    private String createTime;

    private String updateTime;
}
