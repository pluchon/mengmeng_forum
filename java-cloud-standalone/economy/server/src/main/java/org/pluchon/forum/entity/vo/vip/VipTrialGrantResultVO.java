package org.pluchon.forum.entity.vo.vip;

import lombok.Data;

import java.util.Date;

// PRO体验卡实际发放结果
@Data
public class VipTrialGrantResultVO {

    private Byte actualTier;
    private Integer actualDurationHours;
    private Date vipExpireAt;
    private Long qwenBonusMicros;
    private String wanBonusCredits;
    private Date bonusExpireAt;
    private String summary;
}
