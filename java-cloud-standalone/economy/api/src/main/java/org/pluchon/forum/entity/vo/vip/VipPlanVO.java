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
    private Integer durationDays;
    private boolean featured;
    private List<VipPlanFeatureVO> features;

    // current 当前方案 / owned 已拥有 / subscribe 可开通 / upgrade 可升级
    private String buttonState;

    private String buttonLabel;
    private BigDecimal originalPrice;
    private BigDecimal firstMonthPrice;
    private Boolean firstPurchaseEligible;

    // buttonState 为 upgrade 时的实付差价，已按剩余天数折算
    private BigDecimal upgradePrice;

    // 折算所用的剩余天数，前端拿它写小字说明
    private Integer upgradeRemainingDays;

    private Long qwenBudgetMicros;
    private Integer wanImageLimit;
}
