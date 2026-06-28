package org.example.forumdemo.entity.dto.lottery;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "抽奖请求")
public class LotteryDrawDTO {

    @Schema(description = "1 单抽 10 十连", example = "1")
    private Integer times;

    @Schema(description = "活动 ID；不传则使用当前默认进行中活动（最新一条）")
    private Long activityId;

    @Schema(description = "客户端幂等键，同一抽奖请求重试必须复用")
    private String requestId;
}
