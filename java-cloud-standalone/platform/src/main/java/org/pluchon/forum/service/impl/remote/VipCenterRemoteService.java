package org.pluchon.forum.service.impl.remote;

import org.pluchon.forum.api.economy.VipQuotaHintVO;
import org.pluchon.forum.cloud.feign.VipInternalFeignClient;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.entity.vo.mascot.MascotQuotaHintVO;
import org.pluchon.forum.entity.vo.vip.VipCenterVO;
import org.pluchon.forum.entity.vo.vip.VipQuotaPanelVO;
import org.pluchon.forum.service.interfaces.vip.VipCenterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

// 非 economy 域经 Feign 调用 VIP 配额提示（避免本地装载 VipCenterServiceImpl）
@Service
@ConditionalOnProperty(name = "forum.domain")
@ConditionalOnExpression("!'economy'.equals('${forum.domain}') && !'monolith'.equals('${forum.domain}')")
public class VipCenterRemoteService implements VipCenterService {

    @Autowired
    private VipInternalFeignClient vipInternalFeignClient;

    @Override
    public VipCenterVO center(Long userId) {
        throw unsupported("center");
    }

    @Override
    public VipQuotaPanelVO quota(Long userId) {
        throw unsupported("quota");
    }

    @Override
    public MascotQuotaHintVO quotaHintForLlmRoute(Long userId, String llmRoute) {
        VipQuotaHintVO remote = vipInternalFeignClient.quotaHintForLlmRoute(userId, llmRoute);
        MascotQuotaHintVO vo = new MascotQuotaHintVO();
        if (remote == null) {
            vo.setPercent(0);
            vo.setCanUsePointsPay(false);
            vo.setQuotaLabel("");
            return vo;
        }
        vo.setPercent(remote.getPercent() == null ? 0 : remote.getPercent());
        vo.setCanUsePointsPay(Boolean.TRUE.equals(remote.getCanUsePointsPay()));
        vo.setQuotaLabel(remote.getQuotaLabel() == null ? "" : remote.getQuotaLabel());
        return vo;
    }

    private ApplicationException unsupported(String action) {
        return new ApplicationException(Result.fail(
                ResultCode.ERROR_SERVICES,
                "会员业务请走 economy 服务: " + action
        ));
    }
}
