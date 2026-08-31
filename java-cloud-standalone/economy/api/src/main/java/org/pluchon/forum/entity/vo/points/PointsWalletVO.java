package org.pluchon.forum.entity.vo.points;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 积分钱包概览 VO。累计口径在萌币中心 /points/center/overview 里算，这里只回余额
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "积分钱包概览")
public class PointsWalletVO {

    @Schema(description = "当前余额")
    private Integer balance;

}
