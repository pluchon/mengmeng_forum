package org.example.forumdemo.entity.vo.lottery;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "抽奖页彩蛋积分领取结果")
public class LotterySurpriseClaimVO {

    @Schema(description = "本次是否发放成功（首次领取）")
    private boolean granted;

    @Schema(description = "是否此前已领取过（幂等提示）")
    private boolean alreadyClaimed;

    @Schema(description = "发放积分数额（首次领取时）")
    private Integer grantPoints;

    @Schema(description = "发放后的钱包余额快照")
    private Integer balanceAfter;
}
