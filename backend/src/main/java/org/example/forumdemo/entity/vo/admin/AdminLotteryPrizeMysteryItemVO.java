package org.example.forumdemo.entity.vo.admin;

import lombok.Data;

@Data
public class AdminLotteryPrizeMysteryItemVO {

    private Long id;

    private Byte itemType;

    private Integer itemValue;

    private Integer weight;
}
