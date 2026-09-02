package org.pluchon.forum.service.interfaces.ai;

import org.pluchon.forum.service.security.AiUserContext;

import java.math.BigDecimal;

// AI 周期额度预占与结算
public interface AiQuotaService {

    void consumeQwenFlash(AiUserContext user);

    void consumeAdvancedLlm(AiUserContext user);

    /** 预占生图额度。units：普通档 1，进阶档 2。 */
    void consumeImage(AiUserContext user, int units);

    void releaseQwenFlash(AiUserContext user);

    void releaseAdvancedLlm(AiUserContext user);

    void releaseImage(AiUserContext user, int units);

    void settleUsage(AiUserContext user, boolean qwenReserved, BigDecimal qwenCost,
                     int wanImageCount);

    // 封面「推荐配图要点」：仅审计计数，不计入文本写作配额
    void recordCoverHint(AiUserContext user);
}
