package org.pluchon.forum.service.interfaces.vip;

import org.pluchon.forum.api.economy.VipQuotaHintVO;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.entity.vo.vip.VipCenterVO;
import org.pluchon.forum.entity.vo.vip.VipPurchaseRecordVO;
import org.pluchon.forum.entity.vo.vip.VipQuotaPanelVO;

public interface VipCenterService {

    VipCenterVO center(Long userId);

    VipQuotaPanelVO quota(Long userId);

    PageResult<VipPurchaseRecordVO> purchaseRecords(Long userId, Integer pageNum, Integer pageSize);

    // 当前模型路由对应配额使用率 0–100 ，供看板娘「使用萌币」按钮展示
    VipQuotaHintVO quotaHintForLlmRoute(Long userId, String llmRoute);
}
