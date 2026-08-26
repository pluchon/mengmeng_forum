package org.pluchon.forum.entity.vo.lottery;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "卡池专属任务状态")
public class LotteryPoolTaskVO {

    private String taskCode;

    private String title;

    private Integer targetCount;

    private Integer currentCount;

    private Integer voucherReward;

    // LOCKED / CLAIMABLE / CLAIMED
    private String status;
}
