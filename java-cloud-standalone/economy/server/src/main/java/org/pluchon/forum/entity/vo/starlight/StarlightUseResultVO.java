package org.pluchon.forum.entity.vo.starlight;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "萌星辉背包使用结果")
public class StarlightUseResultVO {

    private Long exchangeId;

    private String itemName;

    private String rewardType;

    private Integer rewardValue;

    private String rewardSummary;

    private Byte actualGrantTier;

    private Integer actualDurationHours;

    // 使用状态：0 未使用 1 已使用
    private Integer useStatus;
}
