package org.pluchon.forum.entity.vo.vip;

import lombok.Data;

import java.util.List;

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
    /** current | owned | subscribe */
    private String buttonState;
    private String buttonLabel;
}
