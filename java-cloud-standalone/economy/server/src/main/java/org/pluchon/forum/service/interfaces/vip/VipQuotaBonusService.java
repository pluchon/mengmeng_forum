package org.pluchon.forum.service.interfaces.vip;

import org.pluchon.forum.entity.db.VipQuotaBonusGrant;

import java.util.List;
import java.math.BigDecimal;
import org.pluchon.forum.api.economy.VipBonusReservationVO;
import org.pluchon.forum.api.economy.VipBonusSettlementVO;

// 会员奖励礼包统一发放与查询入口
public interface VipQuotaBonusService {

    VipQuotaBonusGrant grantTrialBonus(Long userId, int proDays, String sourceType, String idempotencyKey);

    List<VipQuotaBonusGrant> listActiveGrants(Long userId);

    VipBonusReservationVO reserve(Long userId, String resourceType, BigDecimal amount);

    VipBonusSettlementVO settle(Long userId, String reservationToken, BigDecimal actualAmount);

    void release(Long userId, String reservationToken);
}
