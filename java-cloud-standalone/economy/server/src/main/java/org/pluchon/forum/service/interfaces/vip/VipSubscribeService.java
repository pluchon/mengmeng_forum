package org.pluchon.forum.service.interfaces.vip;

import org.pluchon.forum.entity.dto.vip.VipSubscribeDTO;
import org.pluchon.forum.entity.vo.vip.VipSubscribeResultVO;
import org.pluchon.forum.entity.vo.vip.VipStatusVO;
import org.pluchon.forum.entity.vo.vip.VipTrialGrantResultVO;

public interface VipSubscribeService {

    VipSubscribeResultVO subscribe(Long userId, VipSubscribeDTO dto);

    VipStatusVO status(Long userId);

    // 抽奖/活动发放 VIP 体验天数 默认 PRO 档，不扣积分
    VipTrialGrantResultVO grantTrialVipDays(Long userId, int days);

    VipTrialGrantResultVO grantTrialVipDays(Long userId, int days, String sourceType, String idempotencyKey);
}
