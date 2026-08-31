package org.pluchon.forum.service.interfaces.bag;

import org.pluchon.forum.entity.vo.bag.BagItemVO;
import org.pluchon.forum.entity.vo.common.PageResult;

public interface UserBagService {

    /**
     * 往背包塞一件。幂等键重复时静默返回，供抽奖/兑换在同一事务里调用。
     *
     * @param pendingDelivery true 表示实物类，落库即为「待发放」，不提供使用入口
     */
    void grant(Long userId, String source, Long sourceRefId, String itemName,
               String rewardType, int rewardValue, Byte vipTier,
               String idempotencyKey, boolean pendingDelivery);

    PageResult<BagItemVO> list(Long userId, Integer useStatus, Integer pageNum, Integer pageSize);

    /** 使用一件，把奖励真正发放到对应钱包 */
    BagItemVO use(Long userId, Long bagItemId);

    int countUnused(Long userId);
}
