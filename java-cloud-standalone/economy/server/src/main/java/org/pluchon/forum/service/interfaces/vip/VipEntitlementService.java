package org.pluchon.forum.service.interfaces.vip;

import org.pluchon.forum.entity.db.UserVipSubscription;
import org.pluchon.forum.entity.enums.VipOrderKind;

import java.util.Date;

// VIP 权益变更统一入口：订阅、试用、抽奖发放均经此服务，基于行锁计算过期时间
public interface VipEntitlementService {

    // 读取用户 VIP 订阅 无行锁，只读路径使用
    UserVipSubscription getSubscription(Long userId);

    UserVipSubscription ensureCurrentBaseQuotaPeriod(Long userId);

    Date extendVipHours(Long userId, Byte tier, int hours);

    // 支付成功后发货：按订单类型推进档位、到期日与配额周期，返回新的权益周期
    VipDeliveryResult deliverPaidOrder(Long userId, Byte tier, VipOrderKind kind, Date expectedExpireAt);

    // 发货结果：会员到期时间 + 本次计入订单的权益周期
    class VipDeliveryResult {
        public Date vipExpireAt;
        public Date periodStart;
        public Date periodEnd;
    }
}
