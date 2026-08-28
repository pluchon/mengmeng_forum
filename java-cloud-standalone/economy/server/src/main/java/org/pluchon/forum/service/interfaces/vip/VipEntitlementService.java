package org.pluchon.forum.service.interfaces.vip;

import org.pluchon.forum.entity.db.UserVipSubscription;

import java.util.Date;

// VIP 权益变更统一入口：订阅、试用、抽奖发放均经此服务，基于行锁计算过期时间
public interface VipEntitlementService {

    // 读取用户 VIP 订阅 无行锁，只读路径使用
    UserVipSubscription getSubscription(Long userId);

    UserVipSubscription ensureCurrentBaseQuotaPeriod(Long userId);

    Date extendVipHours(Long userId, Byte tier, int hours);

}
