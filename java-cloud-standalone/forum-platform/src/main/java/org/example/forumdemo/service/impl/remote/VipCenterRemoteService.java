package org.example.forumdemo.service.impl.remote;

import org.example.forum.api.economy.VipQuotaHintVO;
import org.example.forum.cloud.feign.VipInternalFeignClient;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.entity.vo.mascot.MascotQuotaHintVO;
import org.example.forumdemo.entity.vo.vip.VipCenterVO;
import org.example.forumdemo.entity.vo.vip.VipQuotaPanelVO;
import org.example.forumdemo.service.interfaces.vip.VipCenterService;
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
