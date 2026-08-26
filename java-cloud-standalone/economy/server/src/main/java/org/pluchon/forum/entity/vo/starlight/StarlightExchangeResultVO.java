package org.pluchon.forum.entity.vo.starlight;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "萌星辉兑换结果")
public class StarlightExchangeResultVO {

    private Long exchangeId;

    private String itemName;

    private Integer pricePaid;

    private String rewardType;

    private Integer rewardValue;

    private Integer starlightBalanceAfter;

    private String rewardSummary;
}
