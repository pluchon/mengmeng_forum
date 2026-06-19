package org.example.forumdemo.entity.vo.admin;

import lombok.Data;

@Data
public class AdminLotteryPrizeOptionVO {

    private Long id;

    private String name;

    private Byte prizeType;

    private Integer prizeValue;

    private Byte isMysteryBundle;
}
