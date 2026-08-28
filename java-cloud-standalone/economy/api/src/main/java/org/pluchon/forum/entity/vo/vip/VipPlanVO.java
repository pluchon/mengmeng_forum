package org.pluchon.forum.entity.vo.vip;

import lombok.Data;

import java.util.List;
import java.math.BigDecimal;

@Data
public class VipPlanVO {

    private Byte tier;
    private String code;
    private String name;
    private String subtitle;
    private String badge;
    private Integer pricePoints;
    private Integer durationDays;
    private boolean featured;
    private List<VipPlanFeatureVO> features;
    private String buttonState;
    private String buttonLabel;
    private BigDecimal originalPrice;
    private BigDecimal firstMonthPrice;
    private Boolean firstPurchaseEligible;
    private Long qwenBudgetMicros;
    private Integer wanImageLimit;
}
