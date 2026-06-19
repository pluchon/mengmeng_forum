package org.example.forumdemo.service.interfaces.vip;

import org.example.forumdemo.entity.dto.vip.VipSubscribeDTO;
import org.example.forumdemo.entity.vo.vip.VipSubscribeResultVO;
import org.example.forumdemo.entity.vo.vip.VipStatusVO;

public interface VipSubscribeService {

    VipSubscribeResultVO subscribe(Long userId, VipSubscribeDTO dto);

    VipStatusVO status(Long userId);

    /** 抽奖/活动发放 VIP 体验天数（默认 PRO 档，不扣积分） */
    void grantTrialVipDays(Long userId, int days);
}
