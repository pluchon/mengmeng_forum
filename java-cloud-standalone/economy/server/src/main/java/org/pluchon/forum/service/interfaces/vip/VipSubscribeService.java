package org.pluchon.forum.service.interfaces.vip;

import org.pluchon.forum.entity.vo.vip.VipStatusVO;
import org.pluchon.forum.entity.vo.vip.VipTrialGrantResultVO;

public interface VipSubscribeService {

    VipStatusVO status(Long userId);

    VipTrialGrantResultVO grantTrialVipDays(Long userId, int days, String sourceType, String idempotencyKey);

    // 指定档位的体验卡发放 神秘大奖的 MAX 卡走此入口
    VipTrialGrantResultVO grantTrialVip(Long userId, Byte tier, int days,
                                        String sourceType, String idempotencyKey);
}
