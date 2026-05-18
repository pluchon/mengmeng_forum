package org.example.forumdemo.entity.vo.admin;

import lombok.Data;

@Data
public class AdminLotteryPrizeCatalogRowVO {

    private Long id;

    private String name;

    private Byte prizeType;

    private Integer prizeValue;

    private Integer stockQuantity;

    private Byte catalogStatus;

    private Byte isMysteryBundle;

    private String imagePath;

    private Byte deleteState;

    private String createTime;

    private String updateTime;
}
