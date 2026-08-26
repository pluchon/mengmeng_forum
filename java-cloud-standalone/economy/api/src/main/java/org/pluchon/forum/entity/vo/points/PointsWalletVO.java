package org.pluchon.forum.entity.vo.points;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 积分钱包概览 VO. 给前端 我的积分 页头部使用.
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "积分钱包概览")
public class PointsWalletVO {

    @Schema(description = "当前余额")
    private Integer balance;

    @Schema(description = "累计签到入账(基础+连签)")
    private Integer totalCheckinPoints;

    @Schema(description = "累计商城消费(绝对值)")
    private Integer totalSpendPoints;
}
