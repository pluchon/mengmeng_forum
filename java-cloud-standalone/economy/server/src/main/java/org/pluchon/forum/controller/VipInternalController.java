package org.pluchon.forum.controller;

import org.pluchon.forum.api.economy.VipInternalApi;
import org.pluchon.forum.api.economy.VipQuotaHintVO;
import org.pluchon.forum.api.economy.VipTierSnapshotVO;
import org.pluchon.forum.entity.vo.vip.VipStatusVO;
import org.pluchon.forum.service.interfaces.vip.VipCenterService;
import org.pluchon.forum.service.interfaces.vip.VipSubscribeService;
import org.pluchon.forum.service.interfaces.vip.VipEntitlementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;

// VIP 内部接口：契约路径已是 /vip/internal/**，勿再叠加 @RequestMapping /vip
@RestController
public class VipInternalController implements VipInternalApi {

    @Autowired
    private VipSubscribeService vipSubscribeService;

    @Autowired
    private VipCenterService vipCenterService;

    @Autowired
    private VipEntitlementService vipEntitlementService;

    @Override
    public VipTierSnapshotVO tierSnapshot(@PathVariable("userId") Long userId) {
        VipStatusVO status = vipSubscribeService.status(userId);
        org.pluchon.forum.entity.db.UserVipSubscription subscription =
                vipEntitlementService.ensureCurrentBaseQuotaPeriod(userId);
        VipTierSnapshotVO vo = new VipTierSnapshotVO();
        if (status == null) {
            vo.setVipTier((byte) 0);
            vo.setVipActive(false);
            return vo;
        }
        vo.setVipTier(status.getVipTier());
        vo.setVipExpireAt(status.getVipExpireAt());
        boolean active = status.getVipTier() != null
                && status.getVipTier() > 0
                && (status.getVipExpireAt() == null || status.getVipExpireAt().after(new java.util.Date()));
        vo.setVipActive(active);
        vo.setBaseQuotaTier(subscription.getBaseQuotaTier());
        vo.setQuotaPeriodStart(subscription.getQuotaPeriodStart());
        vo.setQuotaPeriodEnd(subscription.getQuotaPeriodEnd());
        return vo;
    }

    @Override
    public VipQuotaHintVO quotaHintForLlmRoute(
            @PathVariable("userId") Long userId,
            @RequestParam(value = "llmRoute", required = false) String llmRoute) {
        return vipCenterService.quotaHintForLlmRoute(userId, llmRoute);
    }

}
