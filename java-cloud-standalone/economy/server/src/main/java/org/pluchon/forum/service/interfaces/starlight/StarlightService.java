package org.pluchon.forum.service.interfaces.starlight;

// 萌星辉钱包统一入口：入账/扣减必须走此服务并落流水
public interface StarlightService {

    int getBalance(Long userId);

    // 发放萌星辉；幂等键已存在则直接返回当前余额
    int credit(Long userId, int amount, byte sourceType, Long relatedId, String idempotencyKey, String remark);

    // 消耗萌星辉；幂等键已存在则直接返回当前余额
    int debit(Long userId, int amount, byte sourceType, Long relatedId, String idempotencyKey, String remark);

    // 按奖品稀有度计算本次应发放星辉
    int amountForPrize(Byte isJackpot, Byte prizeType);
}
