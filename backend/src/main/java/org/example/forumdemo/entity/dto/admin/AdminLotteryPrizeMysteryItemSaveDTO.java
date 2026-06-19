package org.example.forumdemo.entity.dto.admin;

import lombok.Data;

@Data
public class AdminLotteryPrizeMysteryItemSaveDTO {

    private Byte itemType;

    private Integer itemValue;

    private Integer weight;
}
