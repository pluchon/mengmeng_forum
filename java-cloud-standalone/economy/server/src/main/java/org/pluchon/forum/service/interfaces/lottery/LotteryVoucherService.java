package org.pluchon.forum.service.interfaces.lottery;

// 抽奖抵扣券钱包：发放 / 扣减
public interface LotteryVoucherService {

    // 发放抵扣券（幂等键去重）
    void credit(Long userId, int amount, Long relatedId, String idempotencyKey, String remark, Byte sourceType);
}
