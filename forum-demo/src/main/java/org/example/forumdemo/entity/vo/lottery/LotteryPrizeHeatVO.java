package org.example.forumdemo.entity.vo.lottery;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "奖池中奖项在近期开奖记录里的热度计数（柱状图）")
public class LotteryPrizeHeatVO {

    private String prizeName;

    private Long winCount;
}
