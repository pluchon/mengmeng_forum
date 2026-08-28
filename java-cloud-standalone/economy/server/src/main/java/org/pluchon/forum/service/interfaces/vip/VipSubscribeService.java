package org.pluchon.forum.service.interfaces.vip;

import org.pluchon.forum.entity.vo.vip.VipStatusVO;
import org.pluchon.forum.entity.vo.vip.VipTrialGrantResultVO;

public interface VipSubscribeService {

    VipStatusVO status(Long userId);

    // 体验卡发放必须显式指定档位，避免默认 PRO 把 MAX 卡静默降级
    VipTrialGrantResultVO grantTrialVip(Long userId, Byte tier, int days,
                                        String sourceType, String idempotencyKey);
}
