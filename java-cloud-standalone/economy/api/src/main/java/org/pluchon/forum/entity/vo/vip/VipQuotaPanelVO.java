package org.pluchon.forum.entity.vo.vip;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;
import java.math.BigDecimal;

@Data
@Schema(description = "VIP 配额面板（PRO/MAX）")
public class VipQuotaPanelVO {

    private Byte vipTier;
    private String tierLabel;
    private Date periodStart;
    private Date periodEnd;
    private Integer totalCalls;
    private Long qwenBudgetMicros;
    private Long qwenUsedMicros;
    private Long qwenRemainingMicros;
    private BigDecimal wanImageLimit;
    private BigDecimal wanImageUsed;
    private BigDecimal wanImageRemaining;
}
