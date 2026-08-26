package org.pluchon.forum.entity.vo.lottery;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "奖池展示行")
public class LotteryPrizeLineVO {

    private String name;

    @Schema(description = "0谢谢 1积分 2VIP天数")
    private Byte prizeType;

    private Integer prizeValue;

    @Schema(description = "-1不限量 其它为剩余库存")
    private Integer stockRemaining;

    private Boolean jackpot;

    // 活动关联权重 加权随机 ；前台用于动态概率饼图
    private Integer weight;

    // 奖品封面相对路径，可空
    private String imagePath;
}
