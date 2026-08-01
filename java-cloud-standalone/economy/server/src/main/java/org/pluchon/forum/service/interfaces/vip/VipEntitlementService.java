package org.pluchon.forum.service.interfaces.vip;

import org.pluchon.forum.entity.db.UserVipSubscription;

import java.util.Date;

/**
 * VIP 权益变更统一入口：订阅、试用、抽奖发放均经此服务，基于行锁计算过期时间。
 */
public interface VipEntitlementService {

    /**
     * 读取用户 VIP 订阅（无行锁，只读路径使用）。
     */
    UserVipSubscription getSubscription(Long userId);

    /**
     * 延长 VIP 天数；tier 为 null 时保持当前有效档或默认 PRO。
     *
     * @return 新的过期时间
     */
    Date extendVipDays(Long userId, Byte tier, int days);

    /**
     * 订阅扣款后升级/续费 VIP（内部会先锁订阅行）。
     */
    Date subscribeTier(Long userId, Byte tier, int days);
}
