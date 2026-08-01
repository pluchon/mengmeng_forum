package org.pluchon.forum.service.interfaces.vip;

import org.pluchon.forum.entity.vo.mascot.MascotQuotaHintVO;
import org.pluchon.forum.entity.vo.vip.VipCenterVO;
import org.pluchon.forum.entity.vo.vip.VipQuotaPanelVO;

public interface VipCenterService {

    VipCenterVO center(Long userId);

    VipQuotaPanelVO quota(Long userId);

    /**
     * 当前模型路由对应配额使用率（0–100），供看板娘「使用萌币」按钮展示。
     */
    MascotQuotaHintVO quotaHintForLlmRoute(Long userId, String llmRoute);
}
