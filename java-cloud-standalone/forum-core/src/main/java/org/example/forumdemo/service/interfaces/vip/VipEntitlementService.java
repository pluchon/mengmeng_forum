package org.example.forumdemo.service.interfaces.vip;

import org.example.forumdemo.entity.db.User;

import java.util.Date;

/**
 * VIP 权益变更统一入口：订阅、试用、抽奖发放均经此服务，基于行锁计算过期时间。
 */
public interface VipEntitlementService {

    /**
     * 在已 FOR UPDATE 锁定的用户行上延长 VIP。tier 为 null 时保持当前有效档或默认 PRO。
     *
     * @return 新的过期时间
     */
    Date extendVipDays(User lockedUser, Byte tier, int days);

    /**
     * 订阅扣款后升级/续费 VIP（内部会先锁用户行）。
     */
    Date subscribeTier(Long userId, Byte tier, int days);
}
