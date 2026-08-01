package org.pluchon.forum.entity.vo.lottery;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "抽奖活动页数据")
public class LotteryActivityInfoVO {

    private Long activityId;

    private String title;

    private String description;

    private Integer costPointsPerDraw;

    private Integer balance;

    private List<LotteryPrizeLineVO> prizes;

    /** 当前用户已连续未中「神秘大奖(is_jackpot)」的次数（持久化于 user.lottery_pity_draws） */
    private Integer pityDrawsSinceJackpot;

    /** 硬保底阈值（与 lottery_probability_explainer 一致：累计达到此次数则下一次必出神秘大奖档） */
    private Integer hardPityThreshold;

    /** 当前用户近期抽奖摘要（横向滚动展示） */
    private List<LotteryRecentDrawVO> recentDraws;
}
