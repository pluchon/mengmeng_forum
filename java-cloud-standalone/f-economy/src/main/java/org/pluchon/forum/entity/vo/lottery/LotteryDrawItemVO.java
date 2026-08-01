package org.pluchon.forum.entity.vo.lottery;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "单次抽奖结果")
public class LotteryDrawItemVO {

    private Long recordId;

    private String prizeName;

    private Byte prizeType;

    private Integer prizeValue;

    private Integer grantPoints;

    private Boolean jackpot;

    /** 神秘大奖子项说明，如「VIP 体验 7 天」「积分 500」 */
    private String rewardDetail;
}
