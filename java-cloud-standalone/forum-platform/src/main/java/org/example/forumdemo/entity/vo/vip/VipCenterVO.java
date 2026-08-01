package org.example.forumdemo.entity.vo.vip;

import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class VipCenterVO {

    private Byte vipTier;
    private Date vipExpireAt;
    private Integer points;
    private boolean vipActive;
    private List<VipPlanVO> plans;
    private VipQuotaPanelVO quota;
}
