package org.example.forumdemo.service.interfaces.vip;

import org.example.forumdemo.entity.vo.vip.VipCenterVO;
import org.example.forumdemo.entity.vo.vip.VipQuotaPanelVO;

import java.util.Map;

public interface VipCenterService {

    VipCenterVO center(Long userId);

    VipQuotaPanelVO quota(Long userId);

    /**
     * 当前模型路由对应配额使用率（0–100），供看板娘「使用萌币」按钮展示。
     */
    Map<String, Object> quotaHintForLlmRoute(Long userId, String llmRoute);
}
