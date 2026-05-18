package org.example.forumdemo.service.interfaces.vip;

import org.example.forumdemo.entity.vo.vip.VipCenterVO;
import org.example.forumdemo.entity.vo.vip.VipQuotaPanelVO;

public interface VipCenterService {

    VipCenterVO center(Long userId);

    VipQuotaPanelVO quota(Long userId);
}
