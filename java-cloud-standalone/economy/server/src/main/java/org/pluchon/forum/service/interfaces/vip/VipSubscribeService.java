package org.pluchon.forum.service.interfaces.vip;

import org.pluchon.forum.entity.vo.vip.VipStatusVO;
import org.pluchon.forum.entity.vo.vip.VipTrialGrantResultVO;

public interface VipSubscribeService {

    VipStatusVO status(Long userId);

    VipTrialGrantResultVO grantTrialVipDays(Long userId, int days, String sourceType, String idempotencyKey);
}
