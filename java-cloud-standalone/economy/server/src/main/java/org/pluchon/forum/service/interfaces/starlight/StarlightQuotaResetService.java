package org.pluchon.forum.service.interfaces.starlight;

// 额度重置卡的实际发放。从商城兑换里抽出来，好让背包也能在「使用」时调用同一份逻辑
public interface StarlightQuotaResetService {

    /**
     * 把用户当前配额周期的已用量清零，重置到其自身档位的上限。
     *
     * @return 面向用户的发放说明，写进背包物品的 grantSummary
     */
    String applyQuotaReset(Long userId);
}
